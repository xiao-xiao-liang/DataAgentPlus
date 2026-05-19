package com.liang.data.agent.ai.vectorstore;

import com.liang.data.agent.common.enums.VectorType;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

/**
 * 智能体向量存储服务接口
 *
 * <p>封装 Spring AI VectorStore 抽象, 提供按 agentId 和 vectorType 隔离的文档管理</p>
 */
public interface AgentVectorStoreService {

    /**
     * 向量检索
     *
     * @param agentId    智能体 ID
     * @param query      查询文本
     * @param vectorType 文档类型
     * @param topK       返回数量
     * @param threshold  相似度阈值
     * @return 匹配的文档列表
     */
    List<Document> search(String agentId, String query, VectorType vectorType,
                          int topK, double threshold);

    /**
     * 使用默认 topK 和 threshold 的检索
     */
    List<Document> search(String agentId, String query, VectorType vectorType);

    /**
     * 添加文档到向量存储
     *
     * @param agentId   智能体 ID
     * @param documents 文档列表 (metadata 中须包含 vectorType)
     */
    void addDocuments(String agentId, List<Document> documents);

    /**
     * 按元数据删除文档
     *
     * @param metadata 元数据条件
     * @return 是否成功
     */
    boolean deleteDocumentsByMetadata(Map<String, Object> metadata);

    /**
     * 检查智能体是否已有向量化文档
     */
    boolean hasDocuments(String agentId);
}
