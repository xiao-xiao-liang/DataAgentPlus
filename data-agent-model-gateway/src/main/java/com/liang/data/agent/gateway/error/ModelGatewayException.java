package com.liang.data.agent.gateway.error;

import com.liang.data.agent.common.exception.ServiceException;

import java.util.Objects;

/**
 * 模型网关异常。
 *
 * <p>封装模型网关结构化错误码，并复用平台服务异常体系向上游传递错误信息。</p>
 */
public class ModelGatewayException extends ServiceException {

    private static final long serialVersionUID = 1L;

    private final ModelGatewayErrorCode gatewayErrorCode;

    public ModelGatewayException(ModelGatewayErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public ModelGatewayException(ModelGatewayErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ModelGatewayException(ModelGatewayErrorCode errorCode, String message, Throwable cause) {
        super(normalizeMessage(errorCode, message), cause, requireErrorCode(errorCode));
        this.gatewayErrorCode = requireErrorCode(errorCode);
    }

    public ModelGatewayErrorCode getGatewayErrorCode() {
        return gatewayErrorCode;
    }

    private static ModelGatewayErrorCode requireErrorCode(ModelGatewayErrorCode errorCode) {
        // 1. 提前校验错误码，避免父类读取错误码信息时出现空指针。
        return Objects.requireNonNull(errorCode, "模型网关错误码不能为空");
    }

    private static String normalizeMessage(ModelGatewayErrorCode errorCode, String message) {
        // 1. 先校验错误码，确保后续可以安全读取默认错误信息。
        ModelGatewayErrorCode checkedErrorCode = requireErrorCode(errorCode);
        // 2. 将空消息统一归一化为错误码默认信息，避免向上游返回空白错误。
        if (message == null || message.isBlank()) {
            return checkedErrorCode.message();
        }
        return message;
    }
}
