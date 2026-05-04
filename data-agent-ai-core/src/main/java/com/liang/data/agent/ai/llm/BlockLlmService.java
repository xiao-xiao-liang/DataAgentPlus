package com.liang.data.agent.ai.llm;

import com.liang.data.agent.ai.model.AiModelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 阻塞 LLM 调用实现 — 等待完整响应, 包装为单元素 Flux
 */
@RequiredArgsConstructor
public class BlockLlmService implements LlmService {
    
    private final AiModelRegistry registry;

    @Override
    public Flux<ChatResponse> call(String system, String user) {
        return Mono.fromCallable(() ->
                registry.getChatClient().prompt()
                        .system(system).user(user).call().chatResponse()
        ).flux();
    }

    @Override
    public Flux<ChatResponse> callSystem(String system) {
        return Mono.fromCallable(() ->
                registry.getChatClient().prompt()
                        .system(system).call().chatResponse()
        ).flux();
    }

    @Override
    public Flux<ChatResponse> callUser(String user) {
        return Mono.fromCallable(() ->
                registry.getChatClient().prompt()
                        .user(user).call().chatResponse()
        ).flux();
    }
}
