-- ============================================================================
-- Liang Data Agent - 数据库建表脚本 (去除物理外键 & 移除外键检查版)
-- 基于 DataAgent 原始表结构，做了以下统一和改进：
--   1. 统一时间字段命名为 create_time / update_time
--   2. 统一逻辑删除字段为 del_flag (TINYINT, 0=未删除, 1=已删除)
--   3. 原 agent 表缺少 del_flag，补上
--   4. 原 datasource 表缺少 del_flag，补上
--   5. 原 agent_datasource/agent_preset_question 缺少 del_flag，补上
--   6. 原 chat_session 用 status='deleted' 模拟软删除，改为 del_flag 统一
--   7. 原表字符集/引号风格不统一，统一为 utf8mb4 + 无反引号
--   8. model_config 表：proxy 相关字段拆出为配置项更合理，但保持原设计兼容
--   9. 去除所有物理外键约束，改为逻辑外键，保留普通索引
--  10. 彻底移除 FOREIGN KEY_CHECKS 开关指令
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------
-- 1. 智能体表
-- ----------------------------
CREATE TABLE IF NOT EXISTS agent
(
    id              INT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255) NOT NULL COMMENT '智能体名称',
    description     TEXT COMMENT '智能体描述',
    avatar          TEXT COMMENT '头像URL',
    status          VARCHAR(50)  DEFAULT 'draft' COMMENT '状态：draft-待发布, published-已发布, offline-已下线',
    api_key         VARCHAR(255) DEFAULT NULL COMMENT '访问 API Key, 格式 sk-xxx',
    api_key_enabled TINYINT      DEFAULT 0 COMMENT 'API Key 是否启用：0-禁用, 1-启用',
    prompt          TEXT COMMENT '自定义 Prompt 配置',
    category        VARCHAR(100) COMMENT '分类',
    admin_id        BIGINT COMMENT '管理员ID',
    tags            TEXT COMMENT '标签, 逗号分隔',
    del_flag        TINYINT      DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_name (name),
    INDEX idx_status (status),
    INDEX idx_category (category)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '智能体表';

