package com.liang.data.agent.ai.schema;

import com.liang.data.agent.common.schema.SchemaDTO;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Schema 服务
 *
 * <p>统一管理数据库 Schema 的初始化（写入向量库）和检索（从向量库读取）</p>
 */
public interface SchemaService {

    // ==================== 检索 ====================

    /**
     * 语义向量检索相关表文档
     *
     * @param datasourceId 数据源 ID
     * @param query        规范化查询
     * @return 召回的表文档列表
     */
    List<Document> recallTableDocuments(Integer agentId, Integer datasourceId, String query);

    /**
     * 根据表名精确检索列文档（元数据过滤，非语义检索）
     *
     * @param datasourceId 数据源 ID
     * @param tableNames   表名列表
     * @return 列文档列表
     */
    List<Document> getColumnDocumentsByTableNames(Integer agentId, Integer datasourceId, List<String> tableNames);

    /**
     * 从 Document 列表构建 SchemaDTO（含外键关系提取）
     *
     * @param tableDocs  表文档
     * @param columnDocs 列文档
     * @return SchemaDTO
     */
    SchemaDTO buildSchemaFromDocuments(List<Document> tableDocs, List<Document> columnDocs);

    // ==================== 初始化 ====================

    /**
     * 初始化 Schema: 连接数据库拉取元数据, 向量化后写入向量库
     *
     * <p>会先清理该 datasourceId 下旧的 TABLE/COLUMN 类型文档, 再写入新文档</p>
     *
     * @param agentId      智能体 ID
     * @param datasourceId 数据源 ID
     * @param dbConfig     数据库连接配置
     * @param tables       需要初始化的表名列表
     */
    void initializeSchema(Integer agentId, Integer datasourceId, DbConfigBO dbConfig, List<String> tables);
}
