package com.liang.data.agent.service.ratelimit.impl;

import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.common.ratelimit.ResourceType;
import com.liang.data.agent.service.ratelimit.ResourceGate;
import com.liang.data.agent.service.ratelimit.ResourcePermit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * JVM 本地资源门控实现。
 *
 * <p>基于 {@link Semaphore} 控制单实例内资源并发，后续分布式部署时可替换为 Redis 实现。</p>
 */
@Slf4j
@Service
public class LocalResourceGate implements ResourceGate {

    private final Map<ResourceType, Semaphore> semaphoreMap = new EnumMap<>(ResourceType.class);

    /**
     * 根据系统配置创建本地资源门控。
     *
     * @param properties 系统配置属性
     */
    @Autowired
    public LocalResourceGate(DataAgentProperties properties) {
        this(properties.getResourceGate().getLimits());
    }

    /**
     * 根据资源上限创建本地资源门控。
     *
     * @param limits 资源并发上限
     */
    public LocalResourceGate(Map<ResourceType, Integer> limits) {
        for (ResourceType resourceType : ResourceType.values()) {
            int limit = Math.max(1, limits.getOrDefault(resourceType, defaultLimit(resourceType)));
            semaphoreMap.put(resourceType, new Semaphore(limit, true));
        }
    }

    @Override
    public ResourcePermit tryAcquire(ResourceType resourceType, String ownerId, Duration timeout) {
        Semaphore semaphore = semaphoreMap.get(resourceType);
        if (semaphore == null) {
            return ResourcePermit.rejected(resourceType, ownerId);
        }
        try {
            // 1. 根据超时时间申请资源许可
            boolean acquired = timeout == null || timeout.isZero() || timeout.isNegative()
                    ? semaphore.tryAcquire()
                    : semaphore.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("资源许可申请失败，资源类型：{}，占用方：{}", resourceType, ownerId);
                return ResourcePermit.rejected(resourceType, ownerId);
            }

            // 2. 返回可关闭许可，调用方关闭时释放资源
            return ResourcePermit.acquired(resourceType, ownerId, semaphore::release);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("资源许可申请被中断，资源类型：{}，占用方：{}", resourceType, ownerId);
            return ResourcePermit.rejected(resourceType, ownerId);
        }
    }

    private int defaultLimit(ResourceType resourceType) {
        return switch (resourceType) {
            case CHAT_WORKFLOW -> 10;
            case SSE_STREAM -> 50;
            case LLM_CALL -> 5;
            case SQL_EXECUTION -> 10;
            case PYTHON_EXECUTION -> 3;
            case KNOWLEDGE_JOB -> 3;
            case KNOWLEDGE_VECTOR -> 5;
        };
    }
}
