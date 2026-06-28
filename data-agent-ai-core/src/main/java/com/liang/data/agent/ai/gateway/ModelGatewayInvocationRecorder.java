package com.liang.data.agent.ai.gateway;

import com.liang.data.agent.gateway.api.ModelCallMode;
import com.liang.data.agent.gateway.api.ModelRoute;
import com.liang.data.agent.gateway.api.ModelUsage;
import com.liang.data.agent.gateway.context.GatewayExecutionContext;
import com.liang.data.agent.gateway.error.ModelGatewayErrorCode;

/**
 * 模型网关调用明细记录器，用于记录主调用与单次尝试的开始、结束状态。
 */
public interface ModelGatewayInvocationRecorder {

    /**
     * 记录一次模型网关调用开始。
     *
     * @param invocationId 调用标识
     * @param context 执行上下文
     * @param sceneCode 场景编码
     * @param mode 调用模式
     */
    void startInvocation(String invocationId, GatewayExecutionContext context, String sceneCode, ModelCallMode mode);

    /**
     * 记录一次模型网关调用结束。
     *
     * @param invocationId 调用标识
     * @param status 调用状态
     * @param route 最终路由信息
     * @param usage Token 使用量
     * @param errorCode 错误码
     * @param errorMessage 错误摘要
     */
    void finishInvocation(String invocationId, ModelGatewayCallStatus status, ModelRoute route, ModelUsage usage,
                          ModelGatewayErrorCode errorCode, String errorMessage);

    /**
     * 记录一次模型调用尝试开始。
     *
     * @param invocationId 调用标识
     * @param attemptId 尝试标识
     * @param attemptNo 尝试序号
     * @param provider 模型厂商
     * @param model 模型名称
     */
    void startAttempt(String invocationId, String attemptId, int attemptNo, String provider, String model);

    /**
     * 记录一次模型调用尝试结束。
     *
     * @param attemptId 尝试标识
     * @param status 尝试状态
     * @param httpStatus HTTP 状态码
     * @param errorCode 错误码
     * @param errorMessage 错误摘要
     */
    void finishAttempt(String attemptId, ModelGatewayCallStatus status, Integer httpStatus,
                       ModelGatewayErrorCode errorCode, String errorMessage);
}
