package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.constant.SseEventKey;
import com.liang.data.agent.workflow.dto.node.ClarificationNormalizedDTO;
import com.liang.data.agent.workflow.dto.node.ClarificationRequestDTO;
import com.liang.data.agent.workflow.prompt.PromptHelper;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.JsonParseUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import com.liang.data.agent.workflow.util.WorkflowEventUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.WAIT_FOR_CLARIFICATION_CONFIRM;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_FEEDBACK_DATA;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_NORMALIZED_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_REQUEST;
import static com.liang.data.agent.common.constant.NodeOutputKey.EVIDENCE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.AGENT_ID;
import static com.liang.data.agent.common.constant.StateKey.INPUT_KEY;
import static com.liang.data.agent.common.constant.StateKey.THREAD_ID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClarificationNormalizeNode implements NodeAction {

    private final LlmService llmService;

    private final JsonParseUtil jsonParseUtil;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        ClarificationRequestDTO request = StateUtil.getObjectValue(state, CLARIFICATION_REQUEST, ClarificationRequestDTO.class);
        Map<String, Object> feedbackData = StateUtil.getMapValue(state, CLARIFICATION_FEEDBACK_DATA);
        String answer = String.valueOf(feedbackData.getOrDefault("answer", ""));
        String originalQuestion = StateUtil.getStringValue(state, INPUT_KEY, "");
        String evidence = StateUtil.getStringValue(state, EVIDENCE_OUTPUT, "");

        String prompt = PromptHelper.buildClarificationNormalizePrompt(
                originalQuestion,
                request.getQuestion(),
                answer,
                evidence
        );

        String llmOutput = llmService.callUser(prompt)
                .map(ChatResponseUtil::getText)
                .collect(StringBuilder::new, StringBuilder::append)
                .map(StringBuilder::toString)
                .block();
        ClarificationNormalizedDTO normalized = parseNormalized(llmOutput == null ? "" : llmOutput, request, answer);
        String agentId = StateUtil.getStringValue(state, AGENT_ID, "");
        String threadId = StateUtil.getStringValue(state, THREAD_ID, "");
        String event = WorkflowEventUtil.encode(
                agentId,
                threadId,
                SseEventKey.CLARIFICATION_CONFIRMATION,
                normalized
        );
        Flux<ChatResponse> sourceFlux = Flux.just(
                ChatResponseUtil.createResponse("正在归纳您的澄清..."),
                ChatResponseUtil.createPureResponse(event),
                ChatResponseUtil.createResponse(normalized.getConfirmationText())
        );
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(),
                state,
                ignored -> {
                    return Map.of(
                            CLARIFICATION_NORMALIZED_OUTPUT, normalized,
                            WAIT_FOR_CLARIFICATION_CONFIRM, true
                    );
                },
                sourceFlux
        );

        return Map.of(CLARIFICATION_NORMALIZED_OUTPUT, generator);
    }

    private ClarificationNormalizedDTO parseNormalized(String llmOutput,
                                                       ClarificationRequestDTO request,
                                                       String answer) {
        try {
            ClarificationNormalizedDTO dto = jsonParseUtil.tryConvertToObject(llmOutput.trim(), ClarificationNormalizedDTO.class);
            if (dto.getConfirmationText() == null || dto.getConfirmationText().isBlank()) {
                dto.setConfirmationText(buildFallbackConfirmation(dto, answer));
            }
            return dto;
        } catch (Exception e) {
            log.warn("澄清归纳 JSON 解析失败，使用兜底归纳: {}", e.getMessage());
            ClarificationNormalizedDTO dto = new ClarificationNormalizedDTO();
            dto.setTerm(request.getMissingTerm());
            dto.setNormalizedDefinition(answer);
            dto.setCalculationRule(answer);
            dto.setSuggestedMemoryType(request.getSuggestedMemoryType());
            dto.setConfidence(new BigDecimal("0.70"));
            dto.setConfirmationText(buildFallbackConfirmation(dto, answer));
            return dto;
        }
    }

    private String buildFallbackConfirmation(ClarificationNormalizedDTO dto, String answer) {
        String term = dto.getTerm() == null || dto.getTerm().isBlank() ? "该业务口径" : dto.getTerm();
        String definition = dto.getNormalizedDefinition() == null || dto.getNormalizedDefinition().isBlank()
                ? answer
                : dto.getNormalizedDefinition();
        return "根据您的澄清回复，" + term + " = " + definition + "。请确认这是否正确？如不正确，请输入纠正后的业务知识，继续进行分析。";
    }
}
