package com.liang.data.agent.common.constant;

import lombok.NoArgsConstructor;

/**
 * StateGraph 状态 Key 常量
 *
 * <p>定义工作流各节点间传递的状态键名</p>
 */
@NoArgsConstructor
public final class StateKey {

    /**
     * NL2SQL 工作流图名称
     */
    public static final String NL2SQL_GRAPH_NAME = "nl2sql_graph";

    // ==================== 用户输入 ====================

    /**
     * 用户输入
     */
    public static final String INPUT_KEY = "input";
    /**
     * 智能体 ID
     */
    public static final String AGENT_ID = "agent_id";
    /**
     * 数据源 ID
     */
    public static final String DATASOURCE_ID = "datasource_id";
    /**
     * 最终结果
     */
    public static final String RESULT = "result";

    // ==================== 节点名称 ====================
    public static final String INTENT_RECOGNITION_NODE = "intent_recognition";
    public static final String EVIDENCE_RECALL_NODE = "evidence_recall";
    public static final String QUERY_ENHANCE_NODE = "query_enhance";
    public static final String SCHEMA_RECALL_NODE = "schema_recall";
    public static final String TABLE_RELATION_NODE = "table_relation";
    public static final String FEASIBILITY_ASSESSMENT_NODE = "feasibility_assessment";
    public static final String PLANNER_NODE = "planner";
    public static final String PLAN_EXECUTOR_NODE = "plan_executor";
    public static final String SQL_GENERATE_NODE = "sql_generate";
    public static final String SEMANTIC_CONSISTENCY_NODE = "semantic_consistency";
    public static final String SQL_EXECUTE_NODE = "sql_execute";
    public static final String PYTHON_GENERATE_NODE = "python_generate";
    public static final String PYTHON_EXECUTE_NODE = "python_execute";
    public static final String PYTHON_ANALYZE_NODE = "python_analyze";
    public static final String REPORT_GENERATOR_NODE = "report_generator";
    public static final String HUMAN_FEEDBACK_NODE = "human_feedback";

    /**
     * 多轮对话上下文
     */
    public static final String MULTI_TURN_CONTEXT = "MULTI_TURN_CONTEXT";
}
