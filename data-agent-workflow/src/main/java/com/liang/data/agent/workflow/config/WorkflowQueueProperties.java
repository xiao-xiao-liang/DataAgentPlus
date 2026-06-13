package com.liang.data.agent.workflow.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 工作流队列配置属性。
 *
 * <p>绑定 chat.workflow.queue 前缀下的并发限制与 Redis 队列调度配置。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "chat.workflow.queue")
public class WorkflowQueueProperties {

    /**
     * 单个用户允许同时运行的最大任务数。
     */
    private int maxUserRunning = 2;

    /**
     * 全局允许同时运行的最大任务数。
     */
    private int maxGlobalRunning = 10;

    /**
     * Redis 队列调度配置。
     */
    private RedisProperties redis = new RedisProperties();

    /**
     * Redis 工作流队列配置属性。
     */
    @Getter
    @Setter
    public static class RedisProperties {

        /**
         * 是否启用 Redis 队列调度。
         */
        private boolean enabled = false;

        /**
         * Redis 队列相关 Key 前缀。
         */
        private String keyPrefix = "data-agent:workflow:queue";

        /**
         * 单次扫描等待队列的窗口大小。
         */
        private int scanWindow = 100;

        /**
         * 恢复任务单次扫描的最大记录数。
         */
        private int recoveryBatchSize = 500;

        /**
         * 恢复任务执行间隔。
         */
        private Duration recoveryInterval = Duration.ofMinutes(1);
    }
}
