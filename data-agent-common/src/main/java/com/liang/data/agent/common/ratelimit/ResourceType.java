package com.liang.data.agent.common.ratelimit;

/**
 * 系统受控资源类型。
 *
 * <p>用于统一描述分析链路、模型调用、SQL 执行、Python 执行和知识库任务等高成本资源。</p>
 */
public enum ResourceType {

    /**
     * 分析工作流运行资源。
     */
    CHAT_WORKFLOW,

    /**
     * SSE 流式连接资源。
     */
    SSE_STREAM,

    /**
     * 大模型调用资源。
     */
    LLM_CALL,

    /**
     * SQL 执行资源。
     */
    SQL_EXECUTION,

    /**
     * Python 执行资源。
     */
    PYTHON_EXECUTION,

    /**
     * 知识文档任务资源。
     */
    KNOWLEDGE_JOB,

    /**
     * 知识向量写入资源。
     */
    KNOWLEDGE_VECTOR
}
