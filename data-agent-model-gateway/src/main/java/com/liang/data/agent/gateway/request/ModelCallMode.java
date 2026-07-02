package com.liang.data.agent.gateway.request;

/**
 * 模型调用方式，区分非流式聚合结果与流式调用。
 */
public enum ModelCallMode {
    /**
     * 非流式聚合结果模式，不表示阻塞线程。
     */
    BLOCK,

    /**
     * 流式增量结果模式。
     */
    STREAM
}
