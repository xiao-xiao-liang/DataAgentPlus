package com.liang.data.agent.workflow.util;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Flux 流式处理工具类
 *
 * <p>核心职责:
 * 1. cascadeFlux: 级联两个有依赖关系的 Flux (前一个的聚合结果作为后一个的输入)
 * 2. createStreamingGenerator: 将 LLM 的 ChatResponse 流包装为 StateGraph 需要的
 * GraphResponse&lt;StreamingOutput&gt; 流，同时收集完整结果用于写入 state
 * </p>
 */
public final class FluxUtil {

    private FluxUtil() {
    }

    /**
     * 级联两个具有前后关系的 Flux
     *
     * @param originFlux   第一个 Flux
     * @param nextFluxFunc 根据第一个 Flux 的聚合结果生成第二个 Flux
     * @param aggregator   聚合第一个 Flux 的所有数据
     * @param preFlux      在第一个 Flux 前添加的信息
     * @param middleFlux   在两个 Flux 之间添加的信息
     * @param endFlux      在第二个 Flux 后添加的信息
     */
    public static <T, R> Flux<T> cascadeFlux(Flux<T> originFlux, Function<R, Flux<T>> nextFluxFunc,
                                             Function<Flux<T>, Mono<R>> aggregator, Flux<T> preFlux,
                                             Flux<T> middleFlux, Flux<T> endFlux) {
        Flux<T> cachedOrigin = originFlux.cache();
        Mono<R> aggregatedResult = aggregator.apply(cachedOrigin).cache();
        Flux<T> secondFlux = aggregatedResult.flatMapMany(nextFluxFunc);
        return preFlux.concatWith(cachedOrigin).concatWith(middleFlux).concatWith(secondFlux).concatWith(endFlux);
    }

    /**
     * 级联两个具有前后关系的 Flux (简化版，无前后缀)
     */
    public static <T, R> Flux<T> cascadeFlux(Flux<T> originFlux, Function<R, Flux<T>> nextFluxFunc,
                                             Function<Flux<T>, Mono<R>> aggregator) {
        return cascadeFlux(originFlux, nextFluxFunc, aggregator, Flux.empty(), Flux.empty(), Flux.empty());
    }

    /**
     * 创建流式生成器 (带开始/结束消息)
     *
     * <p>典型用法: 在 LLM 流前后添加状态提示信息</p>
     *
     * @param nodeClass         节点类 (用于提取节点名)
     * @param state             当前 state
     * @param startMessage      开始消息 (如 "正在生成SQL...")，可为 null
     * @param completionMessage 完成消息 (如 "SQL生成完成！")，可为 null
     * @param resultMapper      将收集到的完整文本映射为 state 更新
     * @param sourceFlux        LLM 的 ChatResponse 流
     */
    public static Flux<GraphResponse<StreamingOutput<ChatResponse>>> createStreamingGeneratorWithMessages(
            Class<? extends NodeAction> nodeClass, OverAllState state, String startMessage, String completionMessage,
            Function<String, Map<String, Object>> resultMapper, Flux<ChatResponse> sourceFlux) {
        String nodeName = nodeClass.getSimpleName();

        Flux<ChatResponse> startFlux = startMessage == null ? Flux.empty() : Flux.just(ChatResponseUtil.createResponse(startMessage));

        final StringBuilder collectedResult = new StringBuilder();
        Flux<ChatResponse> wrapperFlux = startFlux.concatWith(sourceFlux.doOnNext(chatResponse -> {
            String text = ChatResponseUtil.getText(chatResponse);
            collectedResult.append(text);
        }));

        if (!Objects.isNull(completionMessage)) {
            wrapperFlux = wrapperFlux.concatWith(Flux.just(ChatResponseUtil.createResponse(completionMessage)));
        }

        return toStreamingResponseFlux(nodeName, state, wrapperFlux, () -> resultMapper.apply(collectedResult.toString()));
    }

    /**
     * 创建流式生成器 (带开始/结束消息，无 start/completion 消息的简化版)
     */
    public static Flux<GraphResponse<StreamingOutput<ChatResponse>>> createStreamingGeneratorWithMessages(
            Class<? extends NodeAction> nodeClass, OverAllState state,
            Function<String, Map<String, Object>> resultMapper,
            Flux<ChatResponse> sourceFlux) {
        return createStreamingGeneratorWithMessages(nodeClass, state, null, null, resultMapper, sourceFlux);
    }

    /**
     * 创建流式生成器 (带前后缀 Flux)
     *
     * @param nodeClass    节点类
     * @param state        当前 state
     * @param sourceFlux   核心数据流
     * @param preFlux      前缀流
     * @param sufFlux      后缀流
     * @param sourceMapper 核心数据流的结果映射
     */
    public static Flux<GraphResponse<StreamingOutput<ChatResponse>>> createStreamingGenerator(
            Class<? extends NodeAction> nodeClass, OverAllState state, Flux<ChatResponse> sourceFlux,
            Flux<ChatResponse> preFlux, Flux<ChatResponse> sufFlux, Function<String, Map<String, Object>> sourceMapper) {
        String nodeName = nodeClass.getSimpleName();
        final StringBuilder collectedResult = new StringBuilder();
        sourceFlux = sourceFlux.doOnNext(r -> collectedResult.append(ChatResponseUtil.getText(r)));

        return toStreamingResponseFlux(nodeName, state, Flux.concat(preFlux, sourceFlux, sufFlux),
                () -> sourceMapper.apply(collectedResult.toString()));
    }

    /**
     * 将 ChatResponse 流转换为 GraphResponse&lt;StreamingOutput&gt; 流
     *
     * <p>这是 FluxUtil 的核心方法：
     * 1. 过滤无效的 ChatResponse
     * 2. 映射为 GraphResponse (携带节点名、state、输出类型)
     * 3. 在流结束时发出 done 信号 (携带收集到的完整结果)
     * 4. 在流出错时发出 error 信号
     * </p>
     */
    private static Flux<GraphResponse<StreamingOutput<ChatResponse>>> toStreamingResponseFlux(
            String nodeName, OverAllState state,
            Flux<ChatResponse> sourceFlux, Supplier<Map<String, Object>> resultSupplier) {
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> streamingFlux = sourceFlux
                .filter(response -> Optional.ofNullable(response)
                        .map(ChatResponse::getResult)
                        .map(Generation::getOutput)
                        .isPresent()) // 只有当整个链路都有值时才放行
                .map(resp -> GraphResponse.of(new StreamingOutput<>(
                        resp.getResult().getOutput(), resp,
                        nodeName, "", state, OutputType.from(true, nodeName)))
                );

        return streamingFlux
                .concatWith(Mono.fromSupplier(() -> GraphResponse.done(resultSupplier.get())))
                .onErrorResume(error -> Flux.just(GraphResponse.error(error)));
    }
}
