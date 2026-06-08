package com.liang.data.agent.service.knowledge.job;

import com.liang.data.agent.service.knowledge.job.mq.KnowledgeJobConsumer;
import com.liang.data.agent.service.knowledge.job.mq.KnowledgeJobMessage;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 知识文档任务消费者单元测试。
 */
class KnowledgeJobConsumerTest {

    @Test
    void shouldExecuteJobWhenMessageArrives() {
        AgentKnowledgeJobExecutor executor = mock(AgentKnowledgeJobExecutor.class);
        KnowledgeJobConsumer consumer = new KnowledgeJobConsumer(executor);

        consumer.onMessage(new KnowledgeJobMessage(31L, "op-1"));

        verify(executor).execute(31L);
    }

    @Test
    void shouldIgnoreNullJobId() {
        AgentKnowledgeJobExecutor executor = mock(AgentKnowledgeJobExecutor.class);
        KnowledgeJobConsumer consumer = new KnowledgeJobConsumer(executor);

        consumer.onMessage(new KnowledgeJobMessage(null, "op-1"));

        verify(executor, never()).execute(null);
    }
}
