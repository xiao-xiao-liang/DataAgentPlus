package com.liang.data.agent.ai.gateway;

import com.liang.data.agent.gateway.api.GatewayChunk;
import com.liang.data.agent.gateway.api.GatewayResult;
import com.liang.data.agent.gateway.api.ModelCallMode;
import com.liang.data.agent.gateway.api.ModelGateway;
import com.liang.data.agent.gateway.api.ModelGatewayRequest;
import com.liang.data.agent.gateway.api.ModelRoute;
import com.liang.data.agent.gateway.api.ModelUsage;
import com.liang.data.agent.gateway.context.GatewayExecutionContext;
import com.liang.data.agent.gateway.context.GatewayReactorContext;
import com.liang.data.agent.gateway.error.ModelGatewayErrorCode;
import com.liang.data.agent.gateway.error.ModelGatewayException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 默认模型网关，负责阶段 2 单模型 Provider 的调用生命周期编排。
 *
 * <p>该实现只串联固定 Provider、调用记录器、超时控制和基础指标，不做动态路由、重试、熔断或降级。</p>
 */
public class DefaultModelGateway implements ModelGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultModelGateway.class);

    private static final String FALLBACK_SESSION_ID = "model-gateway-standalone";

    private static final String UNKNOWN_TAG_VALUE = "unknown";

    private static final String NONE_ERROR_CODE = "none";

    private static final int FIRST_ATTEMPT_NO = 1;

    private final OpenAiCompatibleGatewayProvider provider;

    private final ModelGatewayInvocationRecorder recorder;

    private final Duration defaultTimeout;

    private final MeterRegistry meterRegistry;

    /**
     * 创建默认模型网关。
     *
     * @param provider OpenAI 兼容模型 Provider
     * @param recorder 模型网关调用记录器
     * @param defaultTimeout 默认调用超时时间
     * @param meterRegistry 指标注册器，允许为空
     */
    public DefaultModelGateway(OpenAiCompatibleGatewayProvider provider,
                               ModelGatewayInvocationRecorder recorder,
                               Duration defaultTimeout,
                               MeterRegistry meterRegistry) {
        this.provider = Objects.requireNonNull(provider, "模型 Provider 不能为空");
        this.recorder = Objects.requireNonNull(recorder, "模型网关调用记录器不能为空");
        this.defaultTimeout = Objects.requireNonNull(defaultTimeout, "默认超时时间不能为空");
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<GatewayResult> call(ModelGatewayRequest request) {
        Objects.requireNonNull(request, "模型网关请求不能为空");
        request.requireMode(ModelCallMode.BLOCK);
        return Mono.deferContextual(contextView -> {
            GatewayExecutionContext context = GatewayReactorContext.current(contextView)
                    .defaultIfEmpty(fallbackContext())
                    .block();
            InvocationState state = new InvocationState(request, context, ModelCallMode.BLOCK);
            AtomicBoolean finished = new AtomicBoolean(false);
            long startNanos = System.nanoTime();
            startLifecycle(state);
            return Mono.fromCallable(() -> provider.call(state.invocationId(), request))
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(defaultTimeout)
                    .map(result -> finishCallSucceeded(state, result, startNanos, finished))
                    .onErrorMap(this::convertException)
                    .doOnError(ModelGatewayException.class,
                            exception -> finishFailedWhenNeeded(state, exception, startNanos, finished))
                    .doFinally(signalType -> {
                        if (signalType == reactor.core.publisher.SignalType.CANCEL
                                && finished.compareAndSet(false, true)) {
                            finishCancelled(state, startNanos);
                        }
                    });
        });
    }

    @Override
    public Flux<GatewayChunk> stream(ModelGatewayRequest request) {
        Objects.requireNonNull(request, "模型网关请求不能为空");
        request.requireMode(ModelCallMode.STREAM);
        return Flux.deferContextual(contextView -> {
            GatewayExecutionContext context = GatewayReactorContext.current(contextView)
                    .defaultIfEmpty(fallbackContext())
                    .block();
            InvocationState state = new InvocationState(request, context, ModelCallMode.STREAM);
            AtomicBoolean finished = new AtomicBoolean(false);
            long startNanos = System.nanoTime();
            startLifecycle(state);
            return Flux.defer(() -> provider.stream(state.invocationId(), request))
                    .timeout(defaultTimeout)
                    .onErrorMap(this::convertException)
                    .doOnNext(chunk -> finishStreamSucceededWhenNeeded(state, chunk, startNanos, finished))
                    .doOnError(ModelGatewayException.class,
                            exception -> finishStreamFailed(state, exception, startNanos, finished))
                    .doFinally(signalType -> {
                        if (signalType == reactor.core.publisher.SignalType.CANCEL
                                && finished.compareAndSet(false, true)) {
                            finishCancelled(state, startNanos);
                        }
                    });
        });
    }

    private GatewayResult finishCallSucceeded(InvocationState state, GatewayResult result, long startNanos,
                                              AtomicBoolean finished) {
        // 1. 成功、失败、取消路径统一通过 CAS 收口，避免重复记录终态。
        if (!finished.compareAndSet(false, true)) {
            return result;
        }
        // 2. Provider 成功返回后记录尝试与主调用成功状态。
        safeRecord("结束模型调用尝试", () -> recorder.finishAttempt(state.attemptId(),
                ModelGatewayCallStatus.SUCCEEDED, null, null, null));
        safeRecord("结束模型网关调用", () -> recorder.finishInvocation(state.invocationId(),
                ModelGatewayCallStatus.SUCCEEDED, result.route(), result.usage(), null, null));
        // 3. 使用脱敏后的场景、路由和状态记录基础指标。
        recordMetrics(state.request().sceneCode(), result.route(), ModelGatewayCallStatus.SUCCEEDED,
                null, result.usage(), startNanos);
        return result;
    }

    private void finishFailedWhenNeeded(InvocationState state, ModelGatewayException exception, long startNanos,
                                        AtomicBoolean finished) {
        // 1. 失败路径仅在尚未被成功或取消收口时记录终态。
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        // 2. 复用统一失败收口，保持错误码、生命周期和指标记录一致。
        finishFailed(state, exception, startNanos);
    }

    private void finishStreamSucceededWhenNeeded(InvocationState state, GatewayChunk chunk, long startNanos,
                                                 AtomicBoolean finished) {
        // 1. 非完成片段只透传，不结束生命周期。
        if (!chunk.finished() || !finished.compareAndSet(false, true)) {
            return;
        }
        // 2. 完成片段携带最终用量与路由，作为成功结束依据。
        safeRecord("结束模型流式调用尝试", () -> recorder.finishAttempt(state.attemptId(),
                ModelGatewayCallStatus.SUCCEEDED, null, null, null));
        safeRecord("结束模型流式网关调用", () -> recorder.finishInvocation(state.invocationId(),
                ModelGatewayCallStatus.SUCCEEDED, chunk.route(), chunk.usage(), null, null));
        recordMetrics(state.request().sceneCode(), chunk.route(), ModelGatewayCallStatus.SUCCEEDED,
                null, chunk.usage(), startNanos);
    }

    private void finishFailed(InvocationState state, ModelGatewayException exception, long startNanos) {
        ModelGatewayErrorCode errorCode = exception.getGatewayErrorCode();
        // 1. 使用结构化错误码记录失败，错误摘要只使用错误码默认文案。
        safeRecord("结束失败模型调用尝试", () -> recorder.finishAttempt(state.attemptId(),
                ModelGatewayCallStatus.FAILED, null, errorCode, errorCode.message()));
        safeRecord("结束失败模型网关调用", () -> recorder.finishInvocation(state.invocationId(),
                ModelGatewayCallStatus.FAILED, null, null, errorCode, errorCode.message()));
        // 2. 失败场景无法可靠获得路由时使用 unknown 标签，避免记录调用标识。
        recordMetrics(state.request().sceneCode(), null, ModelGatewayCallStatus.FAILED,
                errorCode, null, startNanos);
    }

    private void finishStreamFailed(InvocationState state, ModelGatewayException exception, long startNanos,
                                    AtomicBoolean finished) {
        // 1. 保证流式失败与取消只记录一次终态。
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        // 2. 复用非流式失败收口逻辑记录生命周期和指标。
        finishFailed(state, exception, startNanos);
    }

    private void finishCancelled(InvocationState state, long startNanos) {
        // 1. 取消时记录取消状态与统一取消错误码。
        safeRecord("结束取消模型调用尝试", () -> recorder.finishAttempt(state.attemptId(),
                ModelGatewayCallStatus.CANCELLED, null, ModelGatewayErrorCode.CALL_CANCELLED,
                ModelGatewayErrorCode.CALL_CANCELLED.message()));
        safeRecord("结束取消模型网关调用", () -> recorder.finishInvocation(state.invocationId(),
                ModelGatewayCallStatus.CANCELLED, null, null, ModelGatewayErrorCode.CALL_CANCELLED,
                ModelGatewayErrorCode.CALL_CANCELLED.message()));
        // 2. 取消场景记录基础指标，不包含运行标识或链路标识。
        recordMetrics(state.request().sceneCode(), null, ModelGatewayCallStatus.CANCELLED,
                ModelGatewayErrorCode.CALL_CANCELLED, null, startNanos);
    }

    private void startLifecycle(InvocationState state) {
        // 1. 先记录主调用开始，建立 invocation 生命周期。
        safeRecord("开始模型网关调用", () -> recorder.startInvocation(state.invocationId(), state.context(),
                state.request().sceneCode(), state.mode()));
        // 2. 阶段 2 固定单次尝试，Provider 与模型在调用前尚未从结果中获得，使用 unknown 占位。
        safeRecord("开始模型调用尝试", () -> recorder.startAttempt(state.invocationId(), state.attemptId(),
                FIRST_ATTEMPT_NO, UNKNOWN_TAG_VALUE, UNKNOWN_TAG_VALUE));
    }

    private ModelGatewayException convertException(Throwable throwable) {
        Throwable unwrapped = Exceptions.unwrap(throwable);
        // 1. 已经是模型网关异常时原样透传，避免丢失结构化错误码。
        if (unwrapped instanceof ModelGatewayException exception) {
            return exception;
        }
        // 2. Reactor 超时或底层超时统一映射为 Provider 超时，其余异常映射为 Provider 不可用。
        if (unwrapped instanceof TimeoutException) {
            return new ModelGatewayException(ModelGatewayErrorCode.PROVIDER_TIMEOUT);
        }
        return new ModelGatewayException(ModelGatewayErrorCode.PROVIDER_UNAVAILABLE);
    }

    private GatewayExecutionContext fallbackContext() {
        // 1. 缺少 Reactor 上下文时生成独立运行标识，保障记录器必需字段完整。
        String runId = UUID.randomUUID().toString();
        // 2. traceId 去除横线，便于与常见链路格式保持一致。
        String traceId = UUID.randomUUID().toString().replace("-", "");
        return new GatewayExecutionContext(runId, traceId, FALLBACK_SESSION_ID, null, null, null);
    }

    private void safeRecord(String stage, Runnable runnable) {
        try {
            // 1. 执行记录器操作，记录器失败不得影响模型调用主链路。
            runnable.run();
        } catch (RuntimeException exception) {
            // 2. 日志仅记录阶段信息和异常类型，不记录 Prompt、响应正文、调用标识或异常正文。
            LOGGER.warn("模型网关记录器调用失败，阶段：{}，异常类型：{}", stage,
                    exception.getClass().getName());
        }
    }

    private void recordMetrics(String sceneCode, ModelRoute route, ModelGatewayCallStatus status,
                               ModelGatewayErrorCode errorCode, ModelUsage usage, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        String providerTag = route == null ? UNKNOWN_TAG_VALUE : route.provider();
        String modelTag = route == null ? UNKNOWN_TAG_VALUE : route.model();
        String statusTag = status.name();
        String errorCodeTag = errorCode == null ? NONE_ERROR_CODE : errorCode.name();
        String[] tags = new String[] {
                "scene_code", sceneCode,
                "provider", providerTag,
                "model", modelTag,
                "status", statusTag,
                "error_code", errorCodeTag
        };
        try {
            // 1. 记录调用次数、耗时与错误次数，标签中不携带任何运行或调用标识。
            meterRegistry.counter("model_gateway_invocations_total", tags).increment();
            Timer.builder("model_gateway_invocation_duration_seconds")
                    .tags(tags)
                    .register(meterRegistry)
                    .record(Duration.ofNanos(System.nanoTime() - startNanos));
            if (errorCode != null) {
                meterRegistry.counter("model_gateway_errors_total", tags).increment();
            }
            // 2. 成功且存在用量时记录总 Token 数。
            if (usage != null) {
                meterRegistry.counter("model_gateway_tokens_total", tags).increment(usage.totalTokens());
            }
        } catch (RuntimeException exception) {
            // 3. 指标系统异常不得影响主链路，日志不记录 Prompt、响应正文或调用标识。
            LOGGER.warn("模型网关指标记录失败，阶段：记录调用指标，异常类型：{}",
                    exception.getClass().getName());
        }
    }

    /**
     * 单次网关调用状态，集中保存生命周期记录所需的稳定标识和请求上下文。
     *
     * @param request 模型网关请求
     * @param context 执行上下文
     * @param mode 调用模式
     * @param invocationId 调用标识
     * @param attemptId 尝试标识
     */
    private record InvocationState(ModelGatewayRequest request, GatewayExecutionContext context, ModelCallMode mode,
                                   String invocationId, String attemptId) {

        InvocationState(ModelGatewayRequest request, GatewayExecutionContext context, ModelCallMode mode) {
            this(request, context, mode, UUID.randomUUID().toString(), UUID.randomUUID().toString());
        }
    }
}
