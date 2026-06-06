package com.liang.data.agent.service.knowledge.chunk;

import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.entity.AgentKnowledgeEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import com.liang.data.agent.dal.mapper.AgentKnowledgeMapper;
import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkTransactionOperation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识分块超时恢复调度器单元测试。
 */
class KnowledgeChunkRecoverySchedulerTest {

    @Test
    void shouldPublishRecoveryTransactionForTimedOutTask() {
        AgentKnowledgeChunkMapper chunkMapper = mock(AgentKnowledgeChunkMapper.class);
        AgentKnowledgeMapper knowledgeMapper = mock(AgentKnowledgeMapper.class);
        KnowledgeChunkAsyncPublisher publisher = mock(KnowledgeChunkAsyncPublisher.class);
        KnowledgeChunkProperties properties = new KnowledgeChunkProperties();
        KnowledgeChunkRecoveryScheduler scheduler =
                new KnowledgeChunkRecoveryScheduler(chunkMapper, knowledgeMapper, publisher, properties);

        AgentKnowledgeChunkEntity chunk = new AgentKnowledgeChunkEntity();
        chunk.setKnowledgeId(10);
        chunk.setChunkId("chunk-3");
        chunk.setContentVersion(2);
        chunk.setVectorTaskVersion(4);
        chunk.setVectorStatus(ChunkVectorStatus.PROCESSING.getCode());
        when(chunkMapper.selectTimedOutTasks(any(LocalDateTime.class), eq(properties.getRecoveryBatchSize())))
                .thenReturn(List.of(chunk));
        AgentKnowledgeEntity knowledge = new AgentKnowledgeEntity();
        knowledge.setId(10);
        knowledge.setAgentId(1);
        when(knowledgeMapper.selectByIds(List.of(10))).thenReturn(List.of(knowledge));

        scheduler.recoverTimedOutTasks();

        verify(knowledgeMapper, never()).selectById(any());
        verify(publisher).publishVectorizeTransaction(argThat(operation ->
                operation.type() == KnowledgeChunkTransactionOperation.Type.RECOVER_TIMEOUT
                        && operation.agentId() == 1
                        && operation.expectedContentVersion() == 2
                        && operation.expectedTaskVersion() == 4
                        && ChunkVectorStatus.PROCESSING.getCode().equals(operation.expectedStatus())
                        && operation.deadline() != null));
    }
}
