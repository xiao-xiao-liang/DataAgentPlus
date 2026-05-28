/**
 * 消息 Block 数据结构
 */
export interface MessageBlock {
  type: 'text' | 'json' | 'python' | 'sql' | 'markdown-report' | 'result_set';
  content: string;
}

/**
 * 完整消息格式
 */
export interface Message {
  role: 'user' | 'assistant';
  content?: string;
  type?: 'text' | 'data';
  data?: any;
  blocks?: MessageBlock[];
  isComplete?: boolean;
  createTime?: string;
}

/**
 * 解析后的流式内容块
 */
export interface ContentBlock {
  type: 'text' | 'python' | 'sql' | 'markdown-report' | 'result_set' | 'json';
  content: string;
}

/**
 * 单个工作流计划步骤定义
 */
export interface ExecutionStep {
  step: number;
  tool_to_use: string;
  tool_parameters?: {
    instruction?: string;
    summary_and_recommendations?: string;
    sql_query?: string;
  };
}

/**
 * 编排执行计划
 */
export interface Plan {
  thought_process: string;
  execution_plan: ExecutionStep[];
}
