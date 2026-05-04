package com.liang.data.agent.ai.vectorstore;

import com.liang.data.agent.common.config.DataAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;

/**
 * 智能体向量存储服务实现
 *
 * <p>当前仅支持 Milvus VectorStore, ES 混合检索延后实现</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentVectorStoreServiceImpl implements AgentVectorStoreService {

    private static final String AGENT_ID_KEY = "agent_id";
    private static final String VECTOR_TYPE_KEY = "vector_type";
    private static final String DEFAULT_QUERY = "default";
    
    private final VectorStore vectorStore;
    private final DataAgentProperties properties;
    
    @Override
    public List<Document> search(String agentId, String query, String vectorType, int topK, double threshold) {
        Assert.hasText(agentId, "agentId 不能为空");
        Assert.hasText(vectorType, "vectorType 不能为空");

        // 构建过滤表达式: agent_id == 'xxx' AND vector_type == 'yyy'
        String filterExpr = String.format("%s == '%s' && %s == '%s'", AGENT_ID_KEY, agentId, VECTOR_TYPE_KEY, vectorType);

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .filterExpression(filterExpr)
                .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);
        log.debug("向量检索完成: agentId={}, vectorType={}, 命中 {} 条", agentId, vectorType, results.size());
        return results;
    }

    @Override
    public List<Document> search(String agentId, String query, String vectorType) {
        var vs = properties.getVectorStore();
        return search(agentId, query, vectorType, vs.getDefaultTopkLimit(), vs.getDefaultSimilarityThreshold());
    }

    @Override
    public void addDocuments(String agentId, List<Document> documents) {
        Assert.hasText(agentId, "agentId 不能为空");
        Assert.notEmpty(documents, "documents 不能为空");
        vectorStore.add(documents);
        log.info("添加 {} 条文档到向量存储: agentId={}", documents.size(), agentId);
    }

    @Override
    public boolean deleteDocumentsByMetadata(Map<String, Object> metadata) {
        Assert.notNull(metadata, "metadata 不能为 null");
        
        StringBuilder sb = new StringBuilder();
        metadata.forEach((key, value) -> {
            if (!sb.isEmpty()) sb.append("&");
            sb.append(key).append(" == '").append(value).append("'");
        });
        
        vectorStore.delete(sb.toString());
        log.info("按元数据删除文档: {}", sb);
        return true;
    }

    @Override
    public boolean hasDocuments(String agentId) {
        String filterExpr = String.format("%s == '%s'", AGENT_ID_KEY, agentId);
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(DEFAULT_QUERY)
                        .filterExpression(filterExpr)
                        .topK(1)
                        .similarityThreshold(0.0)
                        .build()
        );
        return !docs.isEmpty();
    }
}
