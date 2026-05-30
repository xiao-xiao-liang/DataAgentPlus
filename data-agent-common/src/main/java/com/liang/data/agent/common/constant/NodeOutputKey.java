package com.liang.data.agent.common.constant;

import lombok.NoArgsConstructor;

/**
 * 节点输出 Key 常量
 */
@NoArgsConstructor
public final class NodeOutputKey {

    /**
     * 意图识别节点输出 (IntentRecognitionOutputDTO)
     */
    public static final String INTENT_RECOGNITION_NODE_OUTPUT = "INTENT_RECOGNITION_NODE_OUTPUT";
    
    /**
     * 查询增强节点输出 (QueryEnhanceOutputDTO)
     */
    public static final String QUERY_ENHANCE_NODE_OUTPUT = "QUERY_ENHANCE_NODE_OUTPUT";
    
    /**
     * 可行性评估节点输出
     */
    public static final String FEASIBILITY_ASSESSMENT_NODE_OUTPUT = "FEASIBILITY_ASSESSMENT_NODE_OUTPUT";
    
    /**
     * 证据召回结果
     */
    public static final String EVIDENCE_OUTPUT = "EVIDENCE_OUTPUT";
    
    /**
     * 表文档 (用于 Schema 召回)
     */
    public static final String TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT = "TABLE_DOCUMENTS_FOR_SCHEMA";
    
    /**
     * 列文档 (用于 Schema 召回)
     */
    public static final String COLUMN_DOCUMENTS_FOR_SCHEMA_OUTPUT = "COLUMN_DOCUMENTS_FOR_SCHEMA_OUTPUT";
    
    /**
     * Schema 召回节点输出
     */
    public static final String SCHEMA_RECALL_NODE_OUTPUT = "SCHEMA_RECALL_NODE_OUTPUT";
    
    /**
     * 表关系解析输出 (SchemaDTO)
     */
    public static final String TABLE_RELATION_OUTPUT = "TABLE_RELATION_OUTPUT";
    
    /**
     * 表关系解析异常输出
     */
    public static final String TABLE_RELATION_EXCEPTION_OUTPUT = "TABLE_RELATION_EXCEPTION_OUTPUT";
    
    /**
     * 语义模型 Prompt
     */
    public static final String GENERATED_SEMANTIC_MODEL_PROMPT = "GENERATED_SEMANTIC_MODEL_PROMPT";
    
    /**
     * SQL 生成输出
     */
    public static final String SQL_GENERATE_OUTPUT = "SQL_GENERATE_OUTPUT";
    
    /**
     * SQL 生成时 Schema 缺失建议
     */
    public static final String SQL_GENERATE_SCHEMA_MISSING_ADVICE = "SQL_GENERATE_SCHEMA_MISSING_ADVICE";
    
    /**
     * 语义一致性检查输出
     */
    public static final String SEMANTIC_CONSISTENCY_NODE_OUTPUT = "SEMANTIC_CONSISTENCY_NODE_OUTPUT";
    
    /**
     * Planner 节点输出 (Plan JSON)
     */
    public static final String PLANNER_NODE_OUTPUT = "PLANNER_NODE_OUTPUT";
    
    /**
     * SQL 执行节点输出
     */
    public static final String SQL_EXECUTE_NODE_OUTPUT = "SQL_EXECUTE_NODE_OUTPUT";
    
    /**
     * SQL 结果列表 (跨步骤累积)
     */
    public static final String SQL_RESULT_LIST_MEMORY = "SQL_RESULT_LIST_MEMORY";

    /**
     * Python 生成节点输出
     */
    public static final String PYTHON_GENERATE_NODE_OUTPUT = "PYTHON_GENERATE_NODE_OUTPUT";

    /**
     * Python 执行节点输出
     */
    public static final String PYTHON_EXECUTE_NODE_OUTPUT = "PYTHON_EXECUTE_NODE_OUTPUT";

    /**
     * Python 分析节点输出
     */
    public static final String PYTHON_ANALYSIS_NODE_OUTPUT = "PYTHON_ANALYSIS_NODE_OUTPUT";

    public static final String CLARIFICATION_REQUEST = "clarification_request";

    public static final String CLARIFICATION_FEEDBACK_DATA = "clarification_feedback_data";

    public static final String CLARIFICATION_NORMALIZED_OUTPUT = "clarification_normalized_output";

    public static final String CLARIFICATION_CONFIRM_DATA = "clarification_confirm_data";

    public static final String CLARIFICATION_EVIDENCE = "clarification_evidence";

    public static final String CLARIFICATION_CONFIRMED = "clarification_confirmed";

    public static final String MEMORY_CANDIDATE_OUTPUT = "memory_candidate_output";

    public static final String MEMORY_CANDIDATE_ID = "memory_candidate_id";

    public static final String MEMORY_SAVE_REQUIRED = "memory_save_required";
}
