package com.liang.data.agent.controller;

import com.liang.data.agent.common.result.Result;
import com.liang.data.agent.common.result.Results;
import com.liang.data.agent.dal.connector.DatabaseAccessor;
import com.liang.data.agent.dal.connector.bo.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MVP 阶段临时验证接口
 * 仅用于 ApiFox 联调测试 DatabaseAccessor 连接池与 SQL 执行情况
 */
@RestController
@RequestMapping("/test/connector")
@RequiredArgsConstructor
// @Profile("dev") // 如有需要可取消注释，限制仅开发环境加载
public class TestConnectorController {

    private final DatabaseAccessor databaseAccessor;

    /**
     * 1. 验证连通性 (Ping)
     * POST /test/connector/ping
     */
    @PostMapping("/ping")
    public Result<String> testPing(@RequestBody DbConfigBO config) {
        String result = databaseAccessor.ping(config);
        return Results.success(result);
    }

    /**
     * 2. 验证获取表元数据 (Show Tables)
     * POST /test/connector/show-tables
     */
    @PostMapping("/show-tables")
    public Result<List<TableInfoBO>> testShowTables(@RequestBody DbConfigBO config) {
        // pattern 传空字符串查所有
        List<TableInfoBO> tables = databaseAccessor.showTables(config, "");
        return Results.success(tables);
    }

    /**
     * 3. 验证获取列元数据 (Show Columns)
     * POST /test/connector/show-columns
     */
    @PostMapping("/show-columns")
    public Result<List<ColumnInfoBO>> testShowColumns(@RequestBody TableQueryDTO queryDTO) {
        List<ColumnInfoBO> columns = databaseAccessor.showColumns(queryDTO.getConfig(), queryDTO.getTableName());
        return Results.success(columns);
    }

    /**
     * 4. 验证动态 SQL 执行 (Execute SQL)
     * POST /test/connector/execute-sql
     */
    @PostMapping("/execute-sql")
    public Result<ResultSetBO> testExecuteSql(@RequestBody SqlExecuteDTO executeDTO) {
        ResultSetBO resultSet = databaseAccessor.executeSql(executeDTO.getConfig(), executeDTO.getSql());
        return Results.success(resultSet);
    }

    /**
     * 5. 验证字段采样 (Sample Column)
     * POST /test/connector/sample-column
     */
    @PostMapping("/sample-column")
    public Result<List<String>> testSampleColumn(@RequestBody ColumnSampleDTO sampleDTO) {
        List<String> samples = databaseAccessor.sampleColumn(sampleDTO.getConfig(), sampleDTO.getTableName(), sampleDTO.getColumnName());
        return Results.success(samples);
    }

    // ---------------- 以下为接收复合参数的内部 DTO ----------------

    @Data
    public static class TableQueryDTO {
        private DbConfigBO config;
        private String tableName;
    }

    @Data
    public static class SqlExecuteDTO {
        private DbConfigBO config;
        private String sql;
    }

    @Data
    public static class ColumnSampleDTO {
        private DbConfigBO config;
        private String tableName;
        private String columnName;
    }
}
