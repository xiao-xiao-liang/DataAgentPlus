-- 增加分析任务排队和临时用户标识字段。
CREATE TABLE IF NOT EXISTS chat_workflow_queue
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '队列记录主键',
    queue_id      VARCHAR(36)  NOT NULL COMMENT '队列任务ID',
    user_id       VARCHAR(64)  NOT NULL DEFAULT 'default-user' COMMENT '用户ID',
    session_id    VARCHAR(36)  NOT NULL COMMENT '会话ID',
    agent_id      INT          NOT NULL COMMENT '智能体ID',
    query         TEXT COMMENT '用户原始问题',
    status        VARCHAR(32)  NOT NULL COMMENT '队列状态：WAITING、RUNNING、COMPLETED、FAILED、CANCELLED',
    queue_scope   VARCHAR(64)  NOT NULL COMMENT '队列范围，例如 CHAT_WORKFLOW',
    queued_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '入队时间',
    started_at    TIMESTAMP    DEFAULT NULL COMMENT '开始运行时间',
    finished_at   TIMESTAMP    DEFAULT NULL COMMENT '结束时间',
    cancel_reason VARCHAR(255) DEFAULT NULL COMMENT '取消原因',
    create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_queue_id (queue_id),
    INDEX idx_scope_status_queue (queue_scope, status, queued_at, id),
    INDEX idx_user_status (user_id, status),
    INDEX idx_session_status (session_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '分析任务准入队列表';

ALTER TABLE chat_workflow_run
    ADD COLUMN user_id VARCHAR(64) NOT NULL DEFAULT 'default-user' COMMENT '用户ID' AFTER agent_id;

ALTER TABLE agent_knowledge_job
    ADD COLUMN user_id VARCHAR(64) NOT NULL DEFAULT 'default-user' COMMENT '用户ID' AFTER agent_id,
    ADD INDEX idx_agent_type_status (agent_id, job_type, status, id);
