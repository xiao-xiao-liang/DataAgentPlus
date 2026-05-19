package com.liang.data.agent.workflow.dto.planner;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.common.constant.StateKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static com.liang.data.agent.common.constant.StateKey.SQL_GENERATE_NODE;

/**
 * LLM 生成的执行计划
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    /**
     * LLM 的分析思路 (说明检查了哪些表和字段)
     */
    @JsonProperty("thought_process")
    @JsonPropertyDescription("简要描述你的分析思路。必须明确提到你检查了哪些表和字段")
    private String thoughtProcess;

    /**
     * 执行步骤列表
     */
    @JsonProperty("execution_plan")
    @JsonPropertyDescription("执行计划的步骤列表")
    private List<ExecutionStep> executionPlan;

    /**
     * NL2SQL 快捷计划 (仅走 SQL 生成，跳过 Python 和 Report)
     */
    private static final String NL2SQL_PLAN_JSON;

    static {
        ExecutionStep.ToolParameters parameters = new ExecutionStep.ToolParameters();
        parameters.setInstruction("SQL生成");

        ExecutionStep step = ExecutionStep.builder()
                .step(1)
                .toolToUse(SQL_GENERATE_NODE)
                .toolParameters(parameters)
                .build();

        Plan plan = Plan.builder()
                .thoughtProcess("根据问题生成SQL")
                .executionPlan(List.of(step))
                .build();

        try {
            NL2SQL_PLAN_JSON = new ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(plan);
        } catch (JsonProcessingException e) {
            throw new ExceptionInInitializerError("NL2SQL Plan JSON 序列化失败: " + e.getMessage());
        }
    }

    /**
     * 获取 NL2SQL 模式的固定计划 JSON
     */
    public static String nl2SqlPlan() {
        return NL2SQL_PLAN_JSON;
    }
}
