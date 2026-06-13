package com.liang.data.agent.workflow.service.impl;

import static com.liang.data.agent.workflow.constants.WorkflowQueueConstants.QUEUED_AT_FIELD;
import static com.liang.data.agent.workflow.constants.WorkflowQueueConstants.SCORE_FIELD;
import static com.liang.data.agent.workflow.constants.WorkflowQueueConstants.USER_ID_FIELD;

import com.liang.data.agent.dal.entity.ChatWorkflowQueueEntity;
import com.liang.data.agent.workflow.config.WorkflowQueueProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 基于 Redis 的分析任务公平队列调度器。
 *
 * <p>使用 ZSet 保存等待队列，使用 String/Hash 保存全局和用户运行计数，DB 仍作为最终状态和审计来源。</p>
 */
@Slf4j
@Component
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(name = "chat.workflow.queue.redis.enabled", havingValue = "true")
public class RedisWorkflowQueueScheduler {

    private static final String CLAIM_RUNNABLE_SCRIPT = loadLuaScript("lua/workflow_queue_claim_runnable.lua");
    private static final String RELEASE_RUNNING_SCRIPT = loadLuaScript("lua/workflow_queue_release_running.lua");
    private static final String ROLLBACK_CLAIM_SCRIPT = loadLuaScript("lua/workflow_queue_rollback_claim.lua");

    private final StringRedisTemplate stringRedisTemplate;
    private final String waitingQueueKey;
    private final String globalRunningKey;
    private final String userRunningKey;
    private final String taskKeyPrefix;
    private final int maxUserRunning;
    private final int maxGlobalRunning;
    private final int scanWindow;

    public RedisWorkflowQueueScheduler(StringRedisTemplate stringRedisTemplate,
                                       WorkflowQueueProperties workflowQueueProperties) {
        WorkflowQueueProperties.RedisProperties redisProperties = workflowQueueProperties.getRedis();
        String keyPrefix = redisProperties.getKeyPrefix();
        this.stringRedisTemplate = stringRedisTemplate;
        this.waitingQueueKey = keyPrefix + ":waiting";
        this.globalRunningKey = keyPrefix + ":running:global";
        this.userRunningKey = keyPrefix + ":running:user";
        this.taskKeyPrefix = keyPrefix + ":task:";
        this.maxUserRunning = workflowQueueProperties.getMaxUserRunning();
        this.maxGlobalRunning = workflowQueueProperties.getMaxGlobalRunning();
        this.scanWindow = redisProperties.getScanWindow();
    }

    /**
     * 判断 Redis 调度器是否可用。
     *
     * @return 是否可用
     */
    public boolean isEnabled() {
        return true;
    }

    /**
     * 将等待任务写入 Redis 等待队列。
     *
     * @param entity 队列记录
     */
    public void enqueue(ChatWorkflowQueueEntity entity) {
        if (entity == null || entity.getQueueId() == null) {
            return;
        }
        double score = buildScore(entity.getQueuedAt(), entity.getId());
        Map<String, String> taskValues = new HashMap<>();
        taskValues.put(USER_ID_FIELD, String.valueOf(entity.getUserId()));
        taskValues.put(QUEUED_AT_FIELD, String.valueOf(toEpochMillis(entity.getQueuedAt())));
        taskValues.put(SCORE_FIELD, String.valueOf(score));

        // 1. 先写任务元数据，避免调度脚本读到队列成员却找不到任务详情。
        stringRedisTemplate.opsForHash().putAll(taskKey(entity.getQueueId()), taskValues);

        // 2. 再写等待 ZSet，score 使用入队时间和主键组合，保证位次稳定。
        stringRedisTemplate.opsForZSet().add(waitingQueueKey, entity.getQueueId(), score);
        log.info("写入 Redis 分析等待队列，队列ID：{}，用户ID：{}", entity.getQueueId(), entity.getUserId());
    }

