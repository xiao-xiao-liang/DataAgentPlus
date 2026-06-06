package com.liang.data.agent.ai.vectorstore;

import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.common.enums.VectorType;
import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

import static com.liang.data.agent.common.constant.VectorMetadataKey.*;

/**
 * 智能体向量存储服务实现
 *
 * <p>当前仅支持 Milvus VectorStore, ES 混合检索延后实现</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentVectorStoreServiceImpl implements AgentVectorStoreService {


    private final VectorStore vectorStore;
    private final DataAgentProperties properties;

    @Override
    public List<Document> search(String agentId, String query, VectorType vectorType, int topK, double threshold) {
        if (!StringUtils.hasText(agentId)) {
            throw new ServiceException("agentId 不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        if (vectorType == null) {
            throw new ServiceException("vectorType 不能为空", BaseErrorCode.CLIENT_ERROR);
        }

        String filterExpr = String.format("%s == '%s' && %s == '%s'", AGENT_ID, agentId, VECTOR_TYPE, vectorType.getCode());

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .filterExpression(filterExpr)
                .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);
        log.debug("向量检索完成: agentId={}, vectorType={}, 命中 {} 条", agentId, vectorType.getCode(), results.size());
        return results;
    }

    @Override
    public List<Document> search(String agentId, String query, VectorType vectorType) {
        var vs = properties.getVectorStore();
        return search(agentId, query, vectorType, vs.getDefaultTopkLimit(), vs.getDefaultSimilarityThreshold());
    }

    @Override
    public void addDocuments(String agentId, List<Document> documents) {
        if (!StringUtils.hasText(agentId)) {
            throw new ServiceException("agentId 不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        if (documents == null || documents.isEmpty()) {
            throw new ServiceException("documents 不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        vectorStore.add(documents);
        log.info("添加 {} 条文档到向量存储: agentId={}", documents.size(), agentId);
    }

    @Override
    public boolean deleteDocumentsByMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            throw new ServiceException("metadata 不能为 null", BaseErrorCode.CLIENT_ERROR);
        }
        
        StringBuilder sb = new StringBuilder();
        metadata.forEach((key, value) -> {
            if (!sb.isEmpty()) sb.append(" && ");
            sb.append(key).append(" == '").append(value).append("'");
        });
        
        vectorStore.delete(sb.toString());
        log.info("按元数据删除文档: {}", sb);
        return true;
    }

    @Override
    public void deleteDocumentsByIds(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        vectorStore.delete(documentIds);
        log.info("按 ID 删除 {} 条向量文档", documentIds.size());
    }

    @Override
    public boolean hasDocuments(String agentId) {
        String filterExpr = String.format("%s == '%s'", AGENT_ID, agentId);
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
