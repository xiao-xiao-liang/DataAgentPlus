-- 工作流运行记录增加运行与追踪标识。
-- 执行前需由运维确认目标库尚未添加这些列和索引；若环境已有字段或索引，不要重复执行。

ALTER TABLE chat_workflow_run
    ADD COLUMN run_id VARCHAR(36) DEFAULT NULL COMMENT '单次工作流运行ID',
    ADD COLUMN trace_id VARCHAR(32) DEFAULT NULL COMMENT 'OpenTelemetry追踪ID',
    ADD COLUMN start_time TIMESTAMP NULL DEFAULT NULL COMMENT '开始时间',
    ADD COLUMN end_time TIMESTAMP NULL DEFAULT NULL COMMENT '结束时间',
    ADD COLUMN duration_ms BIGINT DEFAULT NULL COMMENT '运行耗时毫秒',
    ADD COLUMN failed_node_name VARCHAR(128) DEFAULT NULL COMMENT '失败节点名称',
    ADD UNIQUE KEY uk_run_id (run_id),
    ADD INDEX idx_trace_id (trace_id);
