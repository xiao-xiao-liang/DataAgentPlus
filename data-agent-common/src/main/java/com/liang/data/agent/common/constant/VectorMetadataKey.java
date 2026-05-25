package com.liang.data.agent.common.constant;

import lombok.NoArgsConstructor;

/**
 * 向量文档元数据 Key 常量
 *
 * <p>定义写入/读取向量库时 Document.metadata 中使用的 key</p>
 */
@NoArgsConstructor
public final class VectorMetadataKey {

    // ==================== 通用 ====================

    /** 智能体 ID */
    public static final String AGENT_ID = "agent_id";

    /** 数据源 ID */
    public static final String DATASOURCE_ID = "datasource_id";

    /** 文档类型 (TABLE / COLUMN / KNOWLEDGE 等) */
    public static final String VECTOR_TYPE = "vector_type";

    // ==================== Schema 相关 ====================

    /** 表名 / 列名 */
    public static final String NAME = "name";

    /** 描述 / 注释 */
    public static final String DESCRIPTION = "description";

    /** 所属表名 (列文档专用) */
    public static final String TABLE_NAME = "tableName";

    /** 数据类型, 如 VARCHAR, INT (列文档专用) */
    public static final String TYPE = "type";

    /** 主键 (逗号分隔) */
    public static final String PRIMARY_KEY = "primaryKey";

    /** 外键关系 (顿号分隔) */
    public static final String FOREIGN_KEY = "foreignKey";

    /** 采样数据 (逗号分隔) */
    public static final String SAMPLES = "samples";

    // ==================== 检索占位 ====================

    /** 元数据过滤检索时使用的占位查询 (非语义检索场景) */
    public static final String DEFAULT_QUERY = "default";
}
