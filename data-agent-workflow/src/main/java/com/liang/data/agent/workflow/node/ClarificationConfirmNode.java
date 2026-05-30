package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.workflow.dto.node.ClarificationNormalizedDTO;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.WAIT_FOR_CLARIFICATION_CONFIRM;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_CONFIRM_DATA;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_CONFIRMED;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_EVIDENCE;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_NORMALIZED_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.EVIDENCE_OUTPUT;

@Slf4j
@Component
public class ClarificationConfirmNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) {
        ClarificationNormalizedDTO normalized = StateUtil.getObjectValue(
                state,
                CLARIFICATION_NORMALIZED_OUTPUT,
                ClarificationNormalizedDTO.class
        );
        Map<String, Object> confirmData = StateUtil.getMapValue(state, CLARIFICATION_CONFIRM_DATA);
        String content = String.valueOf(confirmData.getOrDefault("content", ""));
        String clarificationEvidence = buildEvidence(normalized, content);
        String existingEvidence = StateUtil.getStringValue(state, EVIDENCE_OUTPUT, "");
        String mergedEvidence = existingEvidence + "\n\n[用户确认的业务澄清]\n" + clarificationEvidence;

        Flux<ChatResponse> sourceFlux = Flux.just(ChatResponseUtil.createResponse("已确认业务口径，继续进行分析。"));
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(),
                state,
                llmOutput -> Map.of(
                        CLARIFICATION_EVIDENCE, clarificationEvidence,
                        CLARIFICATION_CONFIRMED, true,
                        EVIDENCE_OUTPUT, mergedEvidence,
                        WAIT_FOR_CLARIFICATION_CONFIRM, false
                ),
                sourceFlux
        );

        log.info("澄清确认完成: {}", clarificationEvidence);
        return Map.of(CLARIFICATION_EVIDENCE, generator);
    }

    private String buildEvidence(ClarificationNormalizedDTO normalized, String confirmContent) {
        boolean correction = confirmContent != null
                && !confirmContent.isBlank()
                && !(confirmContent.contains("正确") || confirmContent.contains("确认") || confirmContent.equalsIgnoreCase("ok"));
        if (correction) {
            return confirmContent;
        }
        StringBuilder builder = new StringBuilder();
        if (normalized.getTerm() != null && !normalized.getTerm().isBlank()) {
            builder.append("业务术语：").append(normalized.getTerm()).append("\n");
        }
        if (normalized.getNormalizedDefinition() != null && !normalized.getNormalizedDefinition().isBlank()) {
            builder.append("业务定义：").append(normalized.getNormalizedDefinition()).append("\n");
        }
        if (normalized.getCalculationRule() != null && !normalized.getCalculationRule().isBlank()) {
            builder.append("计算口径：").append(normalized.getCalculationRule()).append("\n");
        }
        if (normalized.getSynonyms() != null && !normalized.getSynonyms().isEmpty()) {
            builder.append("同义词：").append(String.join(",", normalized.getSynonyms())).append("\n");
        }
        return builder.toString().trim();
    }
}
