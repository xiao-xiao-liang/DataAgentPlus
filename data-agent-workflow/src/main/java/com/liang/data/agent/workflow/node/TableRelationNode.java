package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liang.data.agent.ai.schema.SchemaService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.schema.SchemaDTO;
import com.liang.data.agent.common.schema.TableDTO;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import com.liang.data.agent.dal.entity.AgentDatasourceEntity;
import com.liang.data.agent.dal.entity.DatasourceEntity;
import com.liang.data.agent.dal.entity.LogicalRelationEntity;
import com.liang.data.agent.dal.entity.SemanticModelEntity;
import com.liang.data.agent.dal.mapper.AgentDatasourceMapper;
import com.liang.data.agent.dal.mapper.DatasourceMapper;
import com.liang.data.agent.dal.mapper.LogicalRelationMapper;
import com.liang.data.agent.dal.mapper.SemanticModelMapper;
import com.liang.data.agent.workflow.prompt.PromptHelper;
import com.liang.data.agent.workflow.service.Nl2SqlService;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

import static com.liang.data.agent.common.constant.ControlFlowKey.*;
import static com.liang.data.agent.common.constant.NodeOutputKey.*;
import static com.liang.data.agent.common.constant.StateKey.AGENT_ID;

/**
 * 表关系推理节点
 *
 * <p>核心职责：从 Schema 文档构建 SchemaDTO，查询并融合逻辑外键，调用 LLM 完成表/列精细化筛选，查询并渲染业务语义提示词</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TableRelationNode implements NodeAction {

    private final SchemaService schemaService;
    private final Nl2SqlService nl2SqlService;
    private final DatasourceMapper datasourceMapper;
    private final SemanticModelMapper semanticModelMapper;
    private final LogicalRelationMapper logicalRelationMapper;
    private final AgentDatasourceMapper agentDatasourceMapper;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // ======================== 阶段一：提取输入参数 ========================
        String canonicalQuery = StateUtil.getCanonicalQuery(state);
        String evidence = StateUtil.getStringValue(state, EVIDENCE_OUTPUT, "");
        List<Document> tableDocs = StateUtil.getDocumentList(state, TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT);
        List<Document> columnDocs = StateUtil.getDocumentList(state, COLUMN_DOCUMENTS_FOR_SCHEMA_OUTPUT);
        String agentId = StateUtil.getStringValue(state, AGENT_ID);

        log.info("表关系推理 - 开始，智能体 ID: {}, 表文档: {}, 列文档: {}", agentId, tableDocs.size(), columnDocs.size());

        // ======================== 阶段二：获取激活数据源及配置 ========================
        // 1. 查询激活的数据源关联关系
        Integer datasourceId = agentDatasourceMapper.getActiveDatasource(Integer.valueOf(agentId));

        if (Objects.isNull(datasourceId)) {
            log.warn("智能体 {} 未配置或激活任何数据源", agentId);
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
                            TABLE_RELATION_OUTPUT, new SchemaDTO(),
                            DB_DIALECT_TYPE, ""),
                    displayFlux);

            return Map.of(
                    TABLE_RELATION_OUTPUT, generator,
                    DB_DIALECT_TYPE, "",
                    TABLE_RELATION_RETRY_COUNT, 0,
                    TABLE_RELATION_EXCEPTION_OUTPUT, ""
            );
        }

        // 2. 查询数据源连接详情
        DatasourceEntity datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            log.warn("数据源实体未找到，ID: {}", datasourceId);
            String dbErrorMessage = "\n关联数据源配置缺失，数据源 ID: " + datasourceId + "\n流程已终止。";
            Flux<ChatResponse> displayFlux = Flux.just(ChatResponseUtil.createResponse(dbErrorMessage));
            Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                    this.getClass(), state,
                    currentState -> Map.of(),
                    displayFlux);
            return Map.of(
                    TABLE_RELATION_OUTPUT, generator,
                    DB_DIALECT_TYPE, "",
                    TABLE_RELATION_RETRY_COUNT, 0,
                    TABLE_RELATION_EXCEPTION_OUTPUT, ""
            );
        }

        DbConfigBO agentDbConfig = DbConfigBO.from(datasource);

        // ======================== 阶段三：构建初始 Schema 并融合逻辑外键 ========================
        SchemaDTO initialSchema = schemaService.buildSchemaFromDocuments(tableDocs, columnDocs);
        initialSchema.setName(agentDbConfig.schema());

        // 查询并追加入逻辑外键
        List<String> logicalForeignKeys = getLogicalForeignKeys(datasourceId, tableDocs);
        if (!logicalForeignKeys.isEmpty()) {
            List<String> existingFK = initialSchema.getForeignKeys();
            if (CollectionUtils.isEmpty(existingFK)) {
                initialSchema.setForeignKeys(logicalForeignKeys);
            } else {
                List<String> mergedFK = new ArrayList<>(existingFK);
                mergedFK.addAll(logicalForeignKeys);
                initialSchema.setForeignKeys(mergedFK);
            }
            log.info("合并了 {} 条逻辑外键关系", logicalForeignKeys.size());
        }

        // ======================== 阶段四：LLM 精细选择与语义模型注入 ========================
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(DB_DIALECT_TYPE, agentDbConfig.type());
        resultMap.put(TABLE_RELATION_RETRY_COUNT, 0);
        resultMap.put(TABLE_RELATION_EXCEPTION_OUTPUT, "");
        resultMap.put(GENERATED_SEMANTIC_MODEL_PROMPT, "");

        // 检查 SQL 生成阶段是否因为 Schema 缺失产生建议（重试流）
        String schemaAdvice = StateUtil.getStringValue(state, SQL_GENERATE_SCHEMA_MISSING_ADVICE, null);

        Flux<ChatResponse> schemaFlux = nl2SqlService.fineSelect(
                initialSchema, canonicalQuery, evidence, schemaAdvice, agentDbConfig, result -> {
                    log.info("Schema 精选完成，筛选后表个数: {}", result.getTables() != null ? result.getTables().size() : 0);
                    resultMap.put(TABLE_RELATION_OUTPUT, result);

                    if (!CollectionUtils.isEmpty(result.getTables())) {
                        List<String> tableNames = result.getTables().stream().map(TableDTO::getName).toList();
                        // 3. 根据最终保留的表，查询用户配置的语义字段描述模型
                        List<SemanticModelEntity> semanticModels = semanticModelMapper.selectList(
                                new LambdaQueryWrapper<SemanticModelEntity>()
                                        .eq(SemanticModelEntity::getAgentId, Integer.valueOf(agentId))
                                        .in(SemanticModelEntity::getTableName, tableNames)
                                        .eq(SemanticModelEntity::getStatus, 1)
                        );
                        String semanticPrompt = PromptHelper.buildSemanticModelPrompt(semanticModels);
                        resultMap.put(GENERATED_SEMANTIC_MODEL_PROMPT, semanticPrompt);
                        log.info("查询到关联的语义模型字段映射数量: {}", semanticModels.size());
                    }
                }
        );

        // ======================== 阶段五：流式展示与 Generator 封装 ========================
        Flux<ChatResponse> preFlux = Flux.just(
                ChatResponseUtil.createResponse("开始构建初始Schema..."),
                ChatResponseUtil.createResponse("初始Schema构建完成.")
        );

        Flux<ChatResponse> postFlux = Flux.just(
                ChatResponseUtil.createResponse("\n开始处理Schema选择..."),
                ChatResponseUtil.createResponse("Schema选择处理完成。")
        );

        Flux<ChatResponse> displayFlux = preFlux.concatWith(schemaFlux).concatWith(postFlux);

        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(), state, v -> resultMap, displayFlux
        );

        return Map.of(
                TABLE_RELATION_OUTPUT, generator,
                DB_DIALECT_TYPE, agentDbConfig.type(),
                TABLE_RELATION_RETRY_COUNT, 0,
                TABLE_RELATION_EXCEPTION_OUTPUT, ""
        );
    }

    /**
     * 获取与当前召回表相关的逻辑外键关系列表
     */
    private List<String> getLogicalForeignKeys(Integer datasourceId, List<Document> tableDocuments) {
        try {
            Set<String> recalledTableNames = tableDocuments.stream()
                    .map(doc -> {
                        Object nameObj = doc.getMetadata().get("name");
                        return nameObj != null ? nameObj.toString() : null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (recalledTableNames.isEmpty()) {
                return Collections.emptyList();
            }

            // 查询全部逻辑外键
            List<LogicalRelationEntity> allRelations = logicalRelationMapper.selectList(
                    new LambdaQueryWrapper<LogicalRelationEntity>()
                            .eq(LogicalRelationEntity::getDatasourceId, datasourceId)
            );

            // 过滤：源表或目标表在已召回表集合中时保留，格式化为 "A.col1=B.col2"
            List<String> formattedKeys = allRelations.stream()
                    .filter(lr -> recalledTableNames.contains(lr.getSourceTableName())
                            || recalledTableNames.contains(lr.getTargetTableName()))
                    .map(lr -> String.format("%s.%s=%s.%s",
                            lr.getSourceTableName(), lr.getSourceColumnName(),
                            lr.getTargetTableName(), lr.getTargetColumnName()))
                    .distinct()
                    .collect(Collectors.toList());

            log.info("数据源 {} 匹配到 {} 条相关的逻辑外键", datasourceId, formattedKeys.size());
            return formattedKeys;
        } catch (Exception e) {
            log.error("获取逻辑外键异常，将降级忽略, datasourceId: {}", datasourceId, e);
            return Collections.emptyList();
        }
    }
}
