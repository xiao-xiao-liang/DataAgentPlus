package com.liang.data.agent.service.knowledge.chunk;

import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkTransactionOperation;

/**
 * 知识分块异步任务发布端口。
 *
 * <p>隔离分块业务服务与具体消息中间件实现。</p>
 */
public interface KnowledgeChunkAsyncPublisher {

    /**
     * 使用事务消息提交向量任务状态变更。
     */
    boolean publishVectorizeTransaction(KnowledgeChunkTransactionOperation operation);

    /**
     * 提交当前分块向量化消息。
     *
     * @param agentId 智能体 ID
     * @param knowledgeId 知识文档 ID
     * @param chunkId 分块 ID
     * @param contentVersion 正文版本
     * @param taskVersion 向量任务版本
     */
    boolean publishVectorize(Integer agentId, Integer knowledgeId, String chunkId,
                             Integer contentVersion, Integer taskVersion);

    /**
     * 提交普通 AI 命名消息。
     */
    boolean publishGenerateName(Integer agentId, Integer knowledgeId, String chunkId, Integer contentVersion);
}
