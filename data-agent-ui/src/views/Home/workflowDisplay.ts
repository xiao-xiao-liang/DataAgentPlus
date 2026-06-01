import { parseStreamingPlan } from './streamingPlan';

export type CodeLanguage = 'sql' | 'python' | 'json';

export type CodeToken = {
  text: string;
  type: 'plain' | 'keyword' | 'string' | 'number' | 'comment' | 'operator';
};

export type WorkflowDisplayBlock = {
  type: string;
  content: string;
};

export type ExecutionPlanStepView = {
  step: number;
  tool: string;
  instruction: string;
};

export type ExecutionPlanView = {
  thoughtProcess: string;
  steps: ExecutionPlanStepView[];
};

const fencePattern = /^```([a-zA-Z0-9_-]*)?[^\n]*\n([\s\S]*?)\n?```\s*$/;

export const normalizeCodeForDisplay = (code: string, language?: CodeLanguage) => {
  const normalized = code.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  const trimmed = normalized.trim();
  const match = trimmed.match(fencePattern);

  if (!match) {
    return normalized.replace(/^\n+/, '').replace(/\n+$/, '');
  }

  const fenceLanguage = match[1]?.toLowerCase();
  if (fenceLanguage && language && fenceLanguage !== language) {
    return normalized.replace(/^\n+/, '').replace(/\n+$/, '');
  }

  return match[2].replace(/^\n+/, '').replace(/\n+$/, '');
};

const pythonKeywords = new Set([
  'and', 'as', 'assert', 'break', 'class', 'continue', 'def', 'del', 'elif',
  'else', 'except', 'False', 'finally', 'for', 'from', 'global', 'if', 'import',
  'in', 'is', 'lambda', 'None', 'nonlocal', 'not', 'or', 'pass', 'raise',
  'return', 'True', 'try', 'while', 'with', 'yield',
]);

const sqlKeywords = new Set([
  'select', 'from', 'where', 'join', 'left', 'right', 'inner', 'outer', 'on',
  'group', 'by', 'order', 'having', 'limit', 'offset', 'as', 'and', 'or',
  'case', 'when', 'then', 'else', 'end', 'distinct', 'count', 'sum', 'avg',
  'min', 'max', 'desc', 'asc', 'union', 'all', 'insert', 'update', 'delete',
]);

const splitByPattern = (line: string, pattern: RegExp, type: CodeToken['type']): CodeToken[] => {
  const tokens: CodeToken[] = [];
  let cursor = 0;
  let match: RegExpExecArray | null;

  while ((match = pattern.exec(line)) !== null) {
    if (match.index > cursor) {
      tokens.push({ text: line.slice(cursor, match.index), type: 'plain' });
    }
    tokens.push({ text: match[0], type });
    cursor = match.index + match[0].length;
  }

  if (cursor < line.length) {
    tokens.push({ text: line.slice(cursor), type: 'plain' });
  }

  return tokens.length > 0 ? tokens : [{ text: line, type: 'plain' }];
};

const tokenizePlainSegment = (segment: string, language: CodeLanguage): CodeToken[] => {
  const keywords = language === 'python' ? pythonKeywords : language === 'sql' ? sqlKeywords : new Set<string>();
  const tokens: CodeToken[] = [];
  const pattern = /([A-Za-z_][A-Za-z0-9_]*|\d+(?:\.\d+)?|[=+\-*/<>!]+|[()[\]{}.,:;])/g;
  let cursor = 0;
  let match: RegExpExecArray | null;

  while ((match = pattern.exec(segment)) !== null) {
    if (match.index > cursor) {
      tokens.push({ text: segment.slice(cursor, match.index), type: 'plain' });
    }

    const value = match[0];
    const lower = value.toLowerCase();
    if (keywords.has(value) || keywords.has(lower)) {
      tokens.push({ text: value, type: 'keyword' });
    } else if (/^\d/.test(value)) {
      tokens.push({ text: value, type: 'number' });
    } else if (/^[=+\-*/<>!]+$/.test(value)) {
      tokens.push({ text: value, type: 'operator' });
    } else {
      tokens.push({ text: value, type: 'plain' });
    }
    cursor = match.index + value.length;
  }

  if (cursor < segment.length) {
    tokens.push({ text: segment.slice(cursor), type: 'plain' });
  }

  return tokens;
};

