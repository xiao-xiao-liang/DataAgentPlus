package com.liang.data.agent.service.knowledge.chunk.mq;

import java.time.LocalDateTime;

/**
 * 知识分块事务消息本地操作。
 */
public record KnowledgeChunkTransactionOperation(
        Type type, Integer agentId, Integer knowledgeId, String chunkId,
        Integer expectedContentVersion, Integer expectedTaskVersion,
        String name, Integer nameLocked, String content,
        String expectedStatus, LocalDateTime deadline) {

    /**
     * 事务操作类型。
     */
    public enum Type {
        UPDATE_CONTENT,
        RETRY_FAILED,
        RECOVER_TIMEOUT
    }

    public Integer resultContentVersion() {
        return type == Type.UPDATE_CONTENT ? expectedContentVersion + 1 : expectedContentVersion;
    }

    public Integer resultTaskVersion() {
        return expectedTaskVersion + 1;
    }
}
