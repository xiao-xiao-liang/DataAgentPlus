package com.liang.data.agent.service.knowledge.chunk;

/**
 * 知识分块业务约束。
 */
public final class KnowledgeChunkConstraint {

    public static final int MAX_NAME_LENGTH = 255;
    public static final int MAX_CONTENT_LENGTH = 200_000;
    public static final int MAX_ERROR_MESSAGE_LENGTH = 255;

    private KnowledgeChunkConstraint() {
    }
}
