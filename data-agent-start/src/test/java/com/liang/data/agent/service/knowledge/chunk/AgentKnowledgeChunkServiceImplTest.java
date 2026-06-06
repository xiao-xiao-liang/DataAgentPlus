package com.liang.data.agent.service.knowledge.chunk;

import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.entity.AgentKnowledgeEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import com.liang.data.agent.dal.mapper.AgentKnowledgeMapper;
import com.liang.data.agent.service.knowledge.chunk.impl.AgentKnowledgeChunkServiceImpl;
import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkTransactionOperation;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 知识分块工作台服务单元测试。
 */
class AgentKnowledgeChunkServiceImplTest {
    private final AgentKnowledgeMapper knowledgeMapper = mock(AgentKnowledgeMapper.class);
    private final AgentKnowledgeChunkMapper chunkMapper = mock(AgentKnowledgeChunkMapper.class);
    private final KnowledgeChunkAsyncPublisher publisher = mock(KnowledgeChunkAsyncPublisher.class);
    private final KnowledgeChunkProperties properties = new KnowledgeChunkProperties();
    private final AgentKnowledgeChunkServiceImpl service =
            new AgentKnowledgeChunkServiceImpl(knowledgeMapper, chunkMapper, publisher, properties);
    private AgentKnowledgeChunkEntity chunk;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", chunkMapper);
        AgentKnowledgeEntity knowledge = new AgentKnowledgeEntity();
        knowledge.setId(10);
        knowledge.setAgentId(1);
        when(knowledgeMapper.selectOne(any())).thenReturn(knowledge);
        chunk = new AgentKnowledgeChunkEntity();
        chunk.setKnowledgeId(10);
        chunk.setChunkId("chunk-3");
        chunk.setChunkOrder(3);
        chunk.setName("原名称");
        chunk.setNameLocked(0);
        chunk.setContent("原正文");
        chunk.setContentLength(3);
        chunk.setContentVersion(2);
        chunk.setVectorTaskVersion(4);
        chunk.setVectorStatus(ChunkVectorStatus.SYNCED.getCode());
        when(chunkMapper.selectOne(any())).thenReturn(chunk);
        when(chunkMapper.selectOne(any(), anyBoolean())).thenReturn(chunk);
    }

    @Test
    void nameOnlyUpdateShouldNotPublishVectorTask() {
        when(chunkMapper.updateNameOnly("chunk-3", 2, "新名称")).thenReturn(1);

        var result = service.update(1, 10, "chunk-3", request("新名称", "原正文"));

        assertThat(result.isMessageSubmitted()).isFalse();
        verifyNoInteractions(publisher);
    }

    @Test
    void contentUpdateShouldPublishTransactionAndDecideNameLockOnServer() {
        when(publisher.publishVectorizeTransaction(any())).thenAnswer(invocation -> {
            KnowledgeChunkTransactionOperation operation = invocation.getArgument(0);
            assertThat(operation.expectedContentVersion()).isEqualTo(2);
            assertThat(operation.expectedTaskVersion()).isEqualTo(4);
            assertThat(operation.nameLocked()).isEqualTo(1);
            chunk.setContentVersion(3);
            chunk.setVectorTaskVersion(5);
            chunk.setVectorStatus(ChunkVectorStatus.PENDING.getCode());
            chunk.setNameLocked(1);
            return true;
        });

        var result = service.update(1, 10, "chunk-3", request("新名称", "新正文"));

        assertThat(result.isMessageSubmitted()).isTrue();
        assertThat(result.getDetail().getVectorTaskVersion()).isEqualTo(5);
    }

    @Test
    void retryShouldUseTransactionAndCurrentTaskVersion() {
        when(publisher.publishVectorizeTransaction(any())).thenReturn(true);

        assertThat(service.retry(1, 10, "chunk-3").isMessageSubmitted()).isTrue();
        verify(publisher).publishVectorizeTransaction(argThat(operation ->
                operation.type() == KnowledgeChunkTransactionOperation.Type.RETRY_FAILED
                        && operation.expectedTaskVersion() == 4));
    }

    private KnowledgeChunkUpdateRequest request(String name, String content) {
        KnowledgeChunkUpdateRequest request = new KnowledgeChunkUpdateRequest();
        request.setName(name);
        request.setContent(content);
        request.setContentVersion(2);
        return request;
    }
}
