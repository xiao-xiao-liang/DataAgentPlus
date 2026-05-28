import type { ExecutionStep, Plan } from './types';
import { parseStreamingPlan } from './streamingPlan';

export interface WorkflowDisplayStep {
  key: string;
  title: string;
  description: string;
  source?: string;
}

export interface WorkflowPresentation {
  thought: string;
  steps: WorkflowDisplayStep[];
  estimatedMinutes: number;
  approvalHint: string;
}

const DISCOVERY_STEPS: WorkflowDisplayStep[] = [
  {
    key: 'intent',
    title: '理解用户需求',
    description: '识别用户意图，判断当前问题是否属于数据分析任务，并明确需要回答的核心指标。',
    source: 'intent_recognition',
  },
  {
    key: 'evidence-query',
    title: '整理证据与分析口径',
    description: '查询知识库和历史上下文，改写问题以提升 Schema 召回与后续分析的准确性。',
    source: 'evidence_recall / query_enhance',
  },
  {
    key: 'schema',
    title: '定位可用数据结构',
    description: '召回候选 Schema，构建表关系，并筛选本次分析真正需要的数据表和字段。',
    source: 'schema_recall / table_relation',
  },
  {
    key: 'feasibility',
    title: '评估任务可执行性',
    description: '结合用户问题、证据和 Schema 判断当前任务是否能由工作流继续执行。',
    source: 'feasibility_assessment',
  },
  {
    key: 'planner',
    title: '生成并校验执行计划',
    description: '规划后续工具步骤，校验计划完整性，并在需要时进入人工复核。',
    source: 'planner / plan_executor / human_feedback',
  },
];

const getStepInstruction = (step: ExecutionStep) => {
  return step.tool_parameters?.instruction || step.tool_parameters?.summary_and_recommendations || '';
};

const normalizeToolName = (tool: string) => tool.trim().toLowerCase();

const buildToolStep = (step: ExecutionStep): WorkflowDisplayStep => {
  const tool = normalizeToolName(step.tool_to_use || '');
  const instruction = getStepInstruction(step);

  if (tool === 'sql_generate') {
    return {
      key: `plan-sql-${step.step}`,
      title: '执行数据查询',
      description: instruction || '根据选定的数据表生成 SQL，完成语义一致性检查后执行查询并返回结果集。',
      source: 'sql_generate / semantic_consistency / sql_execute',
    };
  }

  if (tool === 'python_generate') {
    return {
      key: `plan-python-${step.step}`,
      title: '运行分析建模',
      description: instruction || '生成 Python 分析代码，执行后对输出结果进行解释和沉淀。',
      source: 'python_generate / python_execute / python_analyze',
    };
  }

  if (tool === 'report_generator') {
    return {
      key: `plan-report-${step.step}`,
      title: '生成最终报告',
      description: instruction || '根据前序查询和分析结果，汇总生成最终中文分析报告。',
      source: 'report_generator',
    };
  }

  return {
    key: `plan-${tool || 'unknown'}-${step.step}`,
    title: tool ? `${tool.toUpperCase()} 节点执行` : '执行工作流步骤',
    description: instruction || '执行计划中的一个工作流步骤。',
    source: tool || undefined,
  };
};

const dedupeAdjacentToolSteps = (steps: WorkflowDisplayStep[]) => {
  return steps.reduce<WorkflowDisplayStep[]>((acc, step) => {
    const previous = acc[acc.length - 1];
    if (previous && previous.title === step.title && previous.source === step.source) {
      previous.description = `${previous.description}\n${step.description}`;
      return acc;
    }
    acc.push(step);
    return acc;
  }, []);
};

export const buildWorkflowPresentation = (plan: Plan): WorkflowPresentation => {
  const planSteps = Array.isArray(plan.execution_plan)
    ? dedupeAdjacentToolSteps(plan.execution_plan.map(buildToolStep))
    : [];

  const steps = [...DISCOVERY_STEPS, ...planSteps];
  const estimatedMinutes = Math.max(1, Math.ceil((steps.length * 18) / 60));

  return {
    thought: plan.thought_process || '系统已完成需求理解，并生成可执行的数据分析流程。',
    steps,
    estimatedMinutes,
    approvalHint: '请确认本次执行计划、统计口径和数据范围是否符合预期。确认后系统会继续执行；如需调整，请点击修改并写明变更意见。',
  };
};

export const parseWorkflowPlan = (planJson: string): Plan | null => {
  return parseStreamingPlan(planJson);
};
