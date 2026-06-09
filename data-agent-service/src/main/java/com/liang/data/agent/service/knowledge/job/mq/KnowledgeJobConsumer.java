package com.liang.data.agent.service.knowledge.job.mq;

import com.liang.data.agent.common.ratelimit.ResourceType;
import com.liang.data.agent.service.knowledge.job.AgentKnowledgeJobExecutor;
import com.liang.data.agent.service.ratelimit.ResourceGate;
import com.liang.data.agent.service.ratelimit.ResourcePermit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 知识文档任务消息消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = KnowledgeJobMqConstant.TOPIC,
        selectorExpression = KnowledgeJobMqConstant.TAG_EXECUTE,
        consumerGroup = KnowledgeJobMqConstant.EXECUTE_CONSUMER_GROUP)
public class KnowledgeJobConsumer implements RocketMQListener<KnowledgeJobMessage> {

    private final AgentKnowledgeJobExecutor executor;
    private final ResourceGate resourceGate;

    @Override
    public void onMessage(KnowledgeJobMessage message) {
        if (message == null || message.jobId() == null) {
            log.warn("忽略无效的知识文档任务消息");
            return;
        }

        // 1. 先申请知识任务资源，资源不足时抛出异常触发 RocketMQ 重试
        ResourcePermit permit = resourceGate.tryAcquire(ResourceType.KNOWLEDGE_JOB,
                "job-" + message.jobId(), Duration.ZERO);
        if (!permit.acquired()) {
            throw new IllegalStateException("知识文档任务资源繁忙，请稍后重试");
        }

        // 2. 获得许可后执行任务，并在执行结束或异常时释放许可
        try (permit) {
            executor.execute(message.jobId());
        }
    }
}
