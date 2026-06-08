package com.liang.data.agent.service.knowledge.job;

/**
 * 知识文档任务异步发布接口。
 */
public interface KnowledgeJobAsyncPublisher {

    /**
     * 发布知识文档任务执行消息。
     *
     * @param jobId 知识文档任务 ID
     */
    void publish(Long jobId);
}
