package com.liang.data.agent.controller;

import com.liang.data.agent.service.chat.SessionEventPublisher;
import com.liang.data.agent.service.chat.vo.SessionUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 聊天会话事件推送控制器（SSE 端点）
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SessionEventController {

    private final SessionEventPublisher sessionEventPublisher;

    /**
     * 流式推送当前 Agent 的会话标题等更新事件
     *
     * @param agentId  智能体 ID
     * @param response 反应式响应头
     * @return 事件流
     */
    @GetMapping(value = "/agent/{agentId}/sessions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<SessionUpdateEvent>> streamSessionUpdates(@PathVariable Integer agentId,
                                                                         ServerHttpResponse response) {
        response.getHeaders().add("Cache-Control", "no-cache");
        response.getHeaders().add("Connection", "keep-alive");
        response.getHeaders().add("Access-Control-Allow-Origin", "*");

        log.info("Client subscribed to session update stream for agent {}", agentId);
        return sessionEventPublisher.register(agentId)
                .doFinally(signal -> log.info("Session update stream finished for agent {} with signal: {}", agentId, signal));
    }
}
