package com.liang.data.agent.service.knowledge.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.liang.data.agent.dal.entity.AgentKnowledgeJobEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeJobMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体知识任务兜底调度器。
 *
 * <p>定时扫描未完成或待重试任务，补偿服务重启、事件丢失等情况下未执行的任务。</p>
 */
@Slf4j
@Component
public class AgentKnowledgeJobScheduler {

    private static final String JOB_STATUS_PENDING = "PENDING";
    private static final String JOB_STATUS_RETRYING = "RETRYING";
    private static final String JOB_STATUS_RUNNING = "RUNNING";

    private final AgentKnowledgeJobMapper agentKnowledgeJobMapper;
    private final AgentKnowledgeJobExecutor executor;
    private final TaskExecutor taskExecutor;

    /**
     * 创建智能体知识任务兜底调度器。
     *
     * @param agentKnowledgeJobMapper 知识任务 Mapper
     * @param executor 知识任务执行器
     * @param taskExecutor 应用异步任务线程池
     */
    public AgentKnowledgeJobScheduler(AgentKnowledgeJobMapper agentKnowledgeJobMapper,
                                      AgentKnowledgeJobExecutor executor,
                                      @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.agentKnowledgeJobMapper = agentKnowledgeJobMapper;
        this.executor = executor;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 扫描并执行到期的知识异步任务。
     */
    @Scheduled(fixedDelay = 30000L, initialDelay = 10000L)
    public void scanAndExecute() {
        LocalDateTime now = LocalDateTime.now();
        List<AgentKnowledgeJobEntity> jobs = agentKnowledgeJobMapper.selectList(new QueryWrapper<AgentKnowledgeJobEntity>()
                .and(wrapper -> wrapper
                        .in("status", List.of(JOB_STATUS_PENDING, JOB_STATUS_RETRYING))
                        .and(retryWrapper -> retryWrapper.isNull("next_retry_time")
                                .or()
                                .le("next_retry_time", now))
                        .or(recoverWrapper -> recoverWrapper
                                .eq("status", JOB_STATUS_RUNNING)
                                .le("locked_until", now)))
                .orderByAsc("update_time")
                .last("LIMIT 20"));
        for (AgentKnowledgeJobEntity job : jobs) {
            // 将耗时长任务异步派发到线程池，消除调度主线程的同步挂起问题
            taskExecutor.execute(() -> {
                try {
                    executor.execute(job.getId());
                } catch (Exception e) {
                    log.error("兜底异步执行知识任务失败，jobId={}", job.getId(), e);
                }
            });
        }
    }
}
