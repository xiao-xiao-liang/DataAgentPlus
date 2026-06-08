package com.liang.data.agent.service.knowledge.job.mq;

import com.liang.data.agent.service.knowledge.job.AgentKnowledgeJobExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

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

    @Override
    public void onMessage(KnowledgeJobMessage message) {
        if (message == null || message.jobId() == null) {
            log.warn("忽略无效的知识文档任务消息");
            return;
        }
        executor.execute(message.jobId());
    }
}
