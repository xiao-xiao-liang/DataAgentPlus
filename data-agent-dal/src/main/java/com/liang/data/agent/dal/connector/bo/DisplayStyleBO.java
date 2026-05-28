package com.liang.data.agent.dal.connector.bo;

/**
 * 图表展示配置
 *
 * <p>由 LLM 分析 SQL 结果数据后推荐的最佳图表类型和轴配置</p>
 *
 * @param type  图表类型 (table/column/bar/line/pie)
 * @param title 图表标题
 * @param x     X 轴字段名
 * @param y     Y 轴字段名（多个用逗号分隔）
 */
public record DisplayStyleBO(
        String type,
        String title,
        String x,
        String y
) {

    /**
     * 默认表格样式（兜底使用）
     */
    public static DisplayStyleBO tableDefault() {
        return new DisplayStyleBO("table", "查询结果", null, null);
    }
}
