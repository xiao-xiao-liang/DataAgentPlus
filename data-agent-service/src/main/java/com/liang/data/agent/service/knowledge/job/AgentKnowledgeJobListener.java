package com.liang.data.agent.service.knowledge.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 智能体知识任务事件监听器。
 *
 * <p>监听任务创建事件，并交给 Spring 线程池异步执行。</p>
 */
@Slf4j
@Component
public class AgentKnowledgeJobListener {

    private final AgentKnowledgeJobExecutor executor;
    private final TaskExecutor taskExecutor;

    /**
     * 创建智能体知识任务事件监听器。
     *
     * @param executor 知识任务执行器
     * @param taskExecutor 应用异步任务线程池
     */
    public AgentKnowledgeJobListener(AgentKnowledgeJobExecutor executor,
                                     @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.executor = executor;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 接收知识任务事件并在事务提交后异步执行。
     *
     * @param event 任务事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobCreated(AgentKnowledgeJobEvent event) {
        taskExecutor.execute(() -> {
            try {
                executor.execute(event.jobId());
            } catch (Exception e) {
                log.error("提交知识异步任务执行失败，jobId={}", event.jobId(), e);
            }
        });
    }
}
