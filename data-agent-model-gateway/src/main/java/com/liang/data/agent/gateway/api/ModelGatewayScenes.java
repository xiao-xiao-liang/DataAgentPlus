package com.liang.data.agent.gateway.api;

/**
 * 阶段 2 模型网关场景编码常量，不含动态路由策略。
 */
public final class ModelGatewayScenes {

    public static final String LEGACY_SYSTEM_USER = "legacy.system_user";
    public static final String LEGACY_SYSTEM_ONLY = "legacy.system_only";
    public static final String LEGACY_USER_ONLY = "legacy.user_only";
    public static final String INTENT_RECOGNITION = "workflow.intent_recognition";
    public static final String EVIDENCE_RECALL = "workflow.evidence_recall";
    public static final String QUERY_ENHANCE = "workflow.query_enhance";
    public static final String FEASIBILITY_ASSESSMENT = "workflow.feasibility_assessment";
    public static final String PLANNER = "workflow.planner";
    public static final String SQL_GENERATION = "workflow.sql_generation";
    public static final String SQL_REPAIR = "workflow.sql_repair";
    public static final String SCHEMA_MIX_SELECT = "workflow.schema_mix_select";
    public static final String SEMANTIC_CONSISTENCY = "workflow.semantic_consistency";
    public static final String DATA_VIEW_ANALYZE = "workflow.data_view_analyze";
    public static final String PYTHON_GENERATION = "workflow.python_generation";
    public static final String PYTHON_ANALYZE = "workflow.python_analyze";
    public static final String REPORT_GENERATION = "workflow.report_generation";
    public static final String CLARIFICATION_NORMALIZE = "workflow.clarification_normalize";
    public static final String JSON_REPAIR = "workflow.json_repair";
    public static final String HUMAN_FEEDBACK_INTENT = "workflow.human_feedback_intent";
    public static final String SESSION_TITLE = "service.session_title";
    public static final String KNOWLEDGE_CHUNK_NAME = "service.knowledge_chunk_name";
    public static final String AI_SIMULATED_EXECUTION = "ai_core.ai_simulated_execution";

    private ModelGatewayScenes() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }
}