-- ----------------------------
-- 2. 数据源表
-- ----------------------------
CREATE TABLE IF NOT EXISTS datasource
(
    id             INT          NOT NULL AUTO_INCREMENT,
    name           VARCHAR(255) NOT NULL COMMENT '数据源名称',
    type           VARCHAR(50)  NOT NULL COMMENT '数据源类型：mysql, postgresql',
    host           VARCHAR(255) NOT NULL COMMENT '主机地址',
    port           INT          NOT NULL COMMENT '端口号',
    database_name  VARCHAR(255) NOT NULL COMMENT '数据库名称',
    username       VARCHAR(255) NOT NULL COMMENT '用户名',
    password       VARCHAR(255) NOT NULL COMMENT '密码（加密存储）',
    connection_url VARCHAR(1000) COMMENT '完整连接URL',
    status         VARCHAR(50) DEFAULT 'inactive' COMMENT '状态：active-启用, inactive-禁用',
    test_status    VARCHAR(50) DEFAULT 'unknown' COMMENT '连接测试状态：success, failed, unknown',
    description    TEXT COMMENT '描述',
    creator_id     BIGINT COMMENT '创建者ID',
    del_flag       TINYINT     DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
    create_time    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_name (name),
    INDEX idx_type (type),
    INDEX idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '数据源表';

-- ----------------------------
-- 3. 智能体数据源关联表
-- ----------------------------
CREATE TABLE IF NOT EXISTS agent_datasource
(
    id               INT NOT NULL AUTO_INCREMENT,
    agent_id         INT NOT NULL COMMENT '智能体ID',
    datasource_id    INT NOT NULL COMMENT '数据源ID',
    is_active        TINYINT     DEFAULT 0 COMMENT '是否启用：0-禁用, 1-启用',
    schema_status    VARCHAR(50) DEFAULT 'pending' COMMENT 'Schema同步状态：pending / syncing / success / failed',
    embedding_status VARCHAR(50) DEFAULT 'pending' COMMENT '向量化状态：pending / vectorizing / success / failed',
    last_sync_time   TIMESTAMP   DEFAULT NULL COMMENT '最后同步时间',
    del_flag         TINYINT     DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
    create_time      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_datasource (agent_id, datasource_id),
    INDEX idx_agent_id (agent_id),
    INDEX idx_datasource_id (datasource_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '智能体数据源关联表';

-- ----------------------------
-- 4. 智能体数据源选中表
-- ----------------------------
CREATE TABLE IF NOT EXISTS agent_datasource_tables
(
    id                  INT          NOT NULL AUTO_INCREMENT,
    agent_datasource_id INT          NOT NULL COMMENT '智能体数据源ID',
    table_name          VARCHAR(255) NOT NULL COMMENT '数据表名',
    create_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_datasource_table (agent_datasource_id, table_name),
    INDEX idx_agent_datasource_id (agent_datasource_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '某个智能体某个数据源所选中的数据表';

-- ----------------------------
-- 5. 逻辑外键配置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS logical_relation
(
    id                 INT          NOT NULL AUTO_INCREMENT,
    datasource_id      INT          NOT NULL COMMENT '关联的数据源ID',
    source_table_name  VARCHAR(100) NOT NULL COMMENT '主表名 (例如 t_order)',
    source_column_name VARCHAR(100) NOT NULL COMMENT '主表字段名 (例如 buyer_uid)',
    target_table_name  VARCHAR(100) NOT NULL COMMENT '关联表名 (例如 t_user)',
    target_column_name VARCHAR(100) NOT NULL COMMENT '关联表字段名 (例如 id)',
    relation_type      VARCHAR(20)  DEFAULT NULL COMMENT '关系类型: 1:1, 1:N, N:1',
    description        VARCHAR(500) DEFAULT NULL COMMENT '业务描述: 帮助 LLM 理解关联关系',
    del_flag           TINYINT      DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
    create_time        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_datasource_id (datasource_id),
    INDEX idx_source_table (datasource_id, source_table_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '逻辑外键配置表';

-- ----------------------------
-- 6. 业务知识表
-- ----------------------------
CREATE TABLE IF NOT EXISTS business_knowledge
(
    id               INT          NOT NULL AUTO_INCREMENT,
    agent_id         INT          NOT NULL COMMENT '关联的智能体ID',
    business_term    VARCHAR(255) NOT NULL COMMENT '业务名词',
    description      TEXT COMMENT '描述',
    synonyms         TEXT COMMENT '同义词, 逗号分隔',
    is_recall        INT          DEFAULT 1 COMMENT '是否召回：0-不召回, 1-召回',
    embedding_status VARCHAR(20)  DEFAULT NULL COMMENT '向量化状态：PENDING, PROCESSING, COMPLETED, FAILED',
    error_msg        VARCHAR(255) DEFAULT NULL COMMENT '操作失败的错误信息',
    del_flag         TINYINT      DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
    create_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_business_term (business_term),
    INDEX idx_agent_id (agent_id),
    INDEX idx_embedding_status (embedding_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '业务知识表';

-- ----------------------------
-- 7. 智能体知识表
-- ----------------------------
CREATE TABLE IF NOT EXISTS agent_knowledge
(
    id                  INT          NOT NULL AUTO_INCREMENT,
    agent_id            INT          NOT NULL COMMENT '关联的智能体ID',
    title               VARCHAR(255) NOT NULL COMMENT '知识标题',
    type                VARCHAR(50)  NOT NULL COMMENT '知识类型: DOCUMENT-文档, QA-问答, FAQ-常见问题',
    question            TEXT COMMENT '问题 (仅当 type 为 QA/FAQ 时使用)',
    content             MEDIUMTEXT COMMENT '知识内容 (对于 QA/FAQ 是答案; DOCUMENT 通常为空)',
    is_recall           INT          DEFAULT 1 COMMENT '是否召回: 1=召回, 0=不召回',
    embedding_status    VARCHAR(20)  DEFAULT NULL COMMENT '向量化状态：PENDING, PROCESSING, COMPLETED, FAILED',
    error_msg           VARCHAR(255) DEFAULT NULL COMMENT '操作失败的错误信息',
    source_filename     VARCHAR(500) DEFAULT NULL COMMENT '上传时的原始文件名',
    file_path           VARCHAR(500) DEFAULT NULL COMMENT '文件在服务器上的物理存储路径',
    file_size           BIGINT       DEFAULT NULL COMMENT '文件大小 (字节)',
    file_type           VARCHAR(255) DEFAULT NULL COMMENT '文件类型 (pdf, md, doc 等)',
    splitter_type       VARCHAR(50)  DEFAULT 'token' COMMENT '分块策略: token, recursive, sentence, semantic',
    is_resource_cleaned INT          DEFAULT 0 COMMENT '0=物理资源未清理, 1=已清理',
    del_flag            TINYINT      DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
    create_time         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_agent_id_recall (agent_id, is_recall),
    INDEX idx_embedding_status (embedding_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '智能体知识源管理表 (支持文档、QA、FAQ)';

-- ----------------------------
-- 7.1. 智能体知识分块表
-- ----------------------------
CREATE TABLE IF NOT EXISTS agent_knowledge_chunk
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    knowledge_id   INT          NOT NULL COMMENT '所属知识源ID',
    chunk_id       VARCHAR(255) NOT NULL COMMENT '分块业务ID',
    chunk_order    INT          NOT NULL COMMENT '分块顺序，从0开始',
    content        LONGTEXT     NOT NULL COMMENT '分块文本内容',
    content_length INT          DEFAULT NULL COMMENT '分块文本长度',
    metadata       TEXT         DEFAULT NULL COMMENT '分块元数据JSON',
    embedding_id   VARCHAR(255) DEFAULT NULL COMMENT '向量存储中的文档ID',
    splitter_type  VARCHAR(50)  DEFAULT NULL COMMENT '分块策略',
    status         VARCHAR(32)  DEFAULT NULL COMMENT '状态：SKIP_EMBEDDING, VECTOR_STORED, FAILED',
    skip_embedding TINYINT      DEFAULT 0 COMMENT '是否跳过向量化：0-不跳过, 1-跳过',
    error_msg      VARCHAR(255) DEFAULT NULL COMMENT '操作失败的错误信息',
    del_flag       TINYINT      DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
    create_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chunk_id (chunk_id),
    INDEX idx_knowledge_id (knowledge_id),
    INDEX idx_knowledge_order (knowledge_id, chunk_order),
    INDEX idx_status_skip (knowledge_id, status, skip_embedding)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '智能体知识分块表';

-- ----------------------------
-- 8. 语义模型表
-- ----------------------------
CREATE TABLE IF NOT EXISTS semantic_model
(
    id                   INT          NOT NULL AUTO_INCREMENT,
    agent_id             INT          NOT NULL COMMENT '关联的智能体ID',
    datasource_id        INT          NOT NULL COMMENT '关联的数据源ID',
    table_name           VARCHAR(255) NOT NULL COMMENT '关联的表名',
    column_name          VARCHAR(255) NOT NULL DEFAULT '' COMMENT '物理字段名 (例如: csat_score)',
    business_name        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '业务名/别名 (例如: 客户满意度分数)',
    synonyms             TEXT COMMENT '同义词 (例如: 满意度, 客户评分)',
    business_description TEXT COMMENT '业务描述 (帮助 LLM 理解字段含义)',
    column_comment       VARCHAR(255)          DEFAULT NULL COMMENT '物理字段的原始注释',
    data_type            VARCHAR(255) NOT NULL DEFAULT '' COMMENT '物理数据类型 (例如: int, varchar(20))',
    status               TINYINT      NOT NULL DEFAULT 1 COMMENT '0=停用, 1=启用',
    create_time          TIMESTAMP             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          TIMESTAMP             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_agent_id (agent_id),
    INDEX idx_business_name (business_name),
    INDEX idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '语义模型表';

-- ----------------------------
-- 知识候选表
-- ----------------------------
CREATE TABLE IF NOT EXISTS knowledge_candidate
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    agent_id               INT          NOT NULL COMMENT '关联智能体ID',
    datasource_id          INT           DEFAULT NULL COMMENT '关联数据源ID',
    session_id             VARCHAR(64)   DEFAULT NULL COMMENT '会话ID',
    thread_id              VARCHAR(64)   DEFAULT NULL COMMENT 'StateGraph线程ID',
    source_question        TEXT         NOT NULL COMMENT '触发候选知识的原始问题',
    clarification_question TEXT          DEFAULT NULL COMMENT '系统提出的澄清问题',
    user_answer            TEXT          DEFAULT NULL COMMENT '用户澄清回答',
    normalized_content     JSON         NOT NULL COMMENT '结构化候选知识内容',
    candidate_type         VARCHAR(64)  NOT NULL COMMENT '候选类型',
    title                  VARCHAR(255) NOT NULL COMMENT '候选知识标题',
    scope                  VARCHAR(32)  NOT NULL COMMENT '作用域',
    status                 VARCHAR(32)  NOT NULL COMMENT '状态',
    confidence_score       DECIMAL(5, 4) DEFAULT NULL COMMENT '模型归纳置信度',
    reviewer_id            BIGINT        DEFAULT NULL COMMENT '审核人ID',
    review_comment         TEXT          DEFAULT NULL COMMENT '审核意见',
    published_target_type  VARCHAR(64)   DEFAULT NULL COMMENT '发布目标类型',
    published_target_id    BIGINT        DEFAULT NULL COMMENT '发布目标ID',
    del_flag               TINYINT       DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    create_time            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_agent_status (agent_id, status),
    INDEX idx_thread_id (thread_id),
    INDEX idx_candidate_type (candidate_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '知识沉淀候选表';

-- ----------------------------
-- 9. 智能体预设问题表
-- ----------------------------
CREATE TABLE IF NOT EXISTS agent_preset_question
(
    id          INT  NOT NULL AUTO_INCREMENT,
    agent_id    INT  NOT NULL COMMENT '智能体ID',
    question    TEXT NOT NULL COMMENT '预设问题内容',
    sort_order  INT       DEFAULT 0 COMMENT '排序顺序',
    is_active   TINYINT   DEFAULT 0 COMMENT '是否启用：0-禁用, 1-启用',
    del_flag    TINYINT   DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_agent_id (agent_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '智能体预设问题表';

-- ----------------------------
-- 10. 会话表
-- ----------------------------
CREATE TABLE IF NOT EXISTS chat_session
(
    id          VARCHAR(36) NOT NULL COMMENT '会话ID (UUID)',
    agent_id    INT         NOT NULL COMMENT '智能体ID',
    title       VARCHAR(255) DEFAULT '新对话' COMMENT '会话标题',
    status      VARCHAR(50)  DEFAULT 'active' COMMENT '状态：active-活跃, archived-归档',
    is_pinned   TINYINT      DEFAULT 0 COMMENT '是否置顶：0-否, 1-是',
    user_id     BIGINT COMMENT '用户ID',
    del_flag    TINYINT      DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_agent_id (agent_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '聊天会话表';

-- ----------------------------
-- 11. 消息表
-- ----------------------------
CREATE TABLE IF NOT EXISTS chat_message
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    session_id   VARCHAR(36) NOT NULL COMMENT '会话ID',
    role         VARCHAR(20) NOT NULL COMMENT '角色：user, assistant, system',
    content      TEXT        NOT NULL COMMENT '消息内容',
    message_type VARCHAR(50) DEFAULT 'text' COMMENT '消息类型：text, sql, result, error',
    metadata     JSON COMMENT '元数据 (JSON)',
    create_time  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_session_id (session_id),
    INDEX idx_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '聊天消息表';

-- ----------------------------
-- 11.1. 工作流运行快照表
-- ----------------------------
CREATE TABLE IF NOT EXISTS chat_workflow_run
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '运行记录主键',
    session_id          VARCHAR(36)  NOT NULL COMMENT '会话ID',
    agent_id            INT          DEFAULT NULL COMMENT '智能体ID',
    query               TEXT COMMENT '用户原始问题',
    status              VARCHAR(32)  NOT NULL DEFAULT 'running' COMMENT '运行状态：running、interrupted、completed、failed',
    last_node_name      VARCHAR(128) DEFAULT NULL COMMENT '最近完成的节点名称',
    next_node_name      VARCHAR(128) DEFAULT NULL COMMENT '下一节点名称',
    checkpoint_id       VARCHAR(64)  DEFAULT NULL COMMENT '图框架checkpoint ID',
    state_snapshot      JSON COMMENT '最近一次图状态快照',
    accumulated_content MEDIUMTEXT COMMENT '当前已累计输出内容',
    interrupt_reason    VARCHAR(512) DEFAULT NULL COMMENT '中断或失败原因',
    create_time         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_session_id (session_id),
    INDEX idx_status (status),
    INDEX idx_update_time (update_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '工作流运行快照表';

-- ----------------------------
-- 12. 用户 Prompt 配置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS user_prompt_config
(
    id            VARCHAR(36)  NOT NULL COMMENT '配置ID (UUID)',
    name          VARCHAR(255) NOT NULL COMMENT '配置名称',
    prompt_type   VARCHAR(100) NOT NULL COMMENT 'Prompt 类型 (如 report-generator, planner 等)',
    agent_id      INT       DEFAULT NULL COMMENT '关联的智能体ID, 为空表示全局配置',
    system_prompt TEXT         NOT NULL COMMENT '用户自定义系统 Prompt 内容',
    enabled       TINYINT   DEFAULT 1 COMMENT '是否启用：0-禁用, 1-启用',
    description   TEXT COMMENT '配置描述',
    priority      INT       DEFAULT 0 COMMENT '优先级, 数字越大优先级越高',
    display_order INT       DEFAULT 0 COMMENT '显示顺序, 数字越小越靠前',
    creator       VARCHAR(255) COMMENT '创建者',
    del_flag      TINYINT   DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_prompt_type (prompt_type),
    INDEX idx_agent_id (agent_id),
    INDEX idx_enabled (enabled),
    INDEX idx_type_agent_priority (prompt_type, agent_id, enabled, priority DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '用户 Prompt 配置表';

-- ----------------------------
-- 13. 模型配置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS model_config
(
    id               INT          NOT NULL AUTO_INCREMENT,
    provider         VARCHAR(255) NOT NULL COMMENT '厂商标识 (方便前端展示)',
    base_url         VARCHAR(255) NOT NULL COMMENT '模型 Base URL',
    api_key          VARCHAR(255) NOT NULL COMMENT 'API 密钥',
    model_name       VARCHAR(255) NOT NULL COMMENT '模型名称',
    model_type       VARCHAR(20)  NOT NULL  DEFAULT 'CHAT' COMMENT '模型类型: CHAT / EMBEDDING',
    temperature      DECIMAL(4, 2) UNSIGNED DEFAULT 0.00 COMMENT '温度参数',
    max_tokens       INT                    DEFAULT 2000 COMMENT '最大输出 Token数',
    completions_path VARCHAR(255)           DEFAULT NULL COMMENT 'Chat 模型附加路径 (例如 /v1/chat/completions)',
    embeddings_path  VARCHAR(255)           DEFAULT NULL COMMENT 'Embedding 模型附加路径',
    is_active        TINYINT                DEFAULT 0 COMMENT '是否激活：0-禁用, 1-启用',
    proxy_enabled    TINYINT                DEFAULT 0 COMMENT '是否启用代理：0-禁用, 1-启用',
    proxy_host       VARCHAR(255)           DEFAULT NULL COMMENT '代理主机地址',
    proxy_port       INT                    DEFAULT NULL COMMENT '代理端口',
    proxy_username   VARCHAR(255)           DEFAULT NULL COMMENT '代理用户名（可选）',
    proxy_password   VARCHAR(255)           DEFAULT NULL COMMENT '代理密码（可选）',
    del_flag         TINYINT                DEFAULT 0 COMMENT '逻辑删除：0-未删除, 1-已删除',
    create_time      TIMESTAMP              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      TIMESTAMP              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_model_type (model_type),
    INDEX idx_is_active (is_active)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '模型配置表';
-- ----------------------------
-- 智能体知识异步任务表
-- ----------------------------
CREATE TABLE IF NOT EXISTS agent_knowledge_job
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    knowledge_id    INT          NOT NULL COMMENT '所属知识源ID',
    agent_id        INT          NOT NULL COMMENT '关联的智能体ID',
    job_type        VARCHAR(50)  NOT NULL COMMENT '任务类型：UPLOAD_VECTORIZE、DELETE_CLEANUP',
    status          VARCHAR(32)  NOT NULL COMMENT '任务状态：PENDING、RUNNING、RETRYING、SUCCESS、FAILED',
    retry_count     INT          DEFAULT 0 COMMENT '当前重试次数',
    max_retry_count INT          DEFAULT 3 COMMENT '最大重试次数',
    next_retry_time TIMESTAMP    DEFAULT NULL COMMENT '下次可重试时间',
    locked_by       VARCHAR(128) DEFAULT NULL COMMENT '锁持有者',
    locked_until    TIMESTAMP    DEFAULT NULL COMMENT '锁过期时间',
    error_msg       VARCHAR(500) DEFAULT NULL COMMENT '失败错误信息',
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_status_retry (status, next_retry_time),
    INDEX idx_knowledge_type (knowledge_id, job_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '智能体知识异步任务表';
