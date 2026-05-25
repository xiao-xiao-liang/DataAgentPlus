package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liang.data.agent.ai.schema.SchemaService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.dal.entity.AgentDatasourceEntity;
import com.liang.data.agent.dal.mapper.AgentDatasourceMapper;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.liang.data.agent.common.constant.NodeOutputKey.*;
import static com.liang.data.agent.common.constant.StateKey.AGENT_ID;

/**
 * Schema 召回节点
 *
 * <p>根据 canonicalQuery 从向量库检索相关的表文档和列文档</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaRecallNode implements NodeAction {

    private final SchemaService schemaService;
    private final AgentDatasourceMapper agentDatasourceMapper;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        var queryEnhanceOutput = StateUtil.getObjectValue(state, QUERY_ENHANCE_NODE_OUTPUT, QueryEnhanceOutputDTO.class);
        String input = queryEnhanceOutput.getCanonicalQuery();
        String agentId = StateUtil.getStringValue(state, AGENT_ID);
        log.info("Schema 召回 - 查询: {}, agentId: {}", input, agentId);

        // 1. 查询该 Agent 激活的数据源
        Integer datasourceId = agentDatasourceMapper.getActiveDatasource(Integer.valueOf(agentId));

        if (Objects.isNull(datasourceId)) {
            log.warn("Agent {} has no active datasource", agentId);
            String noDataSourceMessage = """
                    \n 该智能体没有激活的数据源
                    
                    这可能是因为：
                    1. 数据源尚未配置或关联。
                    2. 所有数据源都已被禁用。
                    3. 请先在管理后台配置并激活数据源。
                    流程已终止。
                    """;

            Flux<ChatResponse> displayFlux = Flux.just(
                    ChatResponseUtil.createResponse(noDataSourceMessage)
            );

            Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                    this.getClass(), state,
                    currentState -> Map.of(
                            TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, List.of(),
                            COLUMN_DOCUMENTS_FOR_SCHEMA_OUTPUT, List.of()),
                    displayFlux);

            return Map.of(SCHEMA_RECALL_NODE_OUTPUT, generator);
        }

        log.info("查询到激活数据源 ID: {}", datasourceId);

        // 2. 语义向量检索表文档
        List<Document> tableDocuments = schemaService.recallTableDocuments(datasourceId, input);

        // 3. 从表文档提取表名, 按表名精确检索列文档
        List<String> recalledTableNames = extractTableNames(tableDocuments);
        List<Document> columnDocuments = schemaService.getColumnDocumentsByTableNames(datasourceId, recalledTableNames);

        // 构建展示消息
        String message;
        if (tableDocuments.isEmpty()) {
            message = """
                    \n 未检索到相关数据表
                    
                    这可能是因为：
                    1. 数据源尚未初始化。
                    2. 您的提问与当前数据库中的表结构无关。
                    3. 请尝试点击"初始化数据源"或换一个与业务相关的问题。
                    流程已终止。
                    """;
        } else {
            message = "初步表信息召回完成，数量: " + tableDocuments.size()
                    + "，表名: " + String.join(", ", recalledTableNames);
        }

        Flux<ChatResponse> displayFlux = Flux.just(
                ChatResponseUtil.createResponse("开始初步召回Schema信息..."),
                ChatResponseUtil.createResponse(message),
                ChatResponseUtil.createResponse("初步Schema信息召回完成."));

        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(), state,
                currentState -> Map.of(
                        TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, tableDocuments,
                        COLUMN_DOCUMENTS_FOR_SCHEMA_OUTPUT, columnDocuments),
                displayFlux);

        return Map.of(SCHEMA_RECALL_NODE_OUTPUT, generator);
    }

    /**
     * 从表文档元数据中提取表名列表
     */
    private List<String> extractTableNames(List<Document> tableDocuments) {
        List<String> tableNames = new ArrayList<>();
        for (Document document : tableDocuments) {
            Object nameObj = document.getMetadata().get("name");
            if (nameObj != null) {
                String name = nameObj.toString();
                if (!name.isBlank()) {
                    tableNames.add(name);
                }
            }
        }
        log.info("召回表名: {}", tableNames);
        return tableNames;
    }
}
