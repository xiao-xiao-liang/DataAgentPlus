package com.liang.data.agent.service.knowledge.chunk;

/**
 * 知识分块异步任务发布端口。
 *
 * <p>隔离分块业务服务与具体消息中间件实现。</p>
 */
public interface KnowledgeChunkAsyncPublisher {

    boolean publishVectorize(Integer agentId, Integer knowledgeId, String chunkId, Integer contentVersion);

    boolean publishGenerateName(Integer agentId, Integer knowledgeId, String chunkId, Integer contentVersion);
}
