package com.liang.data.agent.service.knowledge.chunk.mq;

/**
 * 知识分块异步任务消息。
 *
 * @param agentId        智能体 ID
 * @param knowledgeId    知识文件 ID
 * @param chunkId        分块业务 ID
 * @param contentVersion 正文版本
 * @param taskVersion    向量任务版本
 * @param operationId    操作 ID
 */
public record KnowledgeChunkMessage(
        Integer agentId,
        Integer knowledgeId,
        String chunkId,
        Integer contentVersion,
        Integer taskVersion,
        String operationId) {

    /**
     * 创建不区分任务版本的普通消息，当前仅用于 AI 命名。
     */
    public KnowledgeChunkMessage(Integer agentId, Integer knowledgeId, String chunkId, Integer contentVersion) {
        this(agentId, knowledgeId, chunkId, contentVersion, null, null);
    }
}