    /**
     * 原子 claim 本轮可运行的公平批次。
     *
     * @return 已获得运行资格的队列 ID
     */
    public List<String> claimRunnableQueueIds() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>(CLAIM_RUNNABLE_SCRIPT, List.class);
        List<?> result = stringRedisTemplate.execute(
                script,
                List.of(waitingQueueKey, globalRunningKey, userRunningKey, taskKeyPrefix),
                String.valueOf(maxGlobalRunning),
                String.valueOf(maxUserRunning),
                String.valueOf(scanWindow)
        );
        if (result.isEmpty()) {
            return List.of();
        }
        return result.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    /**
     * 释放运行中的 Redis 计数并清理任务元数据。
     *
     * @param queueId 队列任务 ID
     */
    public void finishRunning(String queueId) {
        if (queueId == null) {
            return;
        }
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_RUNNING_SCRIPT, Long.class);
        stringRedisTemplate.execute(
                script,
                List.of(waitingQueueKey, globalRunningKey, userRunningKey, taskKey(queueId)),
                queueId
        );
        log.info("释放 Redis 分析运行计数，队列ID：{}", queueId);
    }

    /**
     * 移除仍在等待的 Redis 队列任务。
     *
     * @param queueId 队列任务 ID
     */
    public void removeWaiting(String queueId) {
        if (queueId == null) {
            return;
        }
        stringRedisTemplate.opsForZSet().remove(waitingQueueKey, queueId);
        stringRedisTemplate.delete(taskKey(queueId));
        log.info("移除 Redis 分析等待任务，队列ID：{}", queueId);
    }

    /**
     * 当 DB 推进失败时回滚 Redis claim。
     *
     * @param queueId 队列任务 ID
     */
    public void rollbackClaim(String queueId) {
        if (queueId == null) {
            return;
        }
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ROLLBACK_CLAIM_SCRIPT, Long.class);
        stringRedisTemplate.execute(
                script,
                List.of(waitingQueueKey, globalRunningKey, userRunningKey, taskKey(queueId)),
                queueId
        );
        log.warn("回滚 Redis 分析队列 claim，队列ID：{}", queueId);
    }

    /**
     * 根据 DB 状态重建 Redis 等待队列和运行计数。
     *
     * @param waitingQueues DB 中等待中的队列任务
     * @param runningQueues DB 中运行中的队列任务
     */
    public void recover(List<ChatWorkflowQueueEntity> waitingQueues, List<ChatWorkflowQueueEntity> runningQueues) {
        List<ChatWorkflowQueueEntity> safeWaitingQueues = waitingQueues == null ? List.of() : waitingQueues;
        List<ChatWorkflowQueueEntity> safeRunningQueues = runningQueues == null ? List.of() : runningQueues;

        // 1. 运行计数以 DB RUNNING 状态为准重建，修复实例异常退出导致的 Redis 计数漂移。
        stringRedisTemplate.delete(globalRunningKey);
        stringRedisTemplate.delete(userRunningKey);
        if (!safeRunningQueues.isEmpty()) {
            stringRedisTemplate.opsForValue().set(globalRunningKey, String.valueOf(safeRunningQueues.size()));
            Map<String, String> userRunningCounts = safeRunningQueues.stream()
                    .filter(queue -> queue.getUserId() != null)
                    .collect(Collectors.groupingBy(
                            queue -> String.valueOf(queue.getUserId()),
                            LinkedHashMap::new,
                            Collectors.collectingAndThen(Collectors.counting(), String::valueOf)
                    ));
            if (!userRunningCounts.isEmpty()) {
                stringRedisTemplate.opsForHash().putAll(userRunningKey, userRunningCounts);
            }
        }

        // 2. 等待队列按 DB WAITING 记录补偿写入 Redis，ZSet 写入天然幂等。
        for (ChatWorkflowQueueEntity waitingQueue : safeWaitingQueues) {
            enqueue(waitingQueue);
        }
        log.info("恢复 Redis 分析队列状态，等待任务数：{}，运行任务数：{}", safeWaitingQueues.size(), safeRunningQueues.size());
    }

    private String taskKey(String queueId) {
        return taskKeyPrefix + queueId;
    }

    private double buildScore(LocalDateTime queuedAt, Long id) {
        long epochMillis = toEpochMillis(queuedAt);
        long idPart = id == null ? 0L : Math.floorMod(id, 1_000_000L);
        return epochMillis + idPart / 1_000_000D;
    }

    private long toEpochMillis(LocalDateTime queuedAt) {
        LocalDateTime time = queuedAt != null ? queuedAt : LocalDateTime.now();
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String loadLuaScript(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("读取 Redis 队列 Lua 脚本失败：" + path, ex);
        }
    }
}
