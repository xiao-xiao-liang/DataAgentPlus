CREATE TABLE IF NOT EXISTS datasource_table_columns
(
    id                  INT          NOT NULL AUTO_INCREMENT,
    agent_datasource_id INT          NOT NULL COMMENT '智能体数据源关联ID',
    table_name          VARCHAR(255) NOT NULL COMMENT '数据表名',
    column_name         VARCHAR(255) NOT NULL COMMENT '字段名',
    data_type           VARCHAR(255) DEFAULT NULL COMMENT '数据类型',
    column_comment      TEXT COMMENT '字段注释',
    is_nullable         TINYINT      DEFAULT 1 COMMENT '是否允许为空',
    is_primary_key      TINYINT      DEFAULT 0 COMMENT '是否为主键',
    is_analytic         TINYINT      DEFAULT 1 COMMENT '是否参与分析',
    create_time         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_binding_table_column (agent_datasource_id, table_name, column_name),
    INDEX idx_binding_table_analytic (agent_datasource_id, table_name, is_analytic)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '智能体数据源表字段配置';
