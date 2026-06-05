package com.liang.data.agent.common.constant;

import lombok.NoArgsConstructor;

/**
 * 知识库及解析任务常量类。
 *
 * @author 资深Java架构师
 */
@NoArgsConstructor
public final class KnowledgeConstant {

    /**
     * 知识库状态：待处理
     */
    public static final String KNOWLEDGE_STATUS_PENDING = "PENDING";

    /**
     * 知识库状态：删除中
     */
    public static final String KNOWLEDGE_STATUS_DELETING = "DELETING";

    /**
     * 异步任务类型：上传并向量化
     */
    public static final String JOB_TYPE_UPLOAD_VECTORIZE = "UPLOAD_VECTORIZE";

    /**
     * 异步任务类型：删除与清理
     */
    public static final String JOB_TYPE_DELETE_CLEANUP = "DELETE_CLEANUP";

    /**
     * 异步任务状态：待处理
     */
    public static final String JOB_STATUS_PENDING = "PENDING";

    /**
     * 异步任务默认最大重试次数
     */
    public static final int DEFAULT_MAX_RETRY_COUNT = 3;

    /**
     * 知识库文件存储目录/前缀
     */
    public static final String STORAGE_PREFIX_KNOWLEDGE = "knowledge";

    /**
     * 异步任务状态：进行中
     */
    public static final String JOB_STATUS_RUNNING = "RUNNING";

    /**
     * 异步任务状态：成功
     */
    public static final String JOB_STATUS_SUCCESS = "SUCCESS";

    /**
     * 异步任务状态：重试中
     */
    public static final String JOB_STATUS_RETRYING = "RETRYING";

    /**
     * 异步任务状态：失败
     */
    public static final String JOB_STATUS_FAILED = "FAILED";

    /**
     * 知识库状态：处理中
     */
    public static final String KNOWLEDGE_STATUS_PROCESSING = "PROCESSING";

    /**
     * 知识库状态：已完成
     */
    public static final String KNOWLEDGE_STATUS_COMPLETED = "COMPLETED";

    /**
     * 知识库状态：失败
     */
    public static final String KNOWLEDGE_STATUS_FAILED = "FAILED";

    /**
     * 知识库状态：删除失败
     */
    public static final String KNOWLEDGE_STATUS_DELETE_FAILED = "DELETE_FAILED";

    /**
     * 分块状态：跳过向量化
     */
    public static final String CHUNK_STATUS_SKIP_EMBEDDING = "SKIP_EMBEDDING";

    /**
     * 分块状态：已写入向量库
     */
    public static final String CHUNK_STATUS_VECTOR_STORED = "VECTOR_STORED";
}
