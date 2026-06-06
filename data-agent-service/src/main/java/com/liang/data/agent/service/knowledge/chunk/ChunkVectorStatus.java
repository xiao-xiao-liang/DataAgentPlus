package com.liang.data.agent.service.knowledge.chunk;

/**
 * 知识分块向量同步状态。
 */
public enum ChunkVectorStatus {
    PENDING,
    PROCESSING,
    SYNCED,
    FAILED;

    /**
     * 获取持久化状态码。
     *
     * @return 状态码
     */
    public String getCode() {
        return name();
    }
}
