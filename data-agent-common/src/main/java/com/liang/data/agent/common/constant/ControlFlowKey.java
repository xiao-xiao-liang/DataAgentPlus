package com.liang.data.agent.common.constant;

import lombok.NoArgsConstructor;

/**
 * 控制流 Key
 */
@NoArgsConstructor
public final class ControlFlowKey {

    /**
     * 数据库方言类型
     */
    public static final String DB_DIALECT_TYPE = "DB_DIALECT_TYPE";

    /**
     * 表关系解析重试次数
     */
    public static final String TABLE_RELATION_RETRY_COUNT = "TABLE_RELATION_RETRY_COUNT";

    /**
     * SQL 生成重试计数
     */
    public static final String SQL_GENERATE_COUNT = "SQL_GENERATE_COUNT";

    /**
     * SQL 重新生成原因 (SqlRetryDto)
     */
    public static final String SQL_REGENERATE_REASON = "SQL_REGENERATE_REASON";

    /**
     * Plan 当前执行步骤编号
     */
    public static final String PLAN_CURRENT_STEP = "PLAN_CURRENT_STEP";

    /**
     * Plan 下一个执行节点
     */
    public static final String PLAN_NEXT_NODE = "PLAN_NEXT_NODE";

    /**
     * Plan 验证状态 (boolean)
     */
    public static final String PLAN_VALIDATION_STATUS = "PLAN_VALIDATION_STATUS";

    /**
     * Plan 验证错误信息
     */
    public static final String PLAN_VALIDATION_ERROR = "PLAN_VALIDATION_ERROR";

    /**
     * Plan 修复重试次数
     */
    public static final String PLAN_REPAIR_COUNT = "PLAN_REPAIR_COUNT";

    /**
     * Python 执行是否成功
     */
    public static final String PYTHON_IS_SUCCESS = "PYTHON_IS_SUCCESS";

    /**
     * Python 重试次数
     */
    public static final String PYTHON_TRIES_COUNT = "PYTHON_TRIES_COUNT";

    /**
     * Python 降级模式标记 (超过最大重试后触发)
     */
    public static final String PYTHON_FALLBACK_MODE = "PYTHON_FALLBACK_MODE";

    /**
     * 人工反馈后下一个执行节点
     */
    public static final String HUMAN_NEXT_NODE = "human_next_node";

    /**
     * 等待人工反馈状态标识
     */
    public static final String WAIT_FOR_FEEDBACK = "WAIT_FOR_FEEDBACK";

    public static final String CLARIFICATION_NEXT_NODE = "clarification_next_node";

    public static final String MEMORY_CANDIDATE_NEXT_NODE = "memory_candidate_next_node";

    public static final String WAIT_FOR_CLARIFICATION = "WAIT_FOR_CLARIFICATION";

    public static final String WAIT_FOR_CLARIFICATION_CONFIRM = "WAIT_FOR_CLARIFICATION_CONFIRM";
}
