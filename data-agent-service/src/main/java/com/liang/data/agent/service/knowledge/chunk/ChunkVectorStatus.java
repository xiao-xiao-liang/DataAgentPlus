package com.liang.data.agent.service.knowledge.chunk;

/**
 * 集中定义知识分块向量同步状态。
 */
public final class ChunkVectorStatus {

    /** 等待向量同步。 */
    public static final String PENDING = "PENDING";

    /** 正在进行向量同步。 */
    public static final String PROCESSING = "PROCESSING";

    /** 向量同步完成。 */
    public static final String SYNCED = "SYNCED";

    /** 向量同步失败。 */
    public static final String FAILED = "FAILED";

    private ChunkVectorStatus() {
    }
}
