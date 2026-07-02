package com.liang.data.agent.ai.gateway;

/**
 * 模型网关调用状态，用于标识一次调用或一次尝试的生命周期结果。
 */
public enum ModelGatewayCallStatus {

    /**
     * 调用正在执行。
     */
    RUNNING,

    /**
     * 调用执行成功。
     */
    SUCCEEDED,

    /**
     * 调用执行失败。
     */
    FAILED,

    /**
     * 调用已被取消。
     */
    CANCELLED
}
