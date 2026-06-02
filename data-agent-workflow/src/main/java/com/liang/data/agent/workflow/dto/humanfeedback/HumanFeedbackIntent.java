package com.liang.data.agent.workflow.dto.humanfeedback;

/**
 * 人工反馈意图枚举。
 *
 * <p>用于区分用户在人工审核节点输入的内容是确认计划、修改计划，还是语义不明确。</p>
 */
public enum HumanFeedbackIntent {

    /**
     * 确认当前执行计划。
     */
    APPROVE,

    /**
     * 驳回或要求修改当前执行计划。
     */
    REVISE,

    /**
     * 无法稳定判断用户意图。
     */
    UNCERTAIN
}
