import type { ExecutionStep, Plan } from './types';

const cleanPlanJson = (planJson: string) => planJson.replace('$$$json', '').trim();

const decodeJsonStringContent = (value: string) => {
  try {
    return JSON.parse(`"${value}"`);
  } catch {
    return value
      .replace(/\\"/g, '"')
      .replace(/\\n/g, '\n')
      .replace(/\\r/g, '\r')
      .replace(/\\t/g, '\t')
      .replace(/\\\\/g, '\\');
  }
};

const extractStringValue = (source: string, key: string) => {
  const startMatch = source.match(new RegExp(`"${key}"\\s*:\\s*"`));
  if (!startMatch || startMatch.index === undefined) return '';

  const valueStart = startMatch.index + startMatch[0].length;
  let escaped = false;
  let cursor = valueStart;

  while (cursor < source.length) {
    const char = source[cursor];
    if (escaped) {
      escaped = false;
    } else if (char === '\\') {
      escaped = true;
    } else if (char === '"') {
      break;
    }
    cursor += 1;
  }

  return decodeJsonStringContent(source.slice(valueStart, cursor));
};

const findExecutionPlanArrayStart = (source: string) => {
  const match = source.match(/"execution_plan"\s*:\s*\[/);
  return match?.index === undefined ? -1 : match.index + match[0].length;
};

const extractStepObjectSnippets = (source: string) => {
  const arrayStart = findExecutionPlanArrayStart(source);
  if (arrayStart < 0) return [];

  const snippets: Array<{ content: string; complete: boolean }> = [];
  let depth = 0;
  let objectStart = -1;
  let inString = false;
  let escaped = false;

  for (let index = arrayStart; index < source.length; index += 1) {
    const char = source[index];

    if (inString) {
      if (escaped) {
        escaped = false;
      } else if (char === '\\') {
        escaped = true;
      } else if (char === '"') {
        inString = false;
      }
      continue;
    }

    if (char === '"') {
      inString = true;
      continue;
    }

    if (char === '[' && depth === 0) {
      continue;
    }

    if (char === ']' && depth === 0) {
      break;
    }

    if (char === '{') {
      if (depth === 0) objectStart = index;
      depth += 1;
      continue;
    }

    if (char === '}') {
      depth -= 1;
      if (depth === 0 && objectStart >= 0) {
        snippets.push({ content: source.slice(objectStart, index + 1), complete: true });
        objectStart = -1;
      }
    }
  }

  if (objectStart >= 0 && depth > 0) {
    snippets.push({ content: source.slice(objectStart), complete: false });
  }

  return snippets;
};

const parseStepSnippet = (snippet: { content: string; complete: boolean }, index: number): ExecutionStep | null => {
  if (snippet.complete) {
    try {
      return JSON.parse(snippet.content) as ExecutionStep;
    } catch {
      return null;
    }
  }

  const stepMatch = snippet.content.match(/"step"\s*:\s*(\d+)/);
  const step = stepMatch ? Number(stepMatch[1]) : index + 1;
  const tool = extractStringValue(snippet.content, 'tool_to_use');
  const instruction = extractStringValue(snippet.content, 'instruction');
  const summary = extractStringValue(snippet.content, 'summary_and_recommendations');
  const sql = extractStringValue(snippet.content, 'sql_query');

  if (!tool && !instruction && !summary && !sql && !stepMatch) return null;

  return {
    step,
    tool_to_use: tool,
    tool_parameters: {
      instruction,
      summary_and_recommendations: summary,
      sql_query: sql,
    },
  };
};

export const parseStreamingPlan = (planJson?: string): Plan | null => {
  if (!planJson) return null;

  const cleanJson = cleanPlanJson(planJson);
  try {
    const parsed = JSON.parse(cleanJson);
    if (Array.isArray(parsed.execution_plan)) return parsed as Plan;
  } catch {
    // Fall through to partial extraction.
  }

  const thought = extractStringValue(cleanJson, 'thought_process');
  const steps = extractStepObjectSnippets(cleanJson)
    .map(parseStepSnippet)
    .filter((step): step is ExecutionStep => Boolean(step));

  if (!thought && steps.length === 0) return null;

  return {
    thought_process: thought,
    execution_plan: steps,
  };
};
