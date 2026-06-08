package com.liang.data.agent.service.knowledge.job;

import com.liang.data.agent.service.knowledge.job.mq.KnowledgeJobMessagePublisher;
import com.liang.data.agent.service.knowledge.job.mq.KnowledgeJobMqConstant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 知识文档任务消息发布器单元测试。
 */
class KnowledgeJobMessagePublisherTest {

    @Test
    void shouldPublishJobMessage() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        KnowledgeJobMessagePublisher publisher = new KnowledgeJobMessagePublisher(template);

        publisher.publish(31L);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(template).send(eq(KnowledgeJobMqConstant.EXECUTE_DESTINATION), captor.capture());
        assertThat(captor.getValue().getPayload().toString()).contains("31");
    }
}
