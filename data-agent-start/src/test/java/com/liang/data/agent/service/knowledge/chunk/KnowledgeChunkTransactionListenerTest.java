package com.liang.data.agent.service.knowledge.chunk;

import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkTransactionListener;
import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkTransactionOperation;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 知识分块事务消息监听器单元测试。
 */
class KnowledgeChunkTransactionListenerTest {

    private final AgentKnowledgeChunkMapper chunkMapper = mock(AgentKnowledgeChunkMapper.class);
    private final KnowledgeChunkTransactionListener listener = new KnowledgeChunkTransactionListener(chunkMapper);

    @Test
    void shouldCommitOnlyWhenLocalCasSucceeds() {
        KnowledgeChunkTransactionOperation operation = new KnowledgeChunkTransactionOperation(
                KnowledgeChunkTransactionOperation.Type.RETRY_FAILED, 1, 10, "chunk-3",
                2, 4, null, null, null, null, null);
        when(chunkMapper.retryFailedTask("chunk-3", 2, 4)).thenReturn(1);

        assertThat(listener.executeLocalTransaction(message(2, 5), operation))
                .isEqualTo(RocketMQLocalTransactionState.COMMIT);

        when(chunkMapper.retryFailedTask("chunk-3", 2, 4)).thenReturn(0);
        assertThat(listener.executeLocalTransaction(message(2, 5), operation))
                .isEqualTo(RocketMQLocalTransactionState.ROLLBACK);
    }

    @Test
    void brokerCheckShouldCommitOnlyCurrentResultVersion() {
        AgentKnowledgeChunkEntity chunk = new AgentKnowledgeChunkEntity();
        chunk.setChunkId("chunk-3");
        chunk.setContentVersion(2);
        chunk.setVectorTaskVersion(5);
        when(chunkMapper.selectOne(any())).thenReturn(chunk);

        assertThat(listener.checkLocalTransaction(message(2, 5)))
                .isEqualTo(RocketMQLocalTransactionState.COMMIT);
        assertThat(listener.checkLocalTransaction(message(2, 4)))
                .isEqualTo(RocketMQLocalTransactionState.ROLLBACK);
    }

    private Message<String> message(int contentVersion, int taskVersion) {
        return MessageBuilder.withPayload("payload")
                .setHeader("chunkId", "chunk-3")
                .setHeader("contentVersion", contentVersion)
                .setHeader("taskVersion", taskVersion)
                .build();
    }
}
