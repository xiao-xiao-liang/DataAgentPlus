package com.liang.data.agent.service.knowledge.chunk;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.service.knowledge.chunk.impl.DatabaseActiveKnowledgeVectorResolver;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static com.liang.data.agent.common.constant.VectorMetadataKey.CHUNK_ID;
import static com.liang.data.agent.common.constant.VectorMetadataKey.CONTENT_VERSION;
import static com.liang.data.agent.common.constant.VectorMetadataKey.VECTOR_TASK_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 数据库活跃知识向量过滤器单元测试。
 */
class DatabaseActiveKnowledgeVectorResolverTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldRetainOnlyCurrentEmbeddingAndVersions() {
        AgentKnowledgeChunkService chunkService = mock(AgentKnowledgeChunkService.class);
        LambdaQueryChainWrapper<AgentKnowledgeChunkEntity> query = mock(LambdaQueryChainWrapper.class);
        when(chunkService.lambdaQuery()).thenReturn(query);
        when(query.in(any(), anyCollection())).thenReturn(query);

        AgentKnowledgeChunkEntity chunk = new AgentKnowledgeChunkEntity();
        chunk.setChunkId("chunk-3");
        chunk.setEmbeddingId("active-vector");
        chunk.setContentVersion(2);
        chunk.setVectorTaskVersion(5);
        when(query.list()).thenReturn(List.of(chunk));

        DatabaseActiveKnowledgeVectorResolver resolver = new DatabaseActiveKnowledgeVectorResolver(chunkService);
        Document active = document("active-vector", "chunk-3", "2", "5");
        Document oldEmbedding = document("old-vector", "chunk-3", "2", "5");
        Document oldContent = document("active-vector", "chunk-3", "1", "5");
        Document oldTask = document("active-vector", "chunk-3", "2", "4");
        Document nonChunkDocument = new Document("普通知识");

        assertThat(resolver.retainActive(List.of(active, oldEmbedding, oldContent, oldTask, nonChunkDocument)))
                .containsExactly(active, nonChunkDocument);
    }

    private Document document(String id, String chunkId, String contentVersion, String taskVersion) {
        return new Document(id, "知识内容", Map.of(
                CHUNK_ID, chunkId,
                CONTENT_VERSION, contentVersion,
                VECTOR_TASK_VERSION, taskVersion));
    }
}
