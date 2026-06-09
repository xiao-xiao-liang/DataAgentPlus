package com.liang.data.agent.service.knowledge.job.mq;

/**
 * 知识文档任务消息队列常量。
 */
public final class KnowledgeJobMqConstant {

    public static final String TOPIC = "data-agent-knowledge-job";
    public static final String TAG_EXECUTE = "EXECUTE";
    public static final String EXECUTE_DESTINATION = TOPIC + ":" + TAG_EXECUTE;
    public static final String EXECUTE_CONSUMER_GROUP = "data-agent-knowledge-job-consumer";

    private KnowledgeJobMqConstant() {
    }
}
