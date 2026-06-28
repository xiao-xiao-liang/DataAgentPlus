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

    @Test
    void shouldDefineInvocationAndAttemptTablesInSchemaAndMigration() throws IOException {
        // 1. 读取初始化脚本和增量迁移脚本。
        String schemaSql = readSql(SCHEMA_PATH);
        String migrationSql = readSql(MIGRATION_PATH);

        // 2. 验证两份脚本都包含模型网关调用主表和尝试明细表。
        assertThat(schemaSql).contains("create table if not exists model_gateway_invocation");
        assertThat(schemaSql).contains("create table if not exists model_gateway_attempt");
        assertThat(migrationSql).contains("create table if not exists model_gateway_invocation");
        assertThat(migrationSql).contains("create table if not exists model_gateway_attempt");
    }

    @Test
    void shouldDefineRequiredInvocationIndexesInSchemaAndMigration() throws IOException {
        // 1. 读取初始化脚本和增量迁移脚本。
        String schemaSql = readSql(SCHEMA_PATH);
        String migrationSql = readSql(MIGRATION_PATH);

        // 2. 验证调用主表的唯一索引和查询索引。
        assertInvocationIndexes(schemaSql);
        assertInvocationIndexes(migrationSql);
    }

    @Test
    void shouldDefineRequiredAttemptIndexesInSchemaAndMigration() throws IOException {
        // 1. 读取初始化脚本和增量迁移脚本。
        String schemaSql = readSql(SCHEMA_PATH);
        String migrationSql = readSql(MIGRATION_PATH);

        // 2. 验证尝试明细表的唯一索引和查询索引。
        assertAttemptIndexes(schemaSql);
        assertAttemptIndexes(migrationSql);
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

    private static String readSql(Path path) throws IOException {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8)
                .toLowerCase()
                .replaceAll("\\s+", " ");
    }

    private static void assertInvocationIndexes(String sql) {
        assertThat(sql).contains("primary key (id)");
        assertThat(sql).contains("unique key uk_invocation_id (invocation_id)");
        assertThat(sql).contains("index idx_run_id (run_id)");
        assertThat(sql).contains("index idx_trace_id (trace_id)");
        assertThat(sql).contains("index idx_scene_status_time (scene_code, status, start_time)");
        assertThat(sql).contains("index idx_provider_model_time (provider, model, start_time)");
    }

    private static void assertAttemptIndexes(String sql) {
        assertThat(sql).contains("primary key (id)");
        assertThat(sql).contains("unique key uk_attempt_id (attempt_id)");
        assertThat(sql).contains("index idx_invocation_id (invocation_id)");
        assertThat(sql).contains("index idx_provider_model_time (provider, model, start_time)");
    }
}
