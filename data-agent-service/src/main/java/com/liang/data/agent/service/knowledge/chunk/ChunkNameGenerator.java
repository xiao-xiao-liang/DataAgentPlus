package com.liang.data.agent.service.knowledge.chunk;

/**
 * 知识分块名称生成器。
 */
public interface ChunkNameGenerator {

    String generate(String content, Integer chunkOrder);
}
