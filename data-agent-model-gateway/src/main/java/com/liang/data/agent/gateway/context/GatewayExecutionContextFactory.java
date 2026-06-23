package com.liang.data.agent.gateway.context;

import java.util.Objects;
import java.util.UUID;

/**
 * 模型网关执行上下文工厂，负责在工作流入口创建运行上下文。
 */
public class GatewayExecutionContextFactory {

    private final TraceIdProvider traceIdProvider;

    /**
     * 创建模型网关执行上下文工厂。
     *
     * @param traceIdProvider 链路追踪编号提供器
     * @throws NullPointerException traceIdProvider 为空时抛出
     */
    public GatewayExecutionContextFactory(TraceIdProvider traceIdProvider) {
        this.traceIdProvider = Objects.requireNonNull(traceIdProvider, "链路追踪编号提供器不能为空");
    }

    /**
     * 创建一次工作流运行对应的模型网关执行上下文。
     *
     * @param sessionId 会话编号
     * @param userId 用户编号，允许为 null
     * @param agentId 智能体编号，允许为 null
     * @param tenantId 租户编号，允许为 null
     * @return 模型网关执行上下文
     * @throws IllegalArgumentException 上下文字段不合法时抛出
     */
    public GatewayExecutionContext create(String sessionId, Long userId, Integer agentId, String tenantId) {
        // 1. 生成本次工作流运行编号。
        String runId = UUID.randomUUID().toString();
        // 2. 安全读取并规范化链路追踪编号，读取失败或空白值统一表示为当前无可用 traceId。
        String traceId = currentTraceIdSafely();
        // 3. 组装不可变执行上下文，并复用上下文本身的字段校验规则。
        return new GatewayExecutionContext(runId, traceId, sessionId, userId, agentId, tenantId);
    }

    private String currentTraceIdSafely() {
        try {
            // 1. 从外部提供器读取当前链路追踪编号。
            return normalizeTraceId(traceIdProvider.currentTraceId());
        } catch (RuntimeException exception) {
            // 2. traceId 仅作为可观测字段，读取失败时降级为空，不阻断上下文创建。
            return null;
        }
    }

    private static String normalizeTraceId(String traceId) {
        // 1. 将 null 或空白链路追踪编号归一化为 null。
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        // 2. 保留调用方提供的有效链路追踪编号。
        return traceId;
    }
}
