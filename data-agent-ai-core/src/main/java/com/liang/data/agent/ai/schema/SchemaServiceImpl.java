package com.liang.data.agent.ai.schema;

import com.liang.data.agent.ai.vectorstore.AgentVectorStoreService;
import com.liang.data.agent.common.schema.ColumnDTO;
import com.liang.data.agent.common.schema.SchemaDTO;
import com.liang.data.agent.common.schema.TableDTO;
import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.common.enums.VectorType;
import com.liang.data.agent.dal.connector.DatabaseAccessor;
import com.liang.data.agent.dal.connector.bo.ColumnInfoBO;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import com.liang.data.agent.dal.connector.bo.ForeignKeyInfoBO;
import com.liang.data.agent.dal.connector.bo.TableInfoBO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.liang.data.agent.common.constant.VectorMetadataKey.*;

/**
 * Schema 服务实现
 *
 * <p>检索: 通过 AgentVectorStoreService 从向量库查询表/列文档</p>
 * <p>初始化: 通过 DatabaseAccessor 连接数据库拉取元数据, 转成 Document 后写入向量库</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaServiceImpl implements SchemaService {

    private final VectorStore vectorStore;
    private final DataAgentProperties properties;
    private final DatabaseAccessor databaseAccessor;
    private final AgentVectorStoreService vectorStoreService;

    // ==================== 检索 ====================

    @Override
    public List<Document> recallTableDocuments(Integer datasourceId, String query) {
        if (datasourceId == null) {
            throw new IllegalArgumentException("datasourceId 不能为空");
        }
        var vs = properties.getVectorStore();
        String filterExpr = String.format("%s == '%s' && %s == '%s'",
                DATASOURCE_ID, datasourceId,
                VECTOR_TYPE, VectorType.TABLE.getCode());

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(Math.max(vs.getDefaultTopkLimit(), 15))
                .similarityThreshold(vs.getDefaultSimilarityThreshold())
                .filterExpression(filterExpr)
                .build();
        return vectorStore.similaritySearch(searchRequest);
    }

    @Override
    public List<Document> getColumnDocumentsByTableNames(Integer datasourceId, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyList();
        }

        String filterExpr = buildColumnFilterExpression(datasourceId, tableNames);
        int topK = tableNames.size() * 100;

        SearchRequest request = SearchRequest.builder()
                .query(DEFAULT_QUERY)
                .topK(topK)
                .similarityThreshold(0.0)
                .filterExpression(filterExpr)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);
        log.debug("列文档检索: datasourceId={}, 表名={}, 命中 {} 条", datasourceId, tableNames, results.size());
        return results;
    }

    @Override
    public SchemaDTO buildSchemaFromDocuments(List<Document> tableDocs, List<Document> columnDocs) {
        // 1. 解析表文档 → TableDTO 列表 + 收集外键
        Set<String> foreignKeySet = new HashSet<>();
        List<TableDTO> tables = tableDocs.stream()
                .map(doc -> parseTableDocument(doc, foreignKeySet))
                .toList();

        // 2. 将列文档匹配到对应的表中
        Map<String, TableDTO> tableMap = tables.stream()
                .collect(Collectors.toMap(TableDTO::getName, t -> t, (a, b) -> a));
        columnDocs.forEach(doc -> matchColumnToTable(doc, tableMap));

        // 3. 组装 SchemaDTO
        SchemaDTO schema = new SchemaDTO();
        schema.setTables(new ArrayList<>(tables));
        schema.setForeignKeys(new ArrayList<>(foreignKeySet));
        schema.setTableCount(tables.size());
        return schema;
    }

    // ==================== 初始化 ====================

    @Override
    public void initializeSchema(Integer agentId, Integer datasourceId, DbConfigBO dbConfig, List<String> tables) {
        log.info("开始初始化 Schema: agentId={}, datasourceId={}, tables={}", agentId, datasourceId, tables);

        // 1. 清理旧文档
        clearSchemaDocuments(datasourceId);

        // 2. 拉取外键关系
        Map<String, List<String>> foreignKeyMap = fetchForeignKeyMap(dbConfig, tables);

        // 3. 对每张表构造 Document
        List<Document> tableDocs = new ArrayList<>();
        List<Document> columnDocs = new ArrayList<>();
        for (String tableName : tables) {
            buildDocumentsForTable(agentId, datasourceId, dbConfig, tableName, foreignKeyMap, tableDocs, columnDocs);
        }

        // 4. 写入向量库
        storeDocuments(agentId, tableDocs, columnDocs);
        log.info("Schema 初始化完成: agentId={}, datasourceId={}, 表={}, 列={}", agentId, datasourceId, tableDocs.size(), columnDocs.size());
    }

    // ==================== 检索 - 私有方法 ====================

    /**
     * 构建列文档的过滤表达式
     * <p>格式: datasource_id == 'xxx' && vector_type == 'COLUMN' && (tableName == 't_user' || tableName == 't_order')</p>
     */
    private String buildColumnFilterExpression(Integer datasourceId, List<String> tableNames) {
        String tableNameFilter = tableNames.stream()
                .map(name -> TABLE_NAME + " == '" + name + "'")
                .collect(Collectors.joining(" || "));

        return String.format("%s == '%s' && %s == '%s' && (%s)",
                DATASOURCE_ID, datasourceId.toString(),
                VECTOR_TYPE, VectorType.COLUMN.getCode(),
                tableNameFilter);
    }

    /**
     * 从表文档解析出 TableDTO, 同时收集外键信息
     */
    private TableDTO parseTableDocument(Document tableDoc, Set<String> foreignKeySet) {
        Map<String, Object> meta = tableDoc.getMetadata();

        TableDTO table = new TableDTO();
        table.setName((String) meta.getOrDefault(NAME, ""));
        table.setDescription((String) meta.getOrDefault(DESCRIPTION, ""));
        table.setColumn(new ArrayList<>());

        // 提取主键
        Object pkObj = meta.get(PRIMARY_KEY);
        if (pkObj instanceof String pk && StringUtils.isNotBlank(pk)) {
            table.setPrimaryKeys(List.of(pk.split(",")));
        }

        // 收集外键
        String fk = (String) meta.getOrDefault(FOREIGN_KEY, "");
        if (StringUtils.isNotBlank(fk)) {
            Arrays.stream(fk.split("、"))
                    .filter(StringUtils::isNotBlank)
                    .forEach(foreignKeySet::add);
        }

        return table;
    }

    /**
     * 将列文档匹配到对应的 TableDTO 中
     */
    private void matchColumnToTable(Document colDoc, Map<String, TableDTO> tableMap) {
        Map<String, Object> meta = colDoc.getMetadata();
        String belongTable = (String) meta.get(TABLE_NAME);
        TableDTO table = tableMap.get(belongTable);
        if (table == null) {
            return;
        }

        ColumnDTO col = new ColumnDTO();
        col.setName((String) meta.getOrDefault(NAME, ""));
        col.setDescription((String) meta.getOrDefault(DESCRIPTION, ""));
        col.setType((String) meta.getOrDefault(TYPE, ""));
        table.getColumn().add(col);
    }

    // ==================== 初始化 - 私有方法 ====================

    /**
     * 清理指定数据源的 Schema 文档 (TABLE + COLUMN)
     */
    private void clearSchemaDocuments(Integer datasourceId) {
        vectorStoreService.deleteDocumentsByMetadata(
                Map.of(DATASOURCE_ID, datasourceId.toString(), VECTOR_TYPE, VectorType.TABLE.getCode()));
        vectorStoreService.deleteDocumentsByMetadata(
                Map.of(DATASOURCE_ID, datasourceId.toString(), VECTOR_TYPE, VectorType.COLUMN.getCode()));
        log.info("已清理 datasourceId={} 的旧 Schema 文档", datasourceId);
    }

    /**
     * 拉取外键关系并构建映射: tableName -> List<"sourceTable.sourceCol=targetTable.targetCol">
     */
    private Map<String, List<String>> fetchForeignKeyMap(DbConfigBO dbConfig, List<String> tables) {
        List<ForeignKeyInfoBO> foreignKeys = databaseAccessor.showForeignKeys(dbConfig, tables);
        log.info("外键关系: {} 条", foreignKeys.size());

        Map<String, List<String>> map = new HashMap<>();
        for (ForeignKeyInfoBO fk : foreignKeys) {
            String relation = fk.sourceTable() + "." + fk.sourceColumn() + "=" + fk.targetTable() + "." + fk.targetColumn();
            map.computeIfAbsent(fk.sourceTable(), k -> new ArrayList<>()).add(relation);
            map.computeIfAbsent(fk.targetTable(), k -> new ArrayList<>()).add(relation);
        }
        return map;
    }

    /**
     * 为单张表构建表文档和列文档
     */
    private void buildDocumentsForTable(Integer agentId, Integer datasourceId, DbConfigBO dbConfig, String tableName,
                                        Map<String, List<String>> foreignKeyMap,
                                        List<Document> tableDocs, List<Document> columnDocs) {
        // 拉取表信息
        List<TableInfoBO> tableInfos = databaseAccessor.showTables(dbConfig, tableName);
        if (tableInfos.isEmpty()) {
            log.warn("表不存在: {}", tableName);
            return;
        }

        String comment = Optional.ofNullable(tableInfos.getFirst().comment()).orElse("");
        List<ColumnInfoBO> columns = databaseAccessor.showColumns(dbConfig, tableName);

        // 构造表文档
        tableDocs.add(buildTableDocument(agentId, datasourceId, tableName, comment, columns, foreignKeyMap));

        // 构造列文档
        for (ColumnInfoBO col : columns) {
            columnDocs.add(buildColumnDocument(agentId, datasourceId, dbConfig, tableName, col));
        }
    }

    /**
     * 构造单个表的 Document
     */
    private Document buildTableDocument(Integer agentId, Integer datasourceId, String tableName, String comment,
                                        List<ColumnInfoBO> columns, Map<String, List<String>> foreignKeyMap) {
        String columnSummary = columns.stream()
                .map(col -> String.format("%s(%s): %s",
                        col.columnName(),
                        col.dataType(),
                        Optional.ofNullable(col.comment()).orElse("")))
                .collect(Collectors.joining("；"));
        String content = String.format("表名: %s, 描述: %s, 字段: %s", tableName, comment, columnSummary);

        String primaryKeys = columns.stream()
                .filter(ColumnInfoBO::primaryKey)
                .map(ColumnInfoBO::columnName)
                .collect(Collectors.joining(","));

        List<String> fkList = foreignKeyMap.getOrDefault(tableName, Collections.emptyList());

        Map<String, Object> meta = new HashMap<>();
        meta.put(AGENT_ID, agentId.toString());
        meta.put(DATASOURCE_ID, datasourceId.toString());
        meta.put(VECTOR_TYPE, VectorType.TABLE.getCode());
        meta.put(NAME, tableName);
        meta.put(DESCRIPTION, comment);
        meta.put(PRIMARY_KEY, primaryKeys);
        meta.put(FOREIGN_KEY, String.join("、", fkList));

        return new Document(content, meta);
    }

    /**
     * 构造单个列的 Document
     */
    private Document buildColumnDocument(Integer agentId, Integer datasourceId, DbConfigBO dbConfig,
                                         String tableName, ColumnInfoBO col) {
        String colComment = Optional.ofNullable(col.comment()).orElse("");
        String content = String.format("表 %s 的列 %s, 类型: %s, 描述: %s", 
                tableName, col.columnName(), col.dataType(), colComment);

        Map<String, Object> meta = new HashMap<>();
        meta.put(AGENT_ID, agentId.toString());
        meta.put(DATASOURCE_ID, datasourceId.toString());
        meta.put(VECTOR_TYPE, VectorType.COLUMN.getCode());
        meta.put(TABLE_NAME, tableName);
        meta.put(NAME, col.columnName());
        meta.put(DESCRIPTION, colComment);
        meta.put(TYPE, col.dataType());

        // 采样数据
        try {
            List<String> samples = databaseAccessor.sampleColumn(dbConfig, tableName, col.columnName());
            meta.put(SAMPLES, String.join(", ", samples));
        } catch (Exception e) {
            log.warn("列采样失败: {}.{}, 原因: {}", tableName, col.columnName(), e.getMessage());
        }

        return new Document(content, meta);
    }

    /**
     * 批量写入表文档和列文档到向量库，使用 CompletableFuture 并行处理每批（最多10条）的异步写入
     */
    private void storeDocuments(Integer agentId, List<Document> tableDocs, List<Document> columnDocs) {
        final int batchSize = 10;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        if (!tableDocs.isEmpty()) {
            for (int i = 0; i < tableDocs.size(); i += batchSize) {
                List<Document> batch = tableDocs.subList(i, Math.min(i + batchSize, tableDocs.size()));
                futures.add(CompletableFuture.runAsync(() -> 
                    vectorStoreService.addDocuments(agentId.toString(), batch)
                ));
            }
        }
        if (!columnDocs.isEmpty()) {
            for (int i = 0; i < columnDocs.size(); i += batchSize) {
                List<Document> batch = columnDocs.subList(i, Math.min(i + batchSize, columnDocs.size()));
                futures.add(CompletableFuture.runAsync(() -> 
                    vectorStoreService.addDocuments(agentId.toString(), batch)
                ));
            }
        }
        
        if (!futures.isEmpty()) {
            log.info("开始并行异步写入向量文档, 总分批任务数: {}", futures.size());
            // 阻塞等待所有并发的分批写入任务全部成功执行完毕
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("并行写入向量文档全部完成! 表文档: {} 条, 列文档: {} 条", tableDocs.size(), columnDocs.size());
        }
    }
}
