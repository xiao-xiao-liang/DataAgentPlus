package com.liang.data.agent.ai.llm;

import com.liang.data.agent.ai.util.ChatResponseUtil;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * LLM 调用服务接口
 *
 * <p>所有方法统一返回 Flux<ChatResponse>:
 * <ul>
 *   <li>STREAM 模式: 逐 Token 推送</li>
 *   <li>BLOCK 模式: 包装为单元素 Flux</li>
 * </ul>
 * </p>
 */
public interface LlmService {

    /**
     * 系统消息 + 用户消息
     */
    Flux<ChatResponse> call(String system, String user);

    /**
     * 仅系统消息
     */
    Flux<ChatResponse> callSystem(String system);

    /**
     * 仅用户消息
     */
    Flux<ChatResponse> callUser(String user);

    /**
     * 便捷方法: Flux<ChatResponse> → Flux<String>
     */
    default Flux<String> toStringFlux(Flux<ChatResponse> responseFlux) {
        return responseFlux.map(ChatResponseUtil::getText);
    }
}
