package com.liang.data.agent.workflow.dto.node;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 语义一致性检查输入参数
 *
 * <p>将 SQL、Schema、用户查询等打包传给 Nl2SqlService</p>
 */
@Data
@Builder
@AllArgsConstructor
public class SemanticConsistencyDTO {

    /**
     * 数据库方言 (如 MySQL)
     */
    private String dialect;

    /**
     * 待检查的 SQL
     */
    private String sql;

    /**
     * 当前步骤的执行描述
     */
    private String executionDescription;

    /**
     * Schema 信息 (表结构的文本描述)
     */
    private String schemaInfo;

    /**
     * 用户原始查询
     */
    private String userQuery;

    /**
     * 证据 (业务知识)
     */
    private String evidence;
}
