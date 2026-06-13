package com.liang.data.agent.workflow.dto.node;

import com.liang.data.agent.common.schema.SchemaDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * SQL 生成输入参数
 *
 * <p>包含 Schema、证据、错误信息（重试场景）等</p>
 */
@Data
@Builder
@AllArgsConstructor
public class SqlGenerationDTO {

    /**
     * 用户查询
     */
    private String query;

    /**
     * 证据 (业务知识)
     */
    private String evidence;

    /**
     * Schema 信息
     */
    private SchemaDTO schemaInfo;

    /**
     * 当前智能体允许访问的物理表；空列表表示允许 Schema 中全部表。
     */
    private List<String> allowedTables;

    /**
     * 上一次生成的 SQL (重试场景)
     */
    private String sql;

    /**
     * 上一次执行的异常信息 (重试场景)
     */
    private String exceptionMessage;

    /**
     * 当前步骤的执行描述
     */
    private String executionDescription;

    /**
     * 数据库方言
     */
    private String dialect;
}
