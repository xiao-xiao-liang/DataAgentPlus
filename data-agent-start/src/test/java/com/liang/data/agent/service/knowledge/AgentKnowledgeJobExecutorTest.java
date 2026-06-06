package com.liang.data.agent.service.knowledge;

import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.liang.data.agent.ai.vectorstore.AgentVectorStoreService;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.entity.AgentKnowledgeEntity;
import com.liang.data.agent.dal.entity.AgentKnowledgeJobEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import com.liang.data.agent.dal.mapper.AgentKnowledgeJobMapper;
import com.liang.data.agent.dal.mapper.AgentKnowledgeMapper;
import com.liang.data.agent.service.knowledge.job.AgentKnowledgeJobExecutor;
import com.liang.data.agent.service.knowledge.chunk.KnowledgeChunkAsyncPublisher;
import com.liang.data.agent.service.knowledge.parser.TikaDocumentParser;
import com.liang.data.agent.service.knowledge.splitter.AgentKnowledgeTextSplitter;
import com.liang.data.agent.service.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.ai.document.Document;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 智能体知识异步任务执行器单元测试。
 */
class AgentKnowledgeJobExecutorTest {

    private final AgentKnowledgeMapper agentKnowledgeMapper = mock(AgentKnowledgeMapper.class);
    private final AgentKnowledgeChunkMapper agentKnowledgeChunkMapper = mock(AgentKnowledgeChunkMapper.class);
    private final AgentKnowledgeJobMapper agentKnowledgeJobMapper = mock(AgentKnowledgeJobMapper.class);
    private final AgentVectorStoreService vectorStoreService = mock(AgentVectorStoreService.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final KnowledgeChunkAsyncPublisher chunkAsyncPublisher = mock(KnowledgeChunkAsyncPublisher.class);
    private final AgentKnowledgeJobExecutor executor = new AgentKnowledgeJobExecutor(
            agentKnowledgeMapper,
            agentKnowledgeChunkMapper,
            agentKnowledgeJobMapper,
            vectorStoreService,
            new TikaDocumentParser(),
            new AgentKnowledgeTextSplitter(),
            fileStorageService,
            chunkAsyncPublisher
    );

    @Test
    void executeUploadJobShouldVectorizeDocumentAndMarkSuccess() {
        AgentKnowledgeJobEntity job = new AgentKnowledgeJobEntity();
        job.setId(31L);
        job.setKnowledgeId(12);
        job.setAgentId(1);
        job.setJobType("UPLOAD_VECTORIZE");
        job.setStatus("PENDING");
        job.setRetryCount(0);
        job.setMaxRetryCount(3);
        when(agentKnowledgeJobMapper.selectById(31L)).thenReturn(job);
        when(agentKnowledgeJobMapper.update(any(), any())).thenReturn(1);

        AgentKnowledgeEntity knowledge = new AgentKnowledgeEntity();
        knowledge.setId(12);
        knowledge.setAgentId(1);
        knowledge.setTitle("准点率口诀");
        knowledge.setSourceFilename("metro.md");
        knowledge.setFilePath("knowledge/1/20260605/metro.md");
        knowledge.setFileType("md");
        knowledge.setSplitterType("title");
        when(agentKnowledgeMapper.selectById(12)).thenReturn(knowledge);
        when(fileStorageService.openStream("knowledge/1/20260605/metro.md"))
                .thenReturn(new ByteArrayInputStream("整体准点率 准点列车数 总列车数".getBytes(StandardCharsets.UTF_8)));
        executor.setSelf(executor);

        try (MockedStatic<Db> db = mockStatic(Db.class)) {
            db.when(() -> Db.saveBatch(any(Collection.class))).thenReturn(true);
            db.when(() -> Db.updateBatchById(any(Collection.class))).thenReturn(true);

            executor.execute(31L);

            verify(agentKnowledgeChunkMapper).delete(any());
            verify(vectorStoreService).deleteDocumentsByMetadata(any());

            ArgumentCaptor<Collection> chunkBatchCaptor = ArgumentCaptor.forClass(Collection.class);
            db.verify(() -> Db.saveBatch(chunkBatchCaptor.capture()));
            Collection<AgentKnowledgeChunkEntity> chunks = chunkBatchCaptor.getValue();
            assertThat(chunks).hasSize(1);
            AgentKnowledgeChunkEntity chunk = chunks.iterator().next();
            assertThat(chunk.getKnowledgeId()).isEqualTo(12);
            assertThat(chunk.getChunkId()).isEqualTo("knowledge-12-chunk-0");
            assertThat(chunk.getContent()).contains("整体准点率");
            assertThat(chunk.getName()).isEqualTo("整体准点率 准点列车数 总列车数");
            assertThat(chunk.getNameLocked()).isZero();
            assertThat(chunk.getContentVersion()).isEqualTo(1);
            assertThat(chunk.getVectorVersion()).isEqualTo(1);
            assertThat(chunk.getVectorStatus()).isEqualTo("SYNCED");
            assertThat(chunk.getEmbeddingId()).isEqualTo("knowledge-12-chunk-0-c1-t1");
            assertThat(chunk.getRetryCount()).isZero();

            ArgumentCaptor<List<Document>> documentCaptor = ArgumentCaptor.forClass(List.class);
            verify(vectorStoreService).addDocuments(eq("1"), documentCaptor.capture());
            assertThat(documentCaptor.getValue()).hasSize(1);
            assertThat(documentCaptor.getValue().getFirst().getId()).isEqualTo("knowledge-12-chunk-0-c1-t1");
            assertThat(documentCaptor.getValue().getFirst().getMetadata())
                    .containsEntry("agentKnowledgeId", "12")
                    .containsEntry("vector_type", "KNOWLEDGE");

            ArgumentCaptor<AgentKnowledgeEntity> knowledgeCaptor = ArgumentCaptor.forClass(AgentKnowledgeEntity.class);
            verify(agentKnowledgeMapper, atLeastOnce()).updateById(knowledgeCaptor.capture());
            assertThat(knowledgeCaptor.getAllValues().getLast().getEmbeddingStatus()).isEqualTo("COMPLETED");

            ArgumentCaptor<AgentKnowledgeJobEntity> jobCaptor = ArgumentCaptor.forClass(AgentKnowledgeJobEntity.class);
            verify(agentKnowledgeJobMapper, atLeastOnce()).updateById(jobCaptor.capture());
            assertThat(jobCaptor.getAllValues().getLast().getStatus()).isEqualTo("SUCCESS");
            verify(chunkAsyncPublisher).publishGenerateName(1, 12, "knowledge-12-chunk-0", 1);
        }
    }
}
