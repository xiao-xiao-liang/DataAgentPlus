package com.liang.data.agent.service.knowledge.chunk;

import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 知识分块持久化字段与状态机 SQL 测试。
 */
class AgentKnowledgeChunkEntityTest {

    @Test
    void shouldAccessTaskVersionAndProcessingStartedAt() {
        AgentKnowledgeChunkEntity chunk = new AgentKnowledgeChunkEntity();
        LocalDateTime startedAt = LocalDateTime.now();
        chunk.setVectorTaskVersion(3);
        chunk.setVectorProcessingStartedAt(startedAt);
        chunk.setVectorStatus(ChunkVectorStatus.PROCESSING.getCode());

        assertThat(chunk.getVectorTaskVersion()).isEqualTo(3);
        assertThat(chunk.getVectorProcessingStartedAt()).isEqualTo(startedAt);
        assertThat(chunk.getVectorStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void casSqlShouldCheckContentTaskVersionAndSourceStatus() throws NoSuchMethodException {
        Update update = AgentKnowledgeChunkMapper.class
                .getMethod("completeVectorIfProcessing", String.class, Integer.class, Integer.class, String.class)
                .getAnnotation(Update.class);

        assertThat(String.join("\n", update.value())).contains(
                "content_version = #{contentVersion}",
                "vector_task_version = #{taskVersion}",
                "vector_status = 'PROCESSING'",
                "embedding_id = #{embeddingId}");
    }

    @Test
    void deadLetterSqlShouldFailCurrentPendingOrProcessingTask() throws NoSuchMethodException {
        Update update = AgentKnowledgeChunkMapper.class
                .getMethod("markVectorFailedIfCurrent", String.class, Integer.class, Integer.class, String.class)
                .getAnnotation(Update.class);

        assertThat(String.join("\n", update.value())).contains(
                "content_version = #{contentVersion}",
                "vector_task_version = #{taskVersion}",
                "vector_status IN ('PENDING', 'PROCESSING')");
    }

    @Test
    void migrationShouldAddTaskVersionAndProcessingLease() throws IOException {
        String migration = readClasspathResource("sql/migration/V20260606_02__knowledge_chunk_task_version.sql");
        assertThat(migration).contains(
                "vector_task_version",
                "vector_processing_started_at",
                "idx_vector_pending_recovery",
                "idx_vector_processing_recovery");
    }

    private String readClasspathResource(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
