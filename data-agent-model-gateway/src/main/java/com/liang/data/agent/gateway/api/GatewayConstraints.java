package com.liang.data.agent.gateway.api;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * 单次模型网关调用约束，统一描述超时、输出、预算与降级策略。
 *
 * @param timeout 调用超时时间
 * @param maxOutputTokens 最大输出 Token 数
 * @param budgetLimit 预算上限
 * @param allowFallback 是否允许降级
 */
public record GatewayConstraints(Duration timeout, Integer maxOutputTokens,
                                 BigDecimal budgetLimit, boolean allowFallback) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    public GatewayConstraints {
        // 1. 补充默认超时，并确保超时时间为正数。
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("调用超时必须大于零");
        }
        // 2. 校验可选输出上限，提供时必须为正数。
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("最大输出Token必须大于零");
        }
        // 3. 校验可选预算上限，允许零预算但不允许负数。
        if (budgetLimit != null && budgetLimit.signum() < 0) {
            throw new IllegalArgumentException("预算上限不能小于零");
        }
    }

    /**
     * 创建默认调用约束。
     *
     * @return 默认调用约束
     */
    public static GatewayConstraints defaults() {
        return new GatewayConstraints(DEFAULT_TIMEOUT, null, null, true);
    }
}
