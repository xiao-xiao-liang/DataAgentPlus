package com.liang.data.agent.service.knowledge.chunk;

import com.liang.data.agent.ai.vectorstore.AgentVectorStoreService;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.entity.AgentKnowledgeEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import com.liang.data.agent.dal.mapper.AgentKnowledgeMapper;
import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkDeadLetterConsumer;
import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkMessage;
import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkVectorConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 知识分块向量化消费者单元测试。
 */
class KnowledgeChunkVectorConsumerTest {

    private final AgentKnowledgeMapper knowledgeMapper = mock(AgentKnowledgeMapper.class);
    private final AgentKnowledgeChunkMapper chunkMapper = mock(AgentKnowledgeChunkMapper.class);
    private final AgentVectorStoreService vectorStoreService = mock(AgentVectorStoreService.class);
    private final KnowledgeChunkVectorConsumer consumer =
            new KnowledgeChunkVectorConsumer(knowledgeMapper, chunkMapper, vectorStoreService);
    private final KnowledgeChunkMessage message = new KnowledgeChunkMessage(1, 10, "knowledge-10-chunk-3", 4);
    private AgentKnowledgeChunkEntity chunk;

    @BeforeEach
    void setUp() {
        AgentKnowledgeEntity knowledge = new AgentKnowledgeEntity();
        knowledge.setId(10);
        knowledge.setAgentId(1);
        knowledge.setTitle("列车运行知识");
        knowledge.setSourceFilename("metro.md");
        when(knowledgeMapper.selectById(10)).thenReturn(knowledge);

        chunk = new AgentKnowledgeChunkEntity();
        chunk.setKnowledgeId(10);
        chunk.setChunkId(message.chunkId());
        chunk.setChunkOrder(3);
        chunk.setContent("延误判断规则");
        chunk.setContentVersion(4);
        chunk.setVectorVersion(2);
        chunk.setEmbeddingId("legacy-vector-id");
        chunk.setSplitterType("title");
        when(chunkMapper.selectOne(any())).thenReturn(chunk);
    }

    @Test
    void shouldIgnoreStaleMessage() {
        chunk.setContentVersion(5);

        consumer.onMessage(message);

        verify(chunkMapper, never()).claimVectorProcessing(any(), any());
        verify(vectorStoreService, never()).addDocuments(any(), any());
    }

    @Test
    void shouldWriteNewVectorThenCompleteAndDeletePreviousVersion() {
        when(chunkMapper.claimVectorProcessing(message.chunkId(), 4)).thenReturn(1);
        when(chunkMapper.completeVectorIfProcessing(message.chunkId(), 4)).thenReturn(1);

        consumer.onMessage(message);

        ArgumentCaptor<List<Document>> documents = ArgumentCaptor.forClass(List.class);
        verify(vectorStoreService).addDocuments(eq("1"), documents.capture());
        assertThat(documents.getValue().getFirst().getId()).isEqualTo("knowledge-10-chunk-3-v4");
        assertThat(documents.getValue().getFirst().getMetadata()).containsEntry("chunkVersion", "4");
        verify(chunkMapper).completeVectorIfProcessing(message.chunkId(), 4);
        verify(vectorStoreService).deleteDocumentsByIds(List.of("legacy-vector-id"));
    }

    @Test
    void shouldDeleteNewVectorWhenVersionChangesBeforeCompletion() {
        when(chunkMapper.claimVectorProcessing(message.chunkId(), 4)).thenReturn(1);
        when(chunkMapper.completeVectorIfProcessing(message.chunkId(), 4)).thenReturn(0);

        consumer.onMessage(message);

        verify(vectorStoreService).deleteDocumentsByIds(List.of("knowledge-10-chunk-3-v4"));
        verify(vectorStoreService, never()).deleteDocumentsByIds(List.of("knowledge-10-chunk-3-v2"));
    }

    @Test
    void shouldRecordRetryAndRethrowOnVectorFailure() {
        when(chunkMapper.claimVectorProcessing(message.chunkId(), 4)).thenReturn(1);
        doThrow(new IllegalStateException("向量库不可用"))
                .when(vectorStoreService).addDocuments(any(), any());

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("向量库不可用");
        verify(chunkMapper).recordVectorRetry(message.chunkId(), 4, "向量库不可用");
    }

    @Test
    void duplicateSyncedMessageShouldDoNothing() {
        when(chunkMapper.claimVectorProcessing(message.chunkId(), 4)).thenReturn(0);

        consumer.onMessage(message);

        verify(vectorStoreService, never()).addDocuments(any(), any());
    }

    @Test
    void deadLetterShouldMarkOnlyCurrentVersionFailed() {
        KnowledgeChunkDeadLetterConsumer deadLetterConsumer = new KnowledgeChunkDeadLetterConsumer(chunkMapper);

        deadLetterConsumer.onMessage(message);

        verify(chunkMapper).markVectorFailedIfCurrent(
                message.chunkId(), 4, "分块向量化重试耗尽，请手动重试");
    }
}
