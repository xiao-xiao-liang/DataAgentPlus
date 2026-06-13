package com.liang.data.agent.workflow.constants;

import lombok.NoArgsConstructor;

/**
 * 工作流提示词格式常量。
 */
@NoArgsConstructor
public final class PromptConstants {

    /**
     * DisplayStyleBO 的 JSON 格式描述（用于 LLM 输出约束）
     */
    public static final String DISPLAY_STYLE_FORMAT = """
            {"type": "图表类型(table/column/bar/line/pie)", "title": "图表标题", "x": "X轴字段名", "y": "Y轴字段名"}
            """;
}
