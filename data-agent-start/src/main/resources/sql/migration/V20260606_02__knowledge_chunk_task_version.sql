-- 增加向量任务版本和处理租约字段，用于隔离乱序消息和恢复超时任务。
ALTER TABLE agent_knowledge_chunk
    ADD COLUMN vector_task_version INT NOT NULL DEFAULT 1 COMMENT '当前向量化任务版本号' AFTER vector_version,
    ADD COLUMN vector_processing_started_at TIMESTAMP NULL COMMENT '当前向量化任务开始处理时间' AFTER vector_status,
    ADD INDEX idx_vector_pending_recovery (vector_status, update_time),
    ADD INDEX idx_vector_processing_recovery (vector_status, vector_processing_started_at);
