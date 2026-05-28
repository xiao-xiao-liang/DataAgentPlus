package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.enums.TextType;
import com.liang.data.agent.workflow.dto.node.IntentRecognitionOutputDTO;
import com.liang.data.agent.workflow.prompt.PromptHelper;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.JsonParseUtil;
import com.liang.data.agent.workflow.util.MarkdownParserUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Objects;

import static com.liang.data.agent.common.constant.NodeOutputKey.INTENT_RECOGNITION_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.INPUT_KEY;
import static com.liang.data.agent.common.constant.StateKey.MULTI_TURN_CONTEXT;

/**
 * 意图识别节点
 *
 * <p>调用 LLM 判断用户输入属于"闲聊/无关指令"还是"数据分析请求"</p>
 *
 * <p>输出: INTENT_RECOGNITION_NODE_OUTPUT → IntentRecognitionOutputDTO</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentRecognitionNode implements NodeAction {

    private final LlmService llmService;
    private final JsonParseUtil jsonParseUtil;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 从 state 中读取用户输入和多轮上下文
        String userInput = StateUtil.getStringValue(state, INPUT_KEY);
        String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");
        log.info("意图识别 - 用户输入: {}", userInput);

        // 2. 构建 Prompt 并调用 LLM
        String prompt = PromptHelper.buildIntentRecognitionPrompt(multiTurn, userInput);
//        log.debug("意图识别 Prompt:\n{}", prompt);
        Flux<ChatResponse> responseFlux = llmService.callUser(prompt);

        // 3. 包装为流式响应 (前后加 JSON 标记 + 状态消息)
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGenerator(
                this.getClass(), state, responseFlux,
                Flux.just(
                        ChatResponseUtil.createResponse("正在进行意图识别..."),
                        ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())
                ),
                Flux.just(
                        ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
                        ChatResponseUtil.createResponse("\n意图识别完成！")),
                this::handleIntentRecognition);

        return Map.of(INTENT_RECOGNITION_NODE_OUTPUT, generator);
    }

    /**
     * 处理 LLM 返回的完整文本，解析为 IntentRecognitionOutputDTO
     */
    private Map<String, Object> handleIntentRecognition(String llmOutput) {
        String cleanOutput = MarkdownParserUtil.extractRawText(llmOutput);
        log.info("意图识别结果: {}", cleanOutput);

        IntentRecognitionOutputDTO intentResult = null;
        try {
            intentResult = jsonParseUtil.tryConvertToObject(cleanOutput, IntentRecognitionOutputDTO.class);
        } catch (Exception e) {
            log.error("解析意图识别结果失败: {}", cleanOutput, e);
        }

        if (Objects.isNull(intentResult)) {
            return Map.of();
        }
        return Map.of(INTENT_RECOGNITION_NODE_OUTPUT, intentResult);
    }
}
