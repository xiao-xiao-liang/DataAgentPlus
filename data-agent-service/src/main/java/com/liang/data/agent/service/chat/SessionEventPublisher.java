package com.liang.data.agent.service.chat;

import com.liang.data.agent.service.chat.vo.SessionUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 管理推送会话更新给前端的 SSE 流服务
 */
@Slf4j
@Service
public class SessionEventPublisher {

    private final Map<Integer, AgentSessionSink> sinks = new ConcurrentHashMap<>();

    /**
     * 注册前端订阅
     */
    public Flux<ServerSentEvent<SessionUpdateEvent>> register(Integer agentId) {
        AgentSessionSink sink = sinks.computeIfAbsent(agentId, id -> new AgentSessionSink());
        
        // 创建 5 秒一次的心跳检测 SSE，确保连接不中断
        Flux<ServerSentEvent<SessionUpdateEvent>> heartbeat = Flux.interval(Duration.ofSeconds(5))
                .map(i -> ServerSentEvent.<SessionUpdateEvent>builder().comment("heartbeat").build());
        
        sink.increment();
        log.info("Registered session subscriber for agent {}, current sub count: {}", agentId, sink.getSubscribers().get());
        
        return Flux.merge(heartbeat, sink.getSink().asFlux())
                .doFinally(signalType -> cleanup(agentId, sink, signalType));
    }

    /**
     * 发布标题更新事件
     */
    public void publishTitleUpdated(Integer agentId, String sessionId, String title) {
        if (agentId == null) {
            return;
        }
        SessionUpdateEvent event = SessionUpdateEvent.titleUpdated(sessionId, title);
        AgentSessionSink sink = sinks.get(agentId);
        if (sink == null) {
            log.debug("No active subscribers for agent {}, skip title update event emission", agentId);
            return;
        }
        
        Sinks.EmitResult result = sink.getSink().tryEmitNext(
                ServerSentEvent.builder(event).event(event.getType()).build()
        );
        if (result.isFailure()) {
            log.warn("Failed to emit session title update for agent {}, session {}, reason: {}", agentId, sessionId, result);
        }
    }

    private void cleanup(Integer agentId, AgentSessionSink sink, SignalType signalType) {
        int current = sink.decrement();
        log.info("Cleanup session update subscriber for agent {}, signal: {}, remaining: {}", agentId, signalType, current);
        if (current <= 0) {
            if (sinks.remove(agentId, sink)) {
                sink.getSink().tryEmitComplete();
                log.info("Removed session update sink for agent {}", agentId);
            }
        }
    }

    private static class AgentSessionSink {
        private final AtomicInteger subscribers = new AtomicInteger(0);
        private final Sinks.Many<ServerSentEvent<SessionUpdateEvent>> sink = Sinks.many()
                .multicast()
                .onBackpressureBuffer();

        public void increment() {
            subscribers.incrementAndGet();
        }

        public int decrement() {
            return subscribers.decrementAndGet();
        }

        public AtomicInteger getSubscribers() {
            return subscribers;
        }

        public Sinks.Many<ServerSentEvent<SessionUpdateEvent>> getSink() {
            return sink;
        }
    }
}
