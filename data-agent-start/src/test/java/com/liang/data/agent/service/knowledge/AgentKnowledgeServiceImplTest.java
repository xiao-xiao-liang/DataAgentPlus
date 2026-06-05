package com.liang.data.agent.service.knowledge;

import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.entity.AgentKnowledgeEntity;
import com.liang.data.agent.dal.entity.AgentKnowledgeJobEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import com.liang.data.agent.dal.mapper.AgentKnowledgeJobMapper;
import com.liang.data.agent.dal.mapper.AgentKnowledgeMapper;
import com.liang.data.agent.service.knowledge.impl.AgentKnowledgeServiceImpl;
import com.liang.data.agent.service.knowledge.job.AgentKnowledgeJobEvent;
import com.liang.data.agent.service.storage.FileObjectNameGenerator;
import com.liang.data.agent.service.storage.FileStorageService;
import com.liang.data.agent.service.storage.StoredFile;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 智能体知识源服务单元测试。
 */
class AgentKnowledgeServiceImplTest {

    private final AgentKnowledgeMapper agentKnowledgeMapper = mock(AgentKnowledgeMapper.class);
    private final AgentKnowledgeChunkMapper agentKnowledgeChunkMapper = mock(AgentKnowledgeChunkMapper.class);
    private final AgentKnowledgeJobMapper agentKnowledgeJobMapper = mock(AgentKnowledgeJobMapper.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AgentKnowledgeServiceImpl service = new AgentKnowledgeServiceImpl(
            agentKnowledgeChunkMapper,
            agentKnowledgeJobMapper,
            fileStorageService,
            new FileObjectNameGenerator(),
            eventPublisher
    );

    AgentKnowledgeServiceImplTest() {
        ReflectionTestUtils.setField(service, "baseMapper", agentKnowledgeMapper);
        service.setSelf(service);
    }

    @Test
    void uploadShouldCreatePendingJobAndPublishEvent() {
        when(agentKnowledgeMapper.insert(any(AgentKnowledgeEntity.class))).thenAnswer(invocation -> {
            AgentKnowledgeEntity entity = invocation.getArgument(0);
            entity.setId(12);
            return 1;
        });
        when(agentKnowledgeJobMapper.insert(any(AgentKnowledgeJobEntity.class))).thenAnswer(invocation -> {
            AgentKnowledgeJobEntity entity = invocation.getArgument(0);
            entity.setId(31L);
            return 1;
        });
        when(fileStorageService.upload(any(), any(Long.class), any(), any()))
                .thenReturn(new StoredFile("minio", "knowledge/1/20260605/metro.md", "http://example/metro.md", 42));

        var result = service.upload(
                1,
                "准点率口诀",
                "metro.md",
                new ByteArrayInputStream("整体准点率 准点列车数 总列车数".getBytes(StandardCharsets.UTF_8)),
                48,
                "title"
        );

        ArgumentCaptor<AgentKnowledgeEntity> knowledgeCaptor = ArgumentCaptor.forClass(AgentKnowledgeEntity.class);
        verify(agentKnowledgeMapper).insert(knowledgeCaptor.capture());
        AgentKnowledgeEntity saved = knowledgeCaptor.getValue();
        assertThat(saved.getSourceFilename()).isEqualTo("metro.md");
        assertThat(saved.getFilePath()).isEqualTo("knowledge/1/20260605/metro.md");
        assertThat(saved.getSplitterType()).isEqualTo("title");
        assertThat(saved.getEmbeddingStatus()).isEqualTo("PENDING");
        assertThat(result.getEmbeddingStatus()).isEqualTo("PENDING");

        ArgumentCaptor<AgentKnowledgeJobEntity> jobCaptor = ArgumentCaptor.forClass(AgentKnowledgeJobEntity.class);
        verify(agentKnowledgeJobMapper).insert(jobCaptor.capture());
        AgentKnowledgeJobEntity job = jobCaptor.getValue();
        assertThat(job.getKnowledgeId()).isEqualTo(12);
        assertThat(job.getAgentId()).isEqualTo(1);
        assertThat(job.getJobType()).isEqualTo("UPLOAD_VECTORIZE");
        assertThat(job.getStatus()).isEqualTo("PENDING");

        ArgumentCaptor<AgentKnowledgeJobEvent> eventCaptor = ArgumentCaptor.forClass(AgentKnowledgeJobEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().jobId()).isEqualTo(31L);
        verify(agentKnowledgeChunkMapper, never()).insert(any(AgentKnowledgeChunkEntity.class));
    }

    @Test
    void listChunksShouldQueryPersistedChunks() {
        AgentKnowledgeEntity entity = new AgentKnowledgeEntity();
        entity.setId(5);
        entity.setSplitterType("title");
        when(agentKnowledgeMapper.selectOne(any())).thenReturn(entity);

        AgentKnowledgeChunkEntity chunk = new AgentKnowledgeChunkEntity();
        chunk.setId(11L);
        chunk.setKnowledgeId(5);
        chunk.setChunkId("chunk-5-0");
        chunk.setChunkOrder(0);
        chunk.setContent("第一段内容\n第二段内容");
        chunk.setContentLength(11);
        chunk.setSplitterType("title");
        chunk.setStatus("VECTOR_STORED");
        when(agentKnowledgeChunkMapper.selectList(any())).thenReturn(List.of(chunk));

        var chunks = service.listChunks(1, 5);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().getContent()).contains("第一段内容");
        assertThat(chunks.getFirst().getSeq()).isZero();
    }

    @Test
    void deleteShouldCreateCleanupJobAndPublishEvent() {
        AgentKnowledgeEntity entity = new AgentKnowledgeEntity();
        entity.setId(12);
        entity.setAgentId(1);
        entity.setTitle("准点率口诀");
        entity.setSourceFilename("metro.md");
        entity.setFilePath("knowledge/1/20260605/metro.md");
        entity.setEmbeddingStatus("COMPLETED");
        when(agentKnowledgeMapper.selectOne(any())).thenReturn(entity);
        when(agentKnowledgeJobMapper.insert(any(AgentKnowledgeJobEntity.class))).thenAnswer(invocation -> {
            AgentKnowledgeJobEntity job = invocation.getArgument(0);
            job.setId(32L);
            return 1;
        });

        service.delete(1, 12);

        ArgumentCaptor<AgentKnowledgeEntity> knowledgeCaptor = ArgumentCaptor.forClass(AgentKnowledgeEntity.class);
        verify(agentKnowledgeMapper).updateById(knowledgeCaptor.capture());
        assertThat(knowledgeCaptor.getValue().getEmbeddingStatus()).isEqualTo("DELETING");

        ArgumentCaptor<AgentKnowledgeJobEntity> jobCaptor = ArgumentCaptor.forClass(AgentKnowledgeJobEntity.class);
        verify(agentKnowledgeJobMapper).insert(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getJobType()).isEqualTo("DELETE_CLEANUP");
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo("PENDING");

        ArgumentCaptor<AgentKnowledgeJobEvent> eventCaptor = ArgumentCaptor.forClass(AgentKnowledgeJobEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().jobId()).isEqualTo(32L);
        verify(agentKnowledgeChunkMapper, never()).delete(any());
    }

    @Test
    void listChunksShouldRejectKnowledgeFromOtherAgent() {
        when(agentKnowledgeMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.listChunks(2, 5))
                .hasMessageContaining("知识文件不存在");

        verify(agentKnowledgeChunkMapper, never()).selectList(any());
    }
}
