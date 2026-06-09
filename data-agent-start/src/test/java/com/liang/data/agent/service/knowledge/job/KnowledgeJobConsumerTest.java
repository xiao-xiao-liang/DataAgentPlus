package com.liang.data.agent.service.knowledge.job;

import com.liang.data.agent.common.ratelimit.ResourceType;
import com.liang.data.agent.service.ratelimit.ResourceGate;
import com.liang.data.agent.service.ratelimit.ResourcePermit;
import com.liang.data.agent.service.knowledge.job.mq.KnowledgeJobConsumer;
import com.liang.data.agent.service.knowledge.job.mq.KnowledgeJobMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识文档任务消费者单元测试。
 */
class KnowledgeJobConsumerTest {

    @Test
    void shouldExecuteJobWhenMessageArrives() {
        AgentKnowledgeJobExecutor executor = mock(AgentKnowledgeJobExecutor.class);
        ResourceGate resourceGate = allowKnowledgeJob();
        KnowledgeJobConsumer consumer = new KnowledgeJobConsumer(executor, resourceGate);

        consumer.onMessage(new KnowledgeJobMessage(31L, "op-1"));

        verify(executor).execute(31L);
    }

    @Test
    void shouldIgnoreNullJobId() {
        AgentKnowledgeJobExecutor executor = mock(AgentKnowledgeJobExecutor.class);
        ResourceGate resourceGate = allowKnowledgeJob();
        KnowledgeJobConsumer consumer = new KnowledgeJobConsumer(executor, resourceGate);

        consumer.onMessage(new KnowledgeJobMessage(null, "op-1"));

        verify(executor, never()).execute(null);
    }

    @Test
    void shouldRetryWhenKnowledgeJobPermitRejected() {
        AgentKnowledgeJobExecutor executor = mock(AgentKnowledgeJobExecutor.class);
        ResourceGate resourceGate = mock(ResourceGate.class);
        when(resourceGate.tryAcquire(eq(ResourceType.KNOWLEDGE_JOB), anyString(), any()))
                .thenReturn(ResourcePermit.rejected(ResourceType.KNOWLEDGE_JOB, "job-31"));
        KnowledgeJobConsumer consumer = new KnowledgeJobConsumer(executor, resourceGate);

        assertThatThrownBy(() -> consumer.onMessage(new KnowledgeJobMessage(31L, "op-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("知识文档任务资源繁忙");
        verify(executor, never()).execute(31L);
    }

    private ResourceGate allowKnowledgeJob() {
        ResourceGate resourceGate = mock(ResourceGate.class);
        when(resourceGate.tryAcquire(eq(ResourceType.KNOWLEDGE_JOB), anyString(), any()))
                .thenReturn(ResourcePermit.acquired(ResourceType.KNOWLEDGE_JOB, "job", () -> {
                }));
        return resourceGate;
    }
}
