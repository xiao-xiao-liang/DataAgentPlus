package com.liang.data.agent.service.knowledge.chunk.mq;

/**
 * 知识分块异步任务消息。
 *
 * @param agentId        智能体 ID
 * @param knowledgeId    知识文件 ID
 * @param chunkId        分块业务 ID
 * @param contentVersion 正文版本
 */
public record KnowledgeChunkMessage(
        Integer agentId,
        Integer knowledgeId,
        String chunkId,
        Integer contentVersion) {
}
