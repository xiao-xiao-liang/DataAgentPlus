package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.common.schema.SchemaDTO;
import com.liang.data.agent.common.constant.StateKey;
import com.liang.data.agent.workflow.dto.SqlRetryDTO;
import com.liang.data.agent.workflow.dto.node.SemanticConsistencyDTO;
import com.liang.data.agent.workflow.prompt.PromptHelper;
import com.liang.data.agent.workflow.service.Nl2SqlService;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.DB_DIALECT_TYPE;
import static com.liang.data.agent.common.constant.ControlFlowKey.SQL_REGENERATE_REASON;
import static com.liang.data.agent.common.constant.NodeOutputKey.*;
import static com.liang.data.agent.workflow.util.PlanProcessUtil.getExecutingStepInstruction;

/**
 * 语义一致性检查节点
 *
 * <p>LLM 检查生成的 SQL 是否与用户意图和步骤描述一致</p>
 * <p>输出: SEMANTIC_CONSISTENCY_NODE_OUTPUT → Boolean (true=通过)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticConsistencyNode implements NodeAction {

    private final Nl2SqlService nl2SqlService;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String sql = StateUtil.getStringValue(state, SQL_GENERATE_OUTPUT);
        String canonicalQuery = StateUtil.getCanonicalQuery(state);
        String evidence = StateUtil.getStringValue(state, EVIDENCE_OUTPUT);
        String dialect = StateUtil.getStringValue(state, DB_DIALECT_TYPE, "MySQL");

        SchemaDTO schemaDTO = StateUtil.getObjectValueOrNull(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
        String schemaInfo = "";
        if (schemaDTO != null && schemaDTO.getTables() != null) {
            schemaInfo = PromptHelper.buildMixMacSqlDbPrompt(schemaDTO, true);
        }

        SemanticConsistencyDTO dto = SemanticConsistencyDTO.builder()
                .sql(sql)
                .userQuery(canonicalQuery)
                .evidence(evidence)
                .dialect(dialect)
                .schemaInfo(schemaInfo)
                .executionDescription(getExecutingStepInstruction(state))
                .build();

        Flux<ChatResponse> responseFlux = nl2SqlService.checkSemanticConsistency(dto);

        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(), state, "正在进行语义一致性检查...", "语义一致性检查完成！",
                llmOutput -> {
                    String result = llmOutput.trim().toLowerCase();
                    boolean passed = result.contains("true") || result.contains("通过") || result.contains("一致");
                    log.info("语义一致性检查: {} → {}", result, passed ? "通过" : "不通过");

                    if (!passed) {
                        return Map.of(
                                SEMANTIC_CONSISTENCY_NODE_OUTPUT, false,
                                SQL_REGENERATE_REASON, SqlRetryDTO.semantic("语义不一致: " + result));
                    }
                    return Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, true);
                }, responseFlux);

        return Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, generator);
    }
}
