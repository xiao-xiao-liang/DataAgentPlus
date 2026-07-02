package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.enums.TextType;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.gateway.constants.ModelGatewayConstant;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import com.liang.data.agent.workflow.prompt.PromptHelper;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.JsonParseUtil;
import com.liang.data.agent.workflow.util.MarkdownParserUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Objects;

import static com.liang.data.agent.common.constant.NodeOutputKey.EVIDENCE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.QUERY_ENHANCE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.INPUT_KEY;
import static com.liang.data.agent.common.constant.StateKey.MULTI_TURN_CONTEXT;

/**
 * 查询增强节点
 *
 * <p>根据 evidence 信息对用户查询进行重写和扩展:
 * - canonicalQuery: 规范化查询 (含绝对时间、解析后的业务术语)
 * - expandedQueries: 扩展查询列表 (用于后续 Schema 召回)
 * </p>
 *
 * <p>输出: QUERY_ENHANCE_NODE_OUTPUT → QueryEnhanceOutputDTO</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryEnhanceNode implements NodeAction {

    private static final int MAX_EMPTY_RESPONSE_RETRY_COUNT = 1;

    private final LlmService llmService;
    private final JsonParseUtil jsonParseUtil;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String userInput = StateUtil.getStringValue(state, INPUT_KEY);
        String evidence = StateUtil.getStringValue(state, EVIDENCE_OUTPUT);
        String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");
        log.info("查询增强 - 用户输入: {}", userInput);

        // 构建 Prompt 并调用 LLM
        String prompt = PromptHelper.buildQueryEnhancePrompt(multiTurn, userInput, evidence);
        Flux<ChatResponse> responseFlux = callQueryEnhance(prompt, 0);

        // 包装为流式响应
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGenerator(
                this.getClass(), state, responseFlux,
                Flux.just(
                        ChatResponseUtil.createResponse("正在进行问题增强..."),
                        ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())
                ),
                Flux.just(
                        ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
                        ChatResponseUtil.createResponse("\n问题增强完成！")
                ),
                this::handleQueryEnhance
        );

        return Map.of(QUERY_ENHANCE_NODE_OUTPUT, generator);
    }

    /**
     * 调用查询增强模型，并在响应正文为空时重试一次。
     *
     * @param prompt     查询增强提示词
     * @param retryCount 当前已重试次数
     * @return 包含有效正文的模型响应流
     */
    private Flux<ChatResponse> callQueryEnhance(String prompt, int retryCount) {
        return Flux.defer(() -> llmService.callUser(ModelGatewayConstant.QUERY_ENHANCE, prompt))
                .filter(response -> StringUtils.hasText(ChatResponseUtil.getText(response)))
                .switchIfEmpty(Flux.defer(() -> {
                    if (retryCount < MAX_EMPTY_RESPONSE_RETRY_COUNT) {
                        log.warn("查询增强模型返回空响应，准备进行第 {} 次重试", retryCount + 1);
                        return callQueryEnhance(prompt, retryCount + 1);
                    }
                    return Flux.error(new ServiceException("查询增强模型连续返回空响应"));
                }));
    }

    private Map<String, Object> handleQueryEnhance(String llmOutput) {
        String enhanceResult = MarkdownParserUtil.extractRawText(llmOutput.trim());
        log.info("查询增强结果: {}", enhanceResult);

        QueryEnhanceOutputDTO dto = null;
        try {
            dto = jsonParseUtil.tryConvertToObject(enhanceResult, QueryEnhanceOutputDTO.class);
            log.info("规范化查询: {}", dto.getCanonicalQuery());
        } catch (Exception e) {
            log.error("解析查询增强结果失败: {}", enhanceResult, e);
        }

        if (Objects.isNull(dto)) {
            return Map.of();
        }
        return Map.of(QUERY_ENHANCE_NODE_OUTPUT, dto);
    }
}
