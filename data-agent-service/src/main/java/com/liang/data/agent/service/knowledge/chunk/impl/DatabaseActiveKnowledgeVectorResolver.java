package com.liang.data.agent.service.knowledge.chunk.impl;

import com.liang.data.agent.ai.vectorstore.ActiveKnowledgeVectorResolver;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.service.knowledge.chunk.AgentKnowledgeChunkService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.liang.data.agent.common.constant.VectorMetadataKey.CHUNK_ID;
import static com.liang.data.agent.common.constant.VectorMetadataKey.CONTENT_VERSION;
import static com.liang.data.agent.common.constant.VectorMetadataKey.VECTOR_TASK_VERSION;

/**
 * 基于数据库当前版本批量校验知识分块向量。
 */
@Component
@RequiredArgsConstructor
public class DatabaseActiveKnowledgeVectorResolver implements ActiveKnowledgeVectorResolver {

    private final AgentKnowledgeChunkService chunkService;

    @Override
    public List<Document> retainActive(List<Document> documents) {
        List<String> chunkIds = documents.stream()
                .map(document -> stringMetadata(document, CHUNK_ID))
                .filter(value -> value != null)
                .distinct()
                .toList();
        if (chunkIds.isEmpty()) {
            return documents;
        }
        Map<String, AgentKnowledgeChunkEntity> activeByChunkId = chunkService.lambdaQuery()
                .in(AgentKnowledgeChunkEntity::getChunkId, chunkIds)
                .list()
                .stream()
                .collect(Collectors.toMap(AgentKnowledgeChunkEntity::getChunkId, Function.identity()));
        return documents.stream().filter(document -> isActive(document, activeByChunkId)).toList();
    }

    private boolean isActive(Document document, Map<String, AgentKnowledgeChunkEntity> activeByChunkId) {
        String chunkId = stringMetadata(document, CHUNK_ID);
        if (chunkId == null) {
            return true;
        }
        AgentKnowledgeChunkEntity chunk = activeByChunkId.get(chunkId);
        return chunk != null
                && document.getId().equals(chunk.getEmbeddingId())
                && String.valueOf(chunk.getContentVersion()).equals(stringMetadata(document, CONTENT_VERSION))
                && String.valueOf(chunk.getVectorTaskVersion()).equals(stringMetadata(document, VECTOR_TASK_VERSION));
    }

    private String stringMetadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? null : value.toString();
    }
}
