package com.liang.data.agent.workflow.dto.planner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 执行计划中的单个步骤
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStep {

    @JsonProperty("step")
    @JsonPropertyDescription("步骤顺序号")
    private int step;

    /**
     * 工具名称 (对应 StateKey 中的节点名: sql_generate / python_generate / report_generator)
     */
    @JsonProperty("tool_to_use")
    @JsonPropertyDescription("工具名称")
    private String toolToUse;

    /**
     * 工具参数
     */
    @JsonProperty("tool_parameters")
    @JsonPropertyDescription("工具参数")
    private ToolParameters toolParameters;

    /**
     * 工具参数 (根据 tool_to_use 类型使用不同字段)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolParameters {

        /**
         * 统一指令字段:
         * <ul>
         *   <li>sql_generate → 当前步骤的 SQL 需求描述</li>
         *   <li>python_generate → 当前步骤的编程需求描述</li>
         * </ul>
         */
        @JsonProperty("instruction")
        @JsonPropertyDescription("当 tool_to_use 是 sql_generate 时填 SQL 需求，是 python_generate 时填编程需求")
        private String instruction;

        /**
         * report_generator 专用: 报告大纲和建议方向
         */
        @JsonProperty("summary_and_recommendations")
        @JsonPropertyDescription("仅 report_generator 节点需要此字段，报告的大纲")
        private String summaryAndRecommendations;

        /**
         * 运行态字段: Planner 不会填写，sql_generate 运行完后会把生成的 SQL 塞进来
         */
        @JsonProperty("sql_query")
        @JsonPropertyDescription("sql_generate 运行完后，会把生成的 SQL 塞进来")
        private String sqlQuery;
    }
}
