-- ----------------------------
-- 模型网关调用主表
-- ----------------------------
CREATE TABLE IF NOT EXISTS model_gateway_invocation
(
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '调用记录主键',
    invocation_id VARCHAR(36)   NOT NULL COMMENT '模型网关调用ID',
    run_id        VARCHAR(36)   DEFAULT NULL COMMENT '工作流运行ID',
    trace_id      VARCHAR(32)   DEFAULT NULL COMMENT '链路追踪ID',
    session_id    VARCHAR(36)   DEFAULT NULL COMMENT '会话ID',
    user_id       BIGINT        DEFAULT NULL COMMENT '用户ID',
    agent_id      INT           DEFAULT NULL COMMENT '智能体ID',
    tenant_id     VARCHAR(64)   DEFAULT NULL COMMENT '租户ID',
    scene_code    VARCHAR(64)   NOT NULL COMMENT '调用场景编码',
    call_mode     VARCHAR(16)   NOT NULL COMMENT '调用模式',
    status        VARCHAR(16)   NOT NULL COMMENT '调用状态',
    provider      VARCHAR(64)   DEFAULT NULL COMMENT '模型厂商',
    model         VARCHAR(128)  DEFAULT NULL COMMENT '模型名称',
    start_time    TIMESTAMP     NOT NULL COMMENT '开始时间',
    end_time      TIMESTAMP     NULL DEFAULT NULL COMMENT '结束时间',
    duration_ms   BIGINT        DEFAULT NULL COMMENT '耗时毫秒数',
    input_tokens  BIGINT        NOT NULL DEFAULT 0 COMMENT '输入Token数',
    output_tokens BIGINT        NOT NULL DEFAULT 0 COMMENT '输出Token数',
    total_tokens  BIGINT        NOT NULL DEFAULT 0 COMMENT '总Token数',
    error_code    VARCHAR(32)   DEFAULT NULL COMMENT '错误码',
    error_message VARCHAR(512)  DEFAULT NULL COMMENT '错误摘要',
    create_time   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_invocation_id (invocation_id),
    INDEX idx_run_id (run_id),
    INDEX idx_trace_id (trace_id),
    INDEX idx_scene_status_time (scene_code, status, start_time),
    INDEX idx_provider_model_time (provider, model, start_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '模型网关调用主表';

-- ----------------------------
-- 模型网关调用尝试明细表
-- ----------------------------
CREATE TABLE IF NOT EXISTS model_gateway_attempt
(
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '尝试记录主键',
    attempt_id    VARCHAR(36)   NOT NULL COMMENT '模型网关尝试ID',
    invocation_id VARCHAR(36)   NOT NULL COMMENT '模型网关调用ID',
    attempt_no    INT           NOT NULL COMMENT '尝试序号',
    provider      VARCHAR(64)   NOT NULL COMMENT '模型厂商',
    model         VARCHAR(128)  NOT NULL COMMENT '模型名称',
    status        VARCHAR(16)   NOT NULL COMMENT '尝试状态',
    start_time    TIMESTAMP     NOT NULL COMMENT '开始时间',
    end_time      TIMESTAMP     NULL DEFAULT NULL COMMENT '结束时间',
    duration_ms   BIGINT        DEFAULT NULL COMMENT '耗时毫秒数',
    http_status   INT           DEFAULT NULL COMMENT 'HTTP状态码',
    error_code    VARCHAR(32)   DEFAULT NULL COMMENT '错误码',
    error_message VARCHAR(512)  DEFAULT NULL COMMENT '错误摘要',
    create_time   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_attempt_id (attempt_id),
    INDEX idx_invocation_id (invocation_id),
    INDEX idx_provider_model_time (provider, model, start_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '模型网关调用尝试明细表';
