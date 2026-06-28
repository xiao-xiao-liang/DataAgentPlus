package com.liang.data.agent.service.ratelimit;

import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.ratelimit.ResourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 带资源门控的大模型服务装饰器。
 *
 * <p>统一保护所有 {@link LlmService} 调用入口，避免不同节点绕过 LLM 并发限制。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class ResourceGatedLlmService implements LlmService {

    private static final String UNKNOWN_SCENE = "unknown_scene";
    private static final Pattern SAFE_SCENE_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");

    private final LlmService delegate;
    private final ResourceGate resourceGate;

    @Override
    public Flux<ChatResponse> call(String system, String user) {
        return callWithPermit("llm-call", () -> delegate.call(system, user));
    }

    @Override
    public Flux<ChatResponse> call(String sceneCode, String system, String user) {
        return callWithPermit(buildOwnerId("llm-call", sceneCode), () -> delegate.call(sceneCode, system, user));
    }

    @Override
    public Flux<ChatResponse> callSystem(String system) {
        return callWithPermit("llm-call-system", () -> delegate.callSystem(system));
    }

    @Override
    public Flux<ChatResponse> callSystem(String sceneCode, String system) {
        return callWithPermit(buildOwnerId("llm-call-system", sceneCode), () -> delegate.callSystem(sceneCode, system));
    }

    @Override
    public Flux<ChatResponse> callUser(String user) {
        return callWithPermit("llm-call-user", () -> delegate.callUser(user));
    }

    @Override
    public Flux<ChatResponse> callUser(String sceneCode, String user) {
        return callWithPermit(buildOwnerId("llm-call-user", sceneCode), () -> delegate.callUser(sceneCode, user));
    }

    private Flux<ChatResponse> callWithPermit(String ownerId, Supplier<Flux<ChatResponse>> supplier) {
        // 1. 先申请 LLM 资源许可，资源不足时直接返回明确提示
        ResourcePermit permit = resourceGate.tryAcquire(ResourceType.LLM_CALL, ownerId, Duration.ZERO);
        if (!permit.acquired()) {
            log.warn("LLM 资源繁忙，跳过本次调用，占用方：{}", ownerId);
            return Flux.just(ChatResponseUtil.createResponse("大模型资源繁忙，请稍后重试。"));
        }

        // 2. 执行真实 LLM 流，并在完成、异常或取消时释放许可
        try {
            return supplier.get()
                    .doFinally(signalType -> permit.close());
        } catch (RuntimeException exception) {
            permit.close();
            return Flux.error(exception);
        }
    }

    /**
     * 构建资源占用方标识。
     *
     * @param prefix 资源调用前缀
     * @param sceneCode 原始场景编码
     * @return 安全的资源占用方标识
     */
    private String buildOwnerId(String prefix, String sceneCode) {
        // 1. 先归一化场景编码，避免提示词或用户输入进入资源标识
        String sanitizedSceneCode = sanitizeSceneCode(sceneCode);
        // 2. 再拼接受控的资源占用方标识
        return prefix + ":" + sanitizedSceneCode;
    }

    /**
     * 归一化场景编码，非法输入统一降级为固定值。
     *
     * @param sceneCode 原始场景编码
     * @return 安全场景编码
     */
    private String sanitizeSceneCode(String sceneCode) {
        if (sceneCode == null) {
            return UNKNOWN_SCENE;
        }
        if (!SAFE_SCENE_CODE_PATTERN.matcher(sceneCode).matches()) {
            return UNKNOWN_SCENE;
        }
        return sceneCode;
    }
}
