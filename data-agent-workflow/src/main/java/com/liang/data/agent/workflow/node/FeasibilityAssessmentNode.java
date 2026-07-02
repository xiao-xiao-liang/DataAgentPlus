package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.common.enums.FeasibilityRequestType;
import com.liang.data.agent.common.schema.SchemaDTO;
import com.liang.data.agent.gateway.constants.ModelGatewayConstant;
import com.liang.data.agent.workflow.dto.node.FeasibilityAssessmentOutputDTO;
import com.liang.data.agent.workflow.prompt.PromptHelper;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.JsonParseUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.liang.data.agent.common.constant.NodeOutputKey.EVIDENCE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.FEASIBILITY_ASSESSMENT_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.TABLE_RELATION_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.MULTI_TURN_CONTEXT;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeasibilityAssessmentNode implements NodeAction {

    private final LlmService llmService;

    private final JsonParseUtil jsonParseUtil;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String canonicalQuery = StateUtil.getCanonicalQuery(state);
        SchemaDTO recalledSchema = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
        String evidence = StateUtil.getStringValue(state, EVIDENCE_OUTPUT);
        String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

        String prompt = PromptHelper.buildFeasibilityAssessmentPrompt(canonicalQuery, recalledSchema, evidence, multiTurn);
        log.debug("可行性评估 Prompt:\n{}", prompt);

        Flux<ChatResponse> responseFlux = llmService.callUser(ModelGatewayConstant.FEASIBILITY_ASSESSMENT, prompt);
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(),
                state,
                "正在进行可行性评估...",
                "可行性评估完成！",
                llmOutput -> {
                    FeasibilityAssessmentOutputDTO output = parseOutput(llmOutput.trim());
                    log.info("可行性评估结果: {}", output);
                    return Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, output);
                },
                responseFlux
        );

        return Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, generator);
    }

    private FeasibilityAssessmentOutputDTO parseOutput(String result) {
        try {
            return jsonParseUtil.tryConvertToObject(result, FeasibilityAssessmentOutputDTO.class);
        } catch (Exception e) {
            log.warn("结构化解析可行性评估失败，回退到文本判断: {}", e.getMessage());
            FeasibilityAssessmentOutputDTO output = new FeasibilityAssessmentOutputDTO();
            if (result.contains("需要澄清") || result.contains("NEED_CLARIFICATION")) {
                output.setRequestType(FeasibilityRequestType.NEED_CLARIFICATION.getCode());
                output.setClarificationQuestion(result);
                output.setReason("模型输出需要澄清，但未形成 JSON");
                output.setSuggestedMemoryType("BUSINESS_KNOWLEDGE");
                output.setMemoryWorthSaving(true);
                output.setAffectsSchemaRecall(false);
            } else if (result.contains("数据分析") || result.contains("DATA_ANALYSIS")) {
                output.setRequestType(FeasibilityRequestType.DATA_ANALYSIS.getCode());
                output.setAnalysisGoal(result);
            } else {
                output.setRequestType(FeasibilityRequestType.CHAT.getCode());
                output.setReason(result);
            }
            return output;
        }
    }
}
