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