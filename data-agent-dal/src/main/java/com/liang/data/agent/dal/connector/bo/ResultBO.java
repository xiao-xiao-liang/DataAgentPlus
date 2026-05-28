package com.liang.data.agent.dal.connector.bo;

/**
 * SQL 执行完整结果（结果集 + 展示配置）
 *
 * <p>将 SQL 查询的物理数据行与 LLM 推荐的可视化配置组合在一起，
 * 供前端一次性获取数据和渲染方式</p>
 *
 * @param resultSet    SQL 查询的原始结果集（列名 + 数据行）
 * @param displayStyle LLM 推荐的图表展示配置（可能为 null 时前端降级为表格）
 */
public record ResultBO(
        ResultSetBO resultSet,
        DisplayStyleBO displayStyle
) {
}
