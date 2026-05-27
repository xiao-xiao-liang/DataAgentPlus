package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.common.enums.TextType;
import com.liang.data.agent.common.schema.SchemaDTO;
import com.liang.data.agent.workflow.dto.SqlRetryDTO;
import com.liang.data.agent.workflow.dto.node.SqlGenerationDTO;
import com.liang.data.agent.workflow.service.Nl2SqlService;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.MarkdownParserUtil;
import com.liang.data.agent.workflow.util.PlanProcessUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.*;
import static com.liang.data.agent.common.constant.NodeOutputKey.*;

/**
 * SQL 生成节点
 *
 * <p>职责: 根据 Schema + 用户查询生成 SQL。支持首次生成和错误修复两种模式。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlGenerateNode implements NodeAction {

    private final Nl2SqlService nl2SqlService;
    private final DataAgentProperties properties;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // ======================== 阶段一：限额防御 - 拦截无限重试 ========================
        int currentCount = state.value(SQL_GENERATE_COUNT, 0);
        if (currentCount >= properties.getMaxSqlRetryCount()) {
            String errMsg = String.format("\n 步骤 SQL 生成次数已达到最大限制 (%d)，重试被强行拦截，流程终止。",
                    properties.getMaxSqlRetryCount());
            log.error(errMsg);

            Flux<ChatResponse> displayFlux = Flux.just(
                    ChatResponseUtil.createResponse(errMsg)
            );

            Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                    this.getClass(), state,
                    currentState -> Map.of(
                            SQL_GENERATE_OUTPUT, StateGraph.END,
                            SQL_GENERATE_COUNT, 0),
                    displayFlux
            );
            return Map.of(SQL_GENERATE_OUTPUT, generator);
        }

        // ======================== 阶段二：提取参数与构建 DTO ========================
        String canonicalQuery = StateUtil.getCanonicalQuery(state);
        String evidence = StateUtil.getStringValue(state, EVIDENCE_OUTPUT, "");
        SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
        String dialect = StateUtil.getStringValue(state, DB_DIALECT_TYPE, "MySQL");
        String semanticModel = StateUtil.getStringValue(state, GENERATED_SEMANTIC_MODEL_PROMPT, "");

        // 获取当前步骤的执行描述 (Plan 模式下)
        String executionDescription;
        try {
            executionDescription = PlanProcessUtil.getExecutingStepInstruction(state);
        } catch (Exception e) {
            executionDescription = canonicalQuery;
        }

        // 判断是首次生成还是重试修复
        SqlRetryDTO retryDto = StateUtil.getObjectValue(state, SQL_REGENERATE_REASON,
                SqlRetryDTO.class, SqlRetryDTO.empty());

        SqlGenerationDTO dto = SqlGenerationDTO.builder()
                .evidence(evidence + "\n" + semanticModel)
                .query(canonicalQuery)
                .schemaInfo(schemaDTO)
                .executionDescription(executionDescription)
                .dialect(dialect)
                .build();

        Flux<ChatResponse> responseFlux;
        String statusMessage;

        // ======================== 阶段三：调用 SQL 生成或修复服务 ========================
        if (retryDto.semanticFail() || retryDto.sqlExecuteFail()) {
            // 重试模式: 修复 SQL
            dto.setSql(StateUtil.getStringValue(state, SQL_GENERATE_OUTPUT, ""));
            dto.setExceptionMessage(retryDto.reason());
            responseFlux = nl2SqlService.fixSql(dto);
            statusMessage = String.format("正在修复SQL (第%d次重试)...", currentCount + 1);
            log.info("SQL 修复模式 - 原因: {}", retryDto.reason());
        } else {
            // 首次生成模式
            responseFlux = nl2SqlService.generateSql(dto);
            statusMessage = "正在生成SQL...";
            log.info("SQL 首次生成模式");
        }

        // ======================== 阶段四：组装 Generator 并返回流 ========================
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(SQL_GENERATE_COUNT, currentCount + 1);

        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGenerator(
                this.getClass(), state, responseFlux,
                Flux.just(ChatResponseUtil.createResponse(statusMessage),
                        ChatResponseUtil.createPureResponse(TextType.SQL.getStartSign())),
                Flux.just(ChatResponseUtil.createPureResponse(TextType.SQL.getEndSign()),
                        ChatResponseUtil.createResponse("\nSQL生成完成！")),
                sqlOutput -> {
                    String sql = MarkdownParserUtil.extractRawText(sqlOutput.trim());
                    log.info("生成的最终SQL: {}", sql);
                    resultMap.put(SQL_GENERATE_OUTPUT, sql);
                    resultMap.put(SQL_REGENERATE_REASON, SqlRetryDTO.empty());
                    return resultMap;
                });

        return Map.of(SQL_GENERATE_OUTPUT, generator);
    }
}
