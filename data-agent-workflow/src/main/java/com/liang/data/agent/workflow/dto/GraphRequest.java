package com.liang.data.agent.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流 SSE 流式请求体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphRequest {

    /**
     * 智能体 ID
     */
    private String agentId;

    /**
     * 用户 ID。未接入登录前由前端传默认值，接入登录后改为认证上下文提供。
     */
    private String userId;

    /**
     * 线程 ID (用于多轮对话和流式上下文关联)
     */
    private String threadId;

    /**
     * 用户自然语言查询
     */
    private String query;

    /**
     * 是否启用人工复核
     */
    private boolean humanFeedback;

    /**
     * 人工反馈内容 (复核时填写)
     */
    private String humanFeedbackContent;

    /**
     * 交互类型：NEW_QUERY / CLARIFICATION_ANSWER / CLARIFICATION_CONFIRM / HUMAN_PLAN_FEEDBACK
     */
    private String interactionType;

    /**
     * 澄清回答、确认内容等交互正文。
     */
    private String interactionContent;

    /**
     * 用户是否拒绝了计划
     */
    private boolean rejectedPlan;

    /**
     * 是否仅 NL2SQL 模式 (跳过 Python 分析和报告生成)
     */
    private boolean nl2sqlOnly;
}
