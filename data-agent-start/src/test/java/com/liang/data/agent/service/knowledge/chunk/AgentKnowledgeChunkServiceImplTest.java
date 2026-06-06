package com.liang.data.agent.service.knowledge.chunk;

import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.entity.AgentKnowledgeEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import com.liang.data.agent.dal.mapper.AgentKnowledgeMapper;
import com.liang.data.agent.service.knowledge.chunk.impl.AgentKnowledgeChunkServiceImpl;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识分块工作台服务单元测试。
 */
class AgentKnowledgeChunkServiceImplTest {

    private final AgentKnowledgeMapper knowledgeMapper = mock(AgentKnowledgeMapper.class);
    private final AgentKnowledgeChunkMapper chunkMapper = mock(AgentKnowledgeChunkMapper.class);
    private final KnowledgeChunkAsyncPublisher publisher = mock(KnowledgeChunkAsyncPublisher.class);
    private final AgentKnowledgeChunkServiceImpl service =
            new AgentKnowledgeChunkServiceImpl(knowledgeMapper, chunkMapper, publisher);
    private AgentKnowledgeChunkEntity chunk;

    @BeforeEach
    void setUp() {
        AgentKnowledgeEntity knowledge = new AgentKnowledgeEntity();
        knowledge.setId(10);
        knowledge.setAgentId(1);
        when(knowledgeMapper.selectOne(any())).thenReturn(knowledge);

        chunk = new AgentKnowledgeChunkEntity();
        chunk.setKnowledgeId(10);
        chunk.setChunkId("knowledge-10-chunk-3");
        chunk.setChunkOrder(3);
        chunk.setName("原名称");
        chunk.setNameLocked(1);
        chunk.setContent("原正文");
        chunk.setContentLength(3);
        chunk.setContentVersion(2);
        chunk.setVectorVersion(1);
        chunk.setVectorStatus("SYNCED");
        chunk.setRetryCount(2);
        when(chunkMapper.selectOne(any())).thenAnswer(invocation -> chunk);
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
    }

    @Test
    void updateShouldKeepLockedNameAndPublishNewVersion() {
        when(chunkMapper.updateContentWithVersion(eq(chunk.getChunkId()), eq(2), eq("新名称"), eq(1),
                eq("新正文"), eq(3))).thenAnswer(invocation -> {
            chunk.setName("新名称");
            chunk.setContent("新正文");
            chunk.setContentVersion(3);
            chunk.setVectorStatus("PENDING");
            chunk.setRetryCount(0);
            return 1;
        });
        when(publisher.publishVectorize(1, 10, chunk.getChunkId(), 3)).thenReturn(true);

        var result = service.update(1, 10, chunk.getChunkId(), request(false));

        assertThat(result.isMessageSubmitted()).isTrue();
        assertThat(result.getDetail().getContentVersion()).isEqualTo(3);
        assertThat(result.getDetail().getVectorStatus()).isEqualTo("PENDING");
        verify(chunkMapper).updateContentWithVersion(chunk.getChunkId(), 2, "新名称", 1, "新正文", 3);
    }

    @Test
    void updateShouldLockManualNameAndReportPublishFailure() {
        when(chunkMapper.updateContentWithVersion(any(), any(), any(), eq(1), any(), any())).thenAnswer(invocation -> {
            chunk.setContentVersion(3);
            chunk.setNameLocked(1);
            chunk.setVectorStatus("PENDING");
            return 1;
        });
        when(publisher.publishVectorize(any(), any(), any(), any())).thenReturn(false);

        var result = service.update(1, 10, chunk.getChunkId(), request(true));

        assertThat(result.isMessageSubmitted()).isFalse();
        verify(chunkMapper).updateContentWithVersion(chunk.getChunkId(), 2, "新名称", 1, "新正文", 3);
    }

    @Test
    void updateShouldKeepSavedContentWhenPublisherThrows() {
        when(chunkMapper.updateContentWithVersion(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            chunk.setContentVersion(3);
            chunk.setVectorStatus("PENDING");
            return 1;
        });
        when(publisher.publishVectorize(any(), any(), any(), any())).thenThrow(new IllegalStateException("消息服务不可用"));

        var result = service.update(1, 10, chunk.getChunkId(), request(false));

        assertThat(result.isMessageSubmitted()).isFalse();
        assertThat(result.getDetail().getContentVersion()).isEqualTo(3);
    }

    @Test
    void updateShouldRejectStaleVersion() {
        when(chunkMapper.updateContentWithVersion(any(), any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.update(1, 10, chunk.getChunkId(), request(false)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("分块已被其他操作更新");
    }

    @Test
    void outlineShouldNotExposeContent() {
        var outlines = service.listOutlines(1, 10, "正文", "SYNCED");

        assertThat(outlines).hasSize(1);
        assertThat(outlines.getFirst().getName()).isEqualTo("原名称");
        assertThat(outlines.getFirst().getClass().getDeclaredFields())
                .noneMatch(field -> field.getName().equals("content"));
    }

    @Test
    void retryAndGenerateNameShouldPublishCurrentVersion() {
        when(chunkMapper.resetVectorStatus(chunk.getChunkId(), 2)).thenReturn(1);
        when(chunkMapper.unlockName(chunk.getChunkId(), 2)).thenReturn(1);
        when(publisher.publishVectorize(1, 10, chunk.getChunkId(), 2)).thenReturn(true);
        when(publisher.publishGenerateName(1, 10, chunk.getChunkId(), 2)).thenReturn(true);

        assertThat(service.retry(1, 10, chunk.getChunkId()).isMessageSubmitted()).isTrue();
        assertThat(service.generateName(1, 10, chunk.getChunkId()).isMessageSubmitted()).isTrue();
        verify(publisher).publishVectorize(1, 10, chunk.getChunkId(), 2);
        verify(publisher).publishGenerateName(1, 10, chunk.getChunkId(), 2);
    }

    @Test
    void shouldRejectKnowledgeFromOtherAgent() {
        when(knowledgeMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getDetail(2, 10, chunk.getChunkId()))
                .hasMessageContaining("知识文件不存在");
    }

    private KnowledgeChunkUpdateRequest request(boolean manualNameChanged) {
        KnowledgeChunkUpdateRequest request = new KnowledgeChunkUpdateRequest();
        request.setName(" 新名称 ");
        request.setContent("新正文");
        request.setContentVersion(2);
        request.setManualNameChanged(manualNameChanged);
        return request;
    }
}