const tokenizeLine = (line: string, language: CodeLanguage): CodeToken[] => {
  const commentIndex = language === 'python' ? line.indexOf('#') : language === 'sql' ? line.indexOf('--') : -1;
  const source = commentIndex >= 0 ? line.slice(0, commentIndex) : line;
  const comment = commentIndex >= 0 ? line.slice(commentIndex) : '';
  const stringParts = splitByPattern(source, /(["'`])(?:\\.|(?!\1).)*\1/g, 'string');
  const tokens: CodeToken[] = stringParts.flatMap((token) => (
    token.type === 'plain' ? tokenizePlainSegment(token.text, language) : [token]
  ));

  if (comment) {
    tokens.push({ text: comment, type: 'comment' });
  }

  return tokens.length > 0 ? tokens : [{ text: line, type: 'plain' }];
};

export const tokenizeCodeForDisplay = (code: string, language: CodeLanguage): CodeToken[][] => {
  const normalized = normalizeCodeForDisplay(code, language);
  return normalized.split('\n').map((line) => tokenizeLine(line, language));
};

const parseJsonSafely = (value: string) => {
  try {
    return JSON.parse(value.replace('$$$json', '').trim());
  } catch {
    return null;
  }
};

const findJsonObjectCandidates = (value: string) => {
  const candidates: string[] = [];
  let start = -1;
  let depth = 0;
  let inString = false;
  let escaped = false;

  for (let i = 0; i < value.length; i += 1) {
    const char = value[i];

    if (escaped) {
      escaped = false;
      continue;
    }

    if (char === '\\' && inString) {
      escaped = true;
      continue;
    }

    if (char === '"') {
      inString = !inString;
      continue;
    }

    if (inString) continue;

    if (char === '{') {
      if (depth === 0) {
        start = i;
      }
      depth += 1;
      continue;
    }

    if (char === '}' && depth > 0) {
      depth -= 1;
      if (depth === 0 && start >= 0) {
        candidates.push(value.slice(start, i + 1));
        start = -1;
      }
    }
  }

  return candidates;
};

export const isStructuredAnalysisOutput = (value: string) => {
  const parsed = parseJsonSafely(value);
  if (!parsed || typeof parsed !== 'object') return false;

  const raw = value.trim();
  const workflowJson =
    raw.includes('"execution_plan"') ||
    raw.includes('"thought_process"') ||
    raw.includes('"classification"') ||
    raw.includes('"reply"') ||
    raw.includes('"standalone_query"') ||
    raw.includes('"canonical_query"') ||
    raw.includes('"expanded_queries"') ||
    raw.includes('"resultSet"') ||
    raw.includes('"displayStyle"');

  return !workflowJson;
};

const extractStructuredAnalysisJson = (value: string) => {
  if (isStructuredAnalysisOutput(value)) {
    return value.replace('$$$json', '').trim();
  }

  return findJsonObjectCandidates(value).find(isStructuredAnalysisOutput);
};

export const extractExecutionPlanView = (value?: string): ExecutionPlanView | null => {
  const plan = parseStreamingPlan(value);
  if (!plan) return null;

  const steps = Array.isArray(plan.execution_plan)
    ? plan.execution_plan
      .map((step, index) => ({
        step: typeof step.step === 'number' ? step.step : index + 1,
        tool: typeof step.tool_to_use === 'string' ? step.tool_to_use : 'workflow_step',
        instruction: String(
          step.tool_parameters?.instruction ||
          step.tool_parameters?.summary_and_recommendations ||
          ''
        ).trim(),
      }))
      .filter((step) => step.instruction)
    : [];

  if (!plan.thought_process && steps.length === 0) return null;

  return {
    thoughtProcess: typeof plan.thought_process === 'string' ? plan.thought_process.trim() : '',
    steps,
  };
};

export const getPythonExecutionOutputBlock = (blocks: WorkflowDisplayBlock[]) => {
  const pythonIndex = blocks.findIndex((block) => block.type === 'python');
  const searchStart = pythonIndex >= 0 ? pythonIndex + 1 : 0;
  const candidates = blocks.slice(searchStart).filter((block) => (
    block.type === 'json' && isStructuredAnalysisOutput(block.content)
  ));

  if (candidates[0]) {
    return candidates[0];
  }

  const textOutput = blocks.slice(searchStart)
    .filter((block) => block.type === 'text' && isPythonExecutionResidue(block.content))
    .map((block) => extractStructuredAnalysisJson(block.content))
    .find((content): content is string => Boolean(content));

  return textOutput ? { type: 'json', content: textOutput } : undefined;
};

export const isPythonExecutionResidue = (value: string) => {
  const trimmed = value.trim();
  if (!trimmed) return false;

  const hasPythonLifecycleText =
    trimmed.includes('开始执行 Python 数据分析代码') ||
    trimmed.includes('Python 代码数据处理执行成功') ||
    trimmed.includes('分析引擎标准输出') ||
    trimmed.includes('Python 运行结果');

  const hasStructuredOutput =
    trimmed.includes('"most_time_consuming_node"') ||
    trimmed.includes('"details"') ||
    trimmed.includes('"node_group"') ||
    trimmed.includes('"avg_duration_ms"') ||
    Boolean(extractStructuredAnalysisJson(trimmed));

  return hasPythonLifecycleText && hasStructuredOutput;
};

export const formatStructuredAnalysisOutput = (value: string) => {
  const parsed = parseJsonSafely(value);
  return parsed ? JSON.stringify(parsed, null, 2) : value.trim();
};
