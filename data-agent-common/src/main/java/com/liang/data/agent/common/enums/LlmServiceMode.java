package com.liang.data.agent.common.enums;

/**
 * LLM 调用模式枚举
 *
 * <p>对应 application.yml 中 data-agent.llm-service-mode 配置</p>
 */
public enum LlmServiceMode {

    /**
     * 流式调用: 逐 Token 返回, 用于 SSE 场景
     */
    STREAM,

    /**
     * 阻塞调用: 等待完整响应后返回, 用于内部处理场景
     */
    BLOCK
}
