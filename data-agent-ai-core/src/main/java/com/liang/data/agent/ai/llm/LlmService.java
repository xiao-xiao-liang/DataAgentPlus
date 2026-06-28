package com.liang.data.agent.ai.llm;

import com.liang.data.agent.ai.util.ChatResponseUtil;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * LLM 调用服务接口，统一定义系统消息、用户消息以及显式场景编码调用入口。
 *
 * <p>所有方法统一返回 Flux&lt;ChatResponse&gt;：</p>
 * <ul>
 *   <li>STREAM 模式：逐 Token 推送；</li>
 *   <li>BLOCK 模式：包装为单元素 Flux。</li>
 * </ul>
 */
public interface LlmService {

    /**
     * 兼容入口：系统消息 + 用户消息。
     *
     * <p>旧方法不携带显式 sceneCode，保留用于兼容既有实现。</p>
     */
    Flux<ChatResponse> call(String system, String user);

    /**
     * 显式场景入口：系统消息 + 用户消息。
     *
     * <p>sceneCode 用于模型网关观测，默认回落到旧入口，避免破坏既有实现。</p>
     */
    default Flux<ChatResponse> call(String sceneCode, String system, String user) {
        // 1. 默认不改变旧实现行为 2. 交由旧入口完成实际调用
        return call(system, user);
    }

    /**
     * 兼容入口：仅系统消息。
     *
     * <p>旧方法不携带显式 sceneCode，保留用于兼容既有实现。</p>
     */
    Flux<ChatResponse> callSystem(String system);

    /**
     * 显式场景入口：仅系统消息。
     *
     * <p>sceneCode 用于模型网关观测，默认回落到旧入口，避免破坏既有实现。</p>
     */
    default Flux<ChatResponse> callSystem(String sceneCode, String system) {
        // 1. 默认不改变旧实现行为 2. 交由旧入口完成实际调用
        return callSystem(system);
    }

    /**
     * 兼容入口：仅用户消息。
     *
     * <p>旧方法不携带显式 sceneCode，保留用于兼容既有实现。</p>
     */
    Flux<ChatResponse> callUser(String user);

    /**
     * 显式场景入口：仅用户消息。
     *
     * <p>sceneCode 用于模型网关观测，默认回落到旧入口，避免破坏既有实现。</p>
     */
    default Flux<ChatResponse> callUser(String sceneCode, String user) {
        // 1. 默认不改变旧实现行为 2. 交由旧入口完成实际调用
        return callUser(user);
    }

    /**
     * 便捷方法：将 Flux&lt;ChatResponse&gt; 转换为 Flux&lt;String&gt;。
     */
    default Flux<String> toStringFlux(Flux<ChatResponse> responseFlux) {
        return responseFlux.map(ChatResponseUtil::getText);
    }
}
