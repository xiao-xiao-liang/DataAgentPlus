package com.liang.data.agent.workflow.prompt;

import com.liang.data.agent.common.schema.ColumnDTO;
import com.liang.data.agent.common.schema.SchemaDTO;
import com.liang.data.agent.common.schema.TableDTO;
import com.liang.data.agent.workflow.dto.node.SqlGenerationDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptServiceTest {

    @Test
    void shouldBuildIntentRecognitionPromptWithInputs() {
        String prompt = PromptHelper.buildIntentRecognitionPrompt("历史上下文", "本次问题");

        assertTrue(prompt.contains("历史上下文"));
        assertTrue(prompt.contains("本次问题"));
        assertTrue(prompt.contains("classification"));
    }

    @Test
    void shouldBuildFeasibilityAssessmentPromptWithJsonFormat() {
        String prompt = PromptHelper.buildFeasibilityAssessmentPrompt(
                "分析系统链路核心瓶颈",
                buildSchema(),
                "系统链路指 RAG Trace 链路",
                "无"
        );

        assertTrue(prompt.contains("分析系统链路核心瓶颈"));
        assertTrue(prompt.contains("requestType"));
        assertTrue(prompt.contains("memoryWorthSaving"));
        assertTrue(prompt.contains("# Table: orders, 订单表"));
    }

    @Test
    void shouldBuildSqlPromptWithFormattedSchema() {
        SqlGenerationDTO dto = SqlGenerationDTO.builder()
                .dialect("mysql")
                .query("查询订单")
                .evidence("证据")
                .executionDescription("执行说明")
                .schemaInfo(buildSchema())
                .build();

        String prompt = PromptHelper.buildNewSqlGeneratorPrompt(dto);

        assertTrue(prompt.contains("mysql"));
        assertTrue(prompt.contains("查询订单"));
        assertTrue(prompt.contains("# Table: orders, 订单表"));
        assertTrue(prompt.contains("order_id:BIGINT"));
    }

    @Test
    void shouldBuildSqlErrorFixerPromptWithFailedSqlAndErrorMessage() {
        SqlGenerationDTO dto = SqlGenerationDTO.builder()
                .dialect("mysql")
                .query("query orders")
                .evidence("evidence")
                .executionDescription("execute current step")
                .schemaInfo(buildSchema())
                .sql("select * from orders; select * from users")
                .exceptionMessage("multi-statement not allow")
                .build();

        String prompt = PromptHelper.buildSqlErrorFixerPrompt(dto);

        assertTrue(prompt.contains("multi-statement not allow"));
        assertTrue(prompt.contains("select * from orders; select * from users"));
        assertTrue(prompt.contains("query orders"));
    }

    private SchemaDTO buildSchema() {
        ColumnDTO id = new ColumnDTO();
        id.setName("order_id");
        id.setDescription("订单ID");
        id.setType("bigint");

        TableDTO table = new TableDTO();
        table.setName("orders");
        table.setDescription("订单表");
        table.setColumn(List.of(id));
        table.setPrimaryKeys(List.of("order_id"));

        SchemaDTO schema = new SchemaDTO();
        schema.setName("demo");
        schema.setTables(List.of(table));
        return schema;
    }
}
