package com.liang.data.agent.service.knowledge.chunk.mq;

/**
 * 知识分块消息队列常量。
 */
public final class KnowledgeChunkMqConstant {
    public static final String TOPIC = "data-agent-knowledge-chunk";
    public static final String TAG_VECTORIZE = "VECTORIZE";
    public static final String TAG_GENERATE_NAME = "GENERATE_NAME";
    public static final String VECTORIZE_DESTINATION = TOPIC + ":" + TAG_VECTORIZE;
    public static final String GENERATE_NAME_DESTINATION = TOPIC + ":" + TAG_GENERATE_NAME;
    public static final String VECTOR_CONSUMER_GROUP = "data-agent-knowledge-chunk-vector-consumer";
    public static final String NAME_CONSUMER_GROUP = "data-agent-knowledge-chunk-name-consumer";
    public static final String VECTOR_DEAD_LETTER_TOPIC = "%DLQ%" + VECTOR_CONSUMER_GROUP;
    public static final String VECTOR_DEAD_LETTER_CONSUMER_GROUP = "data-agent-knowledge-chunk-vector-dead-letter-consumer";
    public static final String HEADER_CHUNK_ID = "chunkId";
    public static final String HEADER_CONTENT_VERSION = "contentVersion";
    public static final String HEADER_TASK_VERSION = "taskVersion";

    private KnowledgeChunkMqConstant() {
    }
}
