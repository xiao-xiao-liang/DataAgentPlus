package com.liang.data.agent.service.knowledge.job.mq;

/**
 * 知识文档任务消息。
 *
 * @param jobId       知识文档任务 ID
 * @param operationId 消息操作 ID，用于排查重复投递
 */
public record KnowledgeJobMessage(Long jobId, String operationId) {
}
