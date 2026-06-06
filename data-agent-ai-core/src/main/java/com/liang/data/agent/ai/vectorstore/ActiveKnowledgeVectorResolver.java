package com.liang.data.agent.ai.vectorstore;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 当前有效知识分块向量解析器。
 */
public interface ActiveKnowledgeVectorResolver {

    /**
     * 过滤掉已过期的知识分块向量。
     *
     * @param documents 向量检索结果
     * @return 当前有效结果
     */
    List<Document> retainActive(List<Document> documents);
}
