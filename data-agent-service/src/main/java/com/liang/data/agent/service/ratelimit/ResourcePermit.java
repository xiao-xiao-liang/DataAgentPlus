package com.liang.data.agent.service.ratelimit;

import com.liang.data.agent.common.ratelimit.ResourceType;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 可关闭的资源许可。
 *
 * <p>调用方应使用 try-with-resources 或 finally 调用 {@link #close()}，确保资源占用被释放。</p>
 */
public final class ResourcePermit implements AutoCloseable {

    private final ResourceType resourceType;
    private final String ownerId;
    private final boolean acquired;
    private final Runnable releaseAction;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private ResourcePermit(ResourceType resourceType, String ownerId, boolean acquired, Runnable releaseAction) {
        this.resourceType = resourceType;
        this.ownerId = ownerId;
        this.acquired = acquired;
        this.releaseAction = releaseAction;
    }

    /**
     * 创建已获得的资源许可。
     *
     * @param resourceType  资源类型
     * @param ownerId       资源占用方标识
     * @param releaseAction 释放动作
     * @return 已获得的资源许可
     */
    public static ResourcePermit acquired(ResourceType resourceType, String ownerId, Runnable releaseAction) {
        return new ResourcePermit(resourceType, ownerId, true, releaseAction);
    }

    /**
     * 创建未获得的资源许可。
     *
     * @param resourceType 资源类型
     * @param ownerId      资源占用方标识
     * @return 未获得的资源许可
     */
    public static ResourcePermit rejected(ResourceType resourceType, String ownerId) {
        return new ResourcePermit(resourceType, ownerId, false, null);
    }

    /**
     * 判断是否成功获得资源。
     *
     * @return true 表示已获得资源
     */
    public boolean acquired() {
        return acquired;
    }

    /**
     * 获取资源类型。
     *
     * @return 资源类型
     */
    public ResourceType resourceType() {
        return resourceType;
    }

    /**
     * 获取资源占用方标识。
     *
     * @return 资源占用方标识
     */
    public String ownerId() {
        return ownerId;
    }

    @Override
    public void close() {
        if (!acquired || releaseAction == null) {
            return;
        }
        if (closed.compareAndSet(false, true)) {
            releaseAction.run();
        }
    }
}
