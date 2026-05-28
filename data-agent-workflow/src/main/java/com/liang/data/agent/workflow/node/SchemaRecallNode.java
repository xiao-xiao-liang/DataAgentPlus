package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.schema.SchemaService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.dal.mapper.AgentDatasourceMapper;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.*;

import static com.liang.data.agent.common.constant.NodeOutputKey.*;
import static com.liang.data.agent.common.constant.StateKey.AGENT_ID;

/**
 * Schema 召回节点
 *
 * <p>根据增强重写后的 canonicalQuery 以及扩充的 expandedQueries，
 * 从向量存储中执行多路匹配与语义联合检索，过滤并去重表文档与列文档，合并组装成可用的物理 Schema 候选集。</p>
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
        log.info("Schema 召回 - 规范化查询: {}, agentId: {}", input, agentId);

        // 1. 查询该 Agent 激活的数据源
        Integer datasourceId = agentDatasourceMapper.getActiveDatasource(Integer.valueOf(agentId));

        if (Objects.isNull(datasourceId)) {
            log.warn("Agent {} 没有关联激活的数据源", agentId);
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

        // 2. 语义向量检索表文档 (多路混合召回 + 唯一性去重)
        List<Document> tableDocuments = new ArrayList<>();
        Set<String> docIds = new HashSet<>();

        // 2.1 主查询检索
        try {
            List<Document> mainDocs = schemaService.recallTableDocuments(datasourceId, input);
            if (mainDocs != null) {
                for (Document doc : mainDocs) {
                    if (doc != null && doc.getId() != null && docIds.add(doc.getId())) {
                        tableDocuments.add(doc);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("语义召回主规范化问题失败，input: {}", input, e);
        }

        // 2.2 扩展查询并行检索
        List<String> expandedQueries = queryEnhanceOutput.getExpandedQueries();
        if (expandedQueries != null && !expandedQueries.isEmpty()) {
            log.info("提取到扩展查询 {} 个，开启多路联合召回", expandedQueries.size());
            for (String query : expandedQueries) {
                if (StringUtils.hasText(query)) {
                    try {
                        List<Document> extDocs = schemaService.recallTableDocuments(datasourceId, query);
                        if (extDocs != null) {
                            for (Document doc : extDocs) {
                                if (doc != null && doc.getId() != null && docIds.add(doc.getId())) {
                                    tableDocuments.add(doc);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("语义召回扩展问题 [{}] 失败", query, e);
                    }
                }
            }
        }

        // 3. 从表文档提取表名, 按表名精确检索列文档
        List<String> recalledTableNames = extractTableNames(tableDocuments);
        List<Document> columnDocuments = schemaService.getColumnDocumentsByTableNames(datasourceId, recalledTableNames);

        // 4. 构建展示消息
        String message;
        if (tableDocuments.isEmpty()) {
            message = """
                    \n 未检索到相关数据表
                    
                    这可能是因为：
                    1. 数据源尚未初始化或嵌入模型已更换。
                    2. 您的提问与当前数据库中的表结构无关。
                    3. 请尝试点击"初始化数据源"或换一个与业务相关的问题。
                    流程已终止。
                    """;
        } else {
            message = "多路 Schema 信息召回完成，共召回 " + tableDocuments.size()
                    + " 张表，表名: " + String.join(", ", recalledTableNames);
        }

        Flux<ChatResponse> displayFlux = Flux.just(
                ChatResponseUtil.createResponse("开始多路 Schema 信息召回..."),
                ChatResponseUtil.createResponse(message),
                ChatResponseUtil.createResponse("Schema 候选集召回成功。"));

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
        log.info("最终有效召回的表: {}", tableNames);
        return tableNames;
    }
}
