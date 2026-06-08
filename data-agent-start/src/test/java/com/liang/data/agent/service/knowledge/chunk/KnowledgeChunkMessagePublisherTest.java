package com.liang.data.agent.service.knowledge.chunk;

import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkMessagePublisher;
import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkMqConstant;
import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkTransactionOperation;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 知识分块消息发布器单元测试。
 */
class KnowledgeChunkMessagePublisherTest {

    @Test
    void shouldPublishVectorTaskAsTransactionMessage() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        TransactionSendResult sendResult = new TransactionSendResult();
        sendResult.setLocalTransactionState(LocalTransactionState.COMMIT_MESSAGE);
        when(template.sendMessageInTransaction(eq(KnowledgeChunkMqConstant.VECTORIZE_DESTINATION),
                any(Message.class), any())).thenReturn(sendResult);
        KnowledgeChunkMessagePublisher publisher = new KnowledgeChunkMessagePublisher(template);
        KnowledgeChunkTransactionOperation operation = new KnowledgeChunkTransactionOperation(
                KnowledgeChunkTransactionOperation.Type.RETRY_FAILED, 1, 10, "chunk-3",
                2, 4, null, null, null, null, null);

        assertThat(publisher.publishVectorizeTransaction(operation)).isTrue();
    }

    @Test
    void shouldPublishNameTaskAsNormalMessage() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        KnowledgeChunkMessagePublisher publisher = new KnowledgeChunkMessagePublisher(template);

        assertThat(publisher.publishGenerateName(1, 10, "chunk-3", 2)).isTrue();

        verify(template).convertAndSend(eq(KnowledgeChunkMqConstant.GENERATE_NAME_DESTINATION), any(Object.class));
    }

    @Test
    void shouldPublishCurrentVectorTaskAsNormalMessage() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        KnowledgeChunkMessagePublisher publisher = new KnowledgeChunkMessagePublisher(template);

        assertThat(publisher.publishVectorize(1, 10, "chunk-3", 2, 4)).isTrue();

        verify(template).convertAndSend(eq(KnowledgeChunkMqConstant.VECTORIZE_DESTINATION), any(Object.class));
    }
}
