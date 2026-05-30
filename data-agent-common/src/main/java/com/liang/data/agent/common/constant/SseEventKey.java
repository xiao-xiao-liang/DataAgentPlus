package com.liang.data.agent.common.constant;

import lombok.NoArgsConstructor;

/**
 * SSE 事件 Key
 */
@NoArgsConstructor
public final class SseEventKey {

    /**
     * SSE 完成事件
     */
    public static final String STREAM_EVENT_COMPLETE = "complete";
    
    /**
     * SSE 错误事件
     */
    public static final String STREAM_EVENT_ERROR = "error";

    public static final String CLARIFICATION_REQUEST = "clarification_request";

    public static final String CLARIFICATION_CONFIRMATION = "clarification_confirmation";

    public static final String MEMORY_CANDIDATE = "memory_candidate";
}
