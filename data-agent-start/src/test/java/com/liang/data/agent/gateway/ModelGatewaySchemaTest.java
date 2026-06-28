package com.liang.data.agent.gateway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型网关持久化表结构测试。
 *
 * <p>用于验证调用主表和尝试明细表的建表脚本完整性，确保不会新增完整 Prompt、完整响应或密钥类敏感字段。</p>
 */
class ModelGatewaySchemaTest {

    private static final Path SCHEMA_PATH = Path.of("src/main/resources/sql/schema.sql");
    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/sql/migration/V20260625_01__model_gateway_invocation_attempt.sql");
    private static final String INVOCATION_TABLE_NAME = "model_gateway_invocation";
    private static final String ATTEMPT_TABLE_NAME = "model_gateway_attempt";

    @Test
    void shouldDefineRequiredInvocationFieldsAndIndexesInSchemaAndMigration() throws IOException {
        // 1. 分别读取初始化脚本和增量迁移脚本。
        String schemaSql = readSql(SCHEMA_PATH);
        String migrationSql = readSql(MIGRATION_PATH);

        // 2. 分别截取调用主表的建表语句，避免同名索引在其他表中误判通过。
        String schemaInvocationDdl = extractTableDefinition(schemaSql, INVOCATION_TABLE_NAME);
        String migrationInvocationDdl = extractTableDefinition(migrationSql, INVOCATION_TABLE_NAME);

        // 3. 验证调用主表字段和索引完整性。
        assertInvocationFields(schemaInvocationDdl);
        assertInvocationFields(migrationInvocationDdl);
        assertInvocationIndexes(schemaInvocationDdl);
        assertInvocationIndexes(migrationInvocationDdl);
    }

    @Test
    void shouldDefineRequiredAttemptFieldsAndIndexesInSchemaAndMigration() throws IOException {
        // 1. 分别读取初始化脚本和增量迁移脚本。
        String schemaSql = readSql(SCHEMA_PATH);
        String migrationSql = readSql(MIGRATION_PATH);

        // 2. 分别截取尝试明细表的建表语句，避免同名索引在其他表中误判通过。
        String schemaAttemptDdl = extractTableDefinition(schemaSql, ATTEMPT_TABLE_NAME);
        String migrationAttemptDdl = extractTableDefinition(migrationSql, ATTEMPT_TABLE_NAME);

        // 3. 验证尝试明细表字段和索引完整性。
        assertAttemptFields(schemaAttemptDdl);
        assertAttemptFields(migrationAttemptDdl);
        assertAttemptIndexes(schemaAttemptDdl);
        assertAttemptIndexes(migrationAttemptDdl);
    }

    @Test
    void shouldNotPersistSensitivePayloadFieldsInMigration() throws IOException {
        // 1. 读取增量迁移脚本并统一转为小写。
        String migrationSql = readSql(MIGRATION_PATH);

        // 2. 验证迁移脚本不包含完整请求、完整响应和密钥类敏感字段。
        assertThat(migrationSql).doesNotContain("prompt");
        assertThat(migrationSql).doesNotContain("response");
        assertThat(migrationSql).doesNotContain("api_key");
        assertThat(migrationSql).doesNotContain("proxy_password");
    }

    /**
     * 读取 SQL 文件内容，并统一为小写紧凑格式，降低大小写和空白字符差异对断言的影响。
     *
     * @param path SQL 文件路径
     * @return 标准化后的 SQL 内容
     * @throws IOException 文件读取失败时抛出
     */
    private static String readSql(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8)
                .toLowerCase()
                .replaceAll("\\s+", " ");
    }

    /**
     * 从 SQL 内容中截取指定表的建表语句。
     *
     * @param sql 标准化后的 SQL 内容
     * @param tableName 表名
     * @return 指定表的建表语句
     */
    private static String extractTableDefinition(String sql, String tableName) {
        // 1. 定位目标表的建表语句起始位置。
        String createTablePrefix = "create table if not exists " + tableName;
        int startIndex = sql.indexOf(createTablePrefix);
        assertThat(startIndex).as("应包含表 %s 的建表语句", tableName).isGreaterThanOrEqualTo(0);

        // 2. 定位目标表建表语句结束分号。
        int endIndex = sql.indexOf(';', startIndex);
        assertThat(endIndex).as("表 %s 的建表语句应以分号结束", tableName).isGreaterThan(startIndex);

        // 3. 返回目标表的完整建表片段。
        return sql.substring(startIndex, endIndex + 1);
    }

    /**
     * 批量断言 DDL 片段包含全部指定内容。
     *
     * @param ddl DDL 片段
     * @param snippets 需要包含的内容
     */
    private static void assertContainsAll(String ddl, String... snippets) {
        // 1. 遍历每个预期片段。
        for (String snippet : snippets) {
            // 2. 逐项断言 DDL 中包含该片段。
            assertThat(ddl).contains(snippet);
        }
    }

    private static void assertInvocationFields(String ddl) {
        assertContainsAll(ddl,
                " id ",
                " invocation_id ",
                " run_id ",
                " trace_id ",
                " session_id ",
                " user_id ",
                " agent_id ",
                " tenant_id ",
                " scene_code ",
                " call_mode ",
                " status ",
                " provider ",
                " model ",
                " start_time ",
                " end_time ",
                " duration_ms ",
                " input_tokens ",
                " output_tokens ",
                " total_tokens ",
                " error_code ",
                " error_message ",
                " create_time ",
                " update_time ");
    }

    private static void assertInvocationIndexes(String ddl) {
        assertContainsAll(ddl,
                "unique key uk_invocation_id",
                "index idx_run_id",
                "index idx_trace_id",
                "index idx_scene_status_time",
                "index idx_provider_model_time");
    }

    private static void assertAttemptFields(String ddl) {
        assertContainsAll(ddl,
                " id ",
                " attempt_id ",
                " invocation_id ",
                " attempt_no ",
                " provider ",
                " model ",
                " status ",
                " start_time ",
                " end_time ",
                " duration_ms ",
                " http_status ",
                " error_code ",
                " error_message ",
                " create_time ",
                " update_time ");
    }

    private static void assertAttemptIndexes(String ddl) {
        assertContainsAll(ddl,
                "unique key uk_attempt_id",
                "index idx_invocation_id",
                "index idx_provider_model_time");
    }
}
