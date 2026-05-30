package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.constant.SseEventKey;
import com.liang.data.agent.common.enums.KnowledgeCandidateScope;
import com.liang.data.agent.common.enums.KnowledgeCandidateStatus;
import com.liang.data.agent.common.enums.KnowledgeCandidateType;
import com.liang.data.agent.dal.entity.KnowledgeCandidateEntity;
import com.liang.data.agent.dal.mapper.KnowledgeCandidateMapper;
import com.liang.data.agent.workflow.dto.node.ClarificationNormalizedDTO;
import com.liang.data.agent.workflow.dto.node.MemoryCandidateOutputDTO;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import com.liang.data.agent.workflow.util.WorkflowEventUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_EVIDENCE;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_FEEDBACK_DATA;
import static com.liang.data.agent.common.constant.NodeOutputKey.CLARIFICATION_NORMALIZED_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.MEMORY_CANDIDATE_ID;
import static com.liang.data.agent.common.constant.NodeOutputKey.MEMORY_CANDIDATE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.MEMORY_SAVE_REQUIRED;
import static com.liang.data.agent.common.constant.StateKey.AGENT_ID;
import static com.liang.data.agent.common.constant.StateKey.INPUT_KEY;
import static com.liang.data.agent.common.constant.StateKey.THREAD_ID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryCandidateNode implements NodeAction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final KnowledgeCandidateMapper knowledgeCandidateMapper;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String clarificationEvidence = StateUtil.getStringValue(state, CLARIFICATION_EVIDENCE, "");
        if (!StringUtils.hasText(clarificationEvidence)) {
            return Map.of();
        }

        ClarificationNormalizedDTO normalized = StateUtil.getObjectValue(
                state,
                CLARIFICATION_NORMALIZED_OUTPUT,
                ClarificationNormalizedDTO.class,
                new ClarificationNormalizedDTO()
        );
        String agentId = StateUtil.getStringValue(state, AGENT_ID, "");
        String threadId = StateUtil.getStringValue(state, THREAD_ID, "");
        String sourceQuestion = StateUtil.getStringValue(state, INPUT_KEY, "");
        Map<String, Object> feedbackData = StateUtil.getMapValue(state, CLARIFICATION_FEEDBACK_DATA);

        MemoryCandidateOutputDTO output = new MemoryCandidateOutputDTO();
        output.setTitle(buildTitle(normalized));
        output.setCandidateType(KnowledgeCandidateType.BUSINESS_KNOWLEDGE.getCode());
        output.setScope(KnowledgeCandidateScope.AGENT.getCode());
        output.setConfidenceScore(normalized.getConfidence() == null ? new BigDecimal("0.80") : normalized.getConfidence());
        output.setSaveRequired(true);
        output.setNormalizedContent(toNormalizedContent(normalized, clarificationEvidence));

        KnowledgeCandidateEntity entity = new KnowledgeCandidateEntity();
        entity.setAgentId(Integer.valueOf(agentId));
        entity.setThreadId(threadId);
        entity.setSessionId(threadId);
        entity.setSourceQuestion(sourceQuestion);
        entity.setClarificationQuestion(normalized.getConfirmationText());
        entity.setUserAnswer(String.valueOf(feedbackData.getOrDefault("answer", "")));
        entity.setNormalizedContent(output.getNormalizedContent());
        entity.setCandidateType(output.getCandidateType());
        entity.setTitle(output.getTitle());
        entity.setScope(output.getScope());
        entity.setStatus(KnowledgeCandidateStatus.DRAFT.getCode());
        entity.setConfidenceScore(output.getConfidenceScore());
        entity.setDelFlag(0);
        knowledgeCandidateMapper.insert(entity);
        Long candidateId = entity.getId();
        output.setCandidateId(candidateId);

        String event = WorkflowEventUtil.encode(agentId, threadId, SseEventKey.MEMORY_CANDIDATE, output);
        Flux<ChatResponse> sourceFlux = Flux.just(
                ChatResponseUtil.createPureResponse(event),
                ChatResponseUtil.createResponse("我从您的澄清中识别到一条可复用业务知识，是否保存为候选知识？")
        );
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(),
                state,
                ignored -> Map.of(
                        MEMORY_CANDIDATE_OUTPUT, output,
                        MEMORY_CANDIDATE_ID, candidateId,
                        MEMORY_SAVE_REQUIRED, true
                ),
                sourceFlux
        );

        log.info("生成候选知识: candidateId={}, title={}", candidateId, output.getTitle());
        return Map.of(MEMORY_CANDIDATE_OUTPUT, generator);
    }

    private String buildTitle(ClarificationNormalizedDTO normalized) {
        if (StringUtils.hasText(normalized.getTerm())) {
            return normalized.getTerm() + "业务口径";
        }
        return "用户澄清业务口径";
    }

    private String toNormalizedContent(ClarificationNormalizedDTO normalized, String clarificationEvidence) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("businessTerm", StringUtils.hasText(normalized.getTerm()) ? normalized.getTerm() : "业务口径");
        content.put("description", StringUtils.hasText(normalized.getNormalizedDefinition())
                ? normalized.getNormalizedDefinition()
                : clarificationEvidence);
        content.put("calculationRule", normalized.getCalculationRule());
        content.put("synonyms", normalized.getSynonyms());
        content.put("isRecall", true);
        try {
            return OBJECT_MAPPER.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            return "{\"businessTerm\":\"业务口径\",\"description\":\"" + clarificationEvidence.replace("\"", "'") + "\",\"isRecall\":true}";
        }
    }
}
