package com.liang.data.agent.service.knowledge.chunk;

import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkMessage;
import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkMessagePublisher;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 知识分块 RocketMQ 消息发布器单元测试。
 */
class KnowledgeChunkMessagePublisherTest {

    private final RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
    private final KnowledgeChunkMessagePublisher publisher = new KnowledgeChunkMessagePublisher(rocketMQTemplate);

    @Test
    void shouldPublishMinimalVectorizeMessage() {
        KnowledgeChunkMessage message = new KnowledgeChunkMessage(1, 10, "knowledge-10-chunk-3", 4);

        assertThat(publisher.publishVectorize(1, 10, "knowledge-10-chunk-3", 4)).isTrue();

        verify(rocketMQTemplate).convertAndSend(KnowledgeChunkMessagePublisher.VECTORIZE_DESTINATION, message);
    }

    @Test
    void shouldPublishMinimalGenerateNameMessage() {
        KnowledgeChunkMessage message = new KnowledgeChunkMessage(1, 10, "knowledge-10-chunk-3", 4);

        assertThat(publisher.publishGenerateName(1, 10, "knowledge-10-chunk-3", 4)).isTrue();

        verify(rocketMQTemplate).convertAndSend(KnowledgeChunkMessagePublisher.GENERATE_NAME_DESTINATION, message);
    }
}
