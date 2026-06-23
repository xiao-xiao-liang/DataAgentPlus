package com.liang.data.agent.gateway.context;

/**
 * 模型网关执行上下文，保存一次工作流运行在模型调用链路中需要传播的身份与追踪信息。
 *
 * @param runId 本次工作流运行编号
 * @param traceId 当前链路追踪编号，允许为 null
 * @param sessionId 会话编号
 * @param userId 用户编号，允许为 null
 * @param agentId 智能体编号，允许为 null
 * @param tenantId 租户编号，允许为 null
 * @throws IllegalArgumentException runId、sessionId 为空白，或可选字段存在但不符合取值规则时抛出
 */
public record GatewayExecutionContext(String runId, String traceId, String sessionId,
                                      Long userId, Integer agentId, String tenantId) {

    public GatewayExecutionContext {
        // 1. 校验运行编号，确保每次工作流执行都具备可定位的唯一标识。
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("运行编号不能为空");
        }
        // 2. 校验会话编号，确保上下文能够关联入口会话。
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("会话编号不能为空");
        }
        // 3. 校验可选用户编号，存在时必须为正数。
        if (userId != null && userId <= 0) {
            throw new IllegalArgumentException("用户编号必须大于0");
        }
        // 4. 校验可选智能体编号，存在时必须为正数。
        if (agentId != null && agentId <= 0) {
            throw new IllegalArgumentException("智能体编号必须大于0");
        }
        // 5. 校验可选链路追踪编号，存在时不能是空白字符串。
        if (traceId != null && traceId.isBlank()) {
            throw new IllegalArgumentException("链路追踪编号不能为空白");
        }
        // 6. 校验可选租户编号，存在时不能是空白字符串。
        if (tenantId != null && tenantId.isBlank()) {
            throw new IllegalArgumentException("租户编号不能为空白");
        }
    }
}
