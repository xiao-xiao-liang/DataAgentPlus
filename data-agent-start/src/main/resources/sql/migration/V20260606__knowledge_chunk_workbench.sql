-- 为已存在的知识分块表增加编辑与向量版本字段。
ALTER TABLE agent_knowledge_chunk
    ADD COLUMN name VARCHAR(255) NULL COMMENT '分块名称',
    ADD COLUMN name_locked TINYINT NOT NULL DEFAULT 0 COMMENT '名称是否锁定：0-未锁定，1-已锁定',
    ADD COLUMN content_version INT NOT NULL DEFAULT 1 COMMENT '内容版本号',
    ADD COLUMN vector_version INT NULL COMMENT '向量版本号',
    ADD COLUMN vector_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '向量同步状态',
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT '向量同步重试次数',
    ADD INDEX idx_knowledge_vector_status (knowledge_id, vector_status);

-- 根据旧向量状态初始化历史分块，保留已有向量的可检索版本。
UPDATE agent_knowledge_chunk
SET name = CONCAT('分块 #', chunk_order),
    name_locked = 0,
    content_version = 1,
    vector_version = CASE
        WHEN status = 'VECTOR_STORED' THEN 1
        ELSE NULL
    END,
    vector_status = CASE
        WHEN status = 'VECTOR_STORED' THEN 'SYNCED'
        WHEN status = 'FAILED' THEN 'FAILED'
        ELSE 'PENDING'
    END,
    retry_count = 0;
