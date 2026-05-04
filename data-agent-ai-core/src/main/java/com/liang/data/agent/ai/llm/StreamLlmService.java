package com.liang.data.agent.ai.llm;

import com.liang.data.agent.ai.model.AiModelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * 流式 LLM 调用实现 — 逐 Token 推送, 用于 SSE 场景
 */
@RequiredArgsConstructor
public class StreamLlmService implements LlmService {

    private final AiModelRegistry registry;

    @Override
    public Flux<ChatResponse> call(String system, String user) {
        return registry.getChatClient().prompt().system(system).user(user).stream().chatResponse();
    }

    @Override
    public Flux<ChatResponse> callSystem(String system) {
        return registry.getChatClient().prompt().system(system).stream().chatResponse();
    }

    @Override
    public Flux<ChatResponse> callUser(String user) {
        return registry.getChatClient().prompt().user(user).stream().chatResponse();
    }
}
