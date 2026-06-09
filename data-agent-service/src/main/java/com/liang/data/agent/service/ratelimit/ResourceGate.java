package com.liang.data.agent.service.ratelimit;

import com.liang.data.agent.common.ratelimit.ResourceType;

import java.time.Duration;

/**
 * 资源门控接口。
 *
 * <p>用于在执行高成本资源前申请许可，后续可替换为 Redis 或其他分布式实现。</p>
 */
public interface ResourceGate {

    /**
     * 尝试申请资源许可。
     *
     * @param resourceType 资源类型
     * @param ownerId      资源占用方标识
     * @param timeout      等待超时时间
     * @return 资源许可
     */
    ResourcePermit tryAcquire(ResourceType resourceType, String ownerId, Duration timeout);
}
