package com.liang.data.agent.gateway.api;

/**
 * 模型调用方式，区分阻塞调用与流式调用。
 */
public enum ModelCallMode {
    BLOCK,
    STREAM
}
