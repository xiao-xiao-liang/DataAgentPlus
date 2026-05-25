package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.common.constant.StateKey;
import com.liang.data.agent.common.schema.SchemaDTO;
import com.liang.data.agent.workflow.prompt.PromptHelper;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.liang.data.agent.common.constant.NodeOutputKey.*;
import static com.liang.data.agent.common.constant.StateKey.MULTI_TURN_CONTEXT;

/**
 * 可行性评估节点
 *
 * <p>调用 LLM 评估用户需求是否为数据分析需求</p>
 * <p>输出格式: "【需求类型】：《数据分析》" 或 "【需求类型】：《其他》"</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeasibilityAssessmentNode implements NodeAction {

    private final LlmService llmService;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String canonicalQuery = StateUtil.getCanonicalQuery(state);
        SchemaDTO recalledSchema = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
        String evidence = StateUtil.getStringValue(state, EVIDENCE_OUTPUT);
        String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

        // 构建可行性评估提示词
        String prompt = PromptHelper.buildFeasibilityAssessmentPrompt(canonicalQuery, recalledSchema, evidence, multiTurn);
        log.debug("可行性评估 Prompt:\n{}", prompt);

        Flux<ChatResponse> responseFlux = llmService.callUser(prompt);

        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(), state, "正在进行可行性评估...", "可行性评估完成！",
                llmOutput -> {
                    String result = llmOutput.trim();
                    log.info("可行性评估结果: {}", result);
                    return Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, result);
                }, responseFlux);

        return Map.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, generator);
    }
}
