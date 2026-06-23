package com.liang.data.agent.gateway.error;

import com.liang.data.agent.common.errorcode.IErrorCode;

/**
 * 模型网关错误码。
 *
 * <p>定义模型调用链路中的结构化错误语义，用于统一判断错误来源、重试能力与降级能力。</p>
 */
public enum ModelGatewayErrorCode implements IErrorCode {

    INVALID_REQUEST("A020001", "模型调用参数错误", false, false),
    CONTEXT_TOO_LONG("A020002", "模型上下文超出限制", false, false),
    BUDGET_EXCEEDED("A020003", "模型调用预算不足", false, false),
    RATE_LIMITED("C020001", "模型调用被限流", true, true),
    PROVIDER_TIMEOUT("C020002", "模型供应商调用超时", true, true),
    PROVIDER_UNAVAILABLE("C020003", "模型供应商不可用", true, true),
    AUTHENTICATION_FAILED("C020004", "模型供应商认证失败", false, false),
    RESPONSE_INVALID("C020005", "模型响应格式错误", true, true),
    CALL_CANCELLED("B020001", "模型调用已取消", false, false);

    private final String code;

    private final String message;

    private final boolean retryable;

    private final boolean degradable;

    ModelGatewayErrorCode(String code, String message, boolean retryable, boolean degradable) {
        this.code = code;
        this.message = message;
        this.retryable = retryable;
        this.degradable = degradable;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    public boolean retryable() {
        return retryable;
    }

    public boolean degradable() {
        return degradable;
    }
}
