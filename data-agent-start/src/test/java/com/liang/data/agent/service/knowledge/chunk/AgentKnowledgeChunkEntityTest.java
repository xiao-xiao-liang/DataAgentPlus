package com.liang.data.agent.service.knowledge.chunk;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识分块持久化字段与乐观更新能力测试。
 */
class AgentKnowledgeChunkEntityTest {

    @Test
    void shouldAccessEditingAndVectorVersionFields() {
        AgentKnowledgeChunkEntity chunk = new AgentKnowledgeChunkEntity();

        chunk.setName("第一节");
        chunk.setNameLocked(1);
        chunk.setContentVersion(3);
        chunk.setVectorVersion(2);
        chunk.setVectorStatus(ChunkVectorStatus.SYNCED);
        chunk.setRetryCount(4);

        assertThat(chunk.getName()).isEqualTo("第一节");
        assertThat(chunk.getNameLocked()).isEqualTo(1);
        assertThat(chunk.getContentVersion()).isEqualTo(3);
        assertThat(chunk.getVectorVersion()).isEqualTo(2);
        assertThat(chunk.getVectorStatus()).isEqualTo("SYNCED");
        assertThat(chunk.getRetryCount()).isEqualTo(4);
    }

    @Test
    void updateContentWithVersionShouldUseChunkIdAndContentVersionConditions() {
        AgentKnowledgeChunkMapper mapper = mock(AgentKnowledgeChunkMapper.class, CALLS_REAL_METHODS);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        int updated = mapper.updateContentWithVersion("chunk-1", 3, "新名称", 1, "新内容", 3);

        assertThat(updated).isEqualTo(1);
        UpdateWrapper<AgentKnowledgeChunkEntity> wrapper = captureUpdateWrapper(mapper);
        assertOptimisticConditions(wrapper, "chunk-1", 3);
        assertThat(wrapper.getSqlSet()).contains("name=", "name_locked=", "content=", "content_length=",
                "content_version = content_version + 1", "vector_status=", "retry_count=", "error_msg=")
                .doesNotContain("vector_version");
        assertSetValue(wrapper, "vector_status", ChunkVectorStatus.PENDING);
        assertSetValue(wrapper, "retry_count", 0);
        assertSetValue(wrapper, "error_msg", null);
    }

    @Test
    void updateVectorStatusIfCurrentShouldUseChunkIdAndContentVersionConditions() {
        AgentKnowledgeChunkMapper mapper = mock(AgentKnowledgeChunkMapper.class, CALLS_REAL_METHODS);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        int updated = mapper.updateVectorStatusIfCurrent(
                "chunk-2", 5, ChunkVectorStatus.SYNCED, null, 0, null
        );

        assertThat(updated).isEqualTo(1);
        UpdateWrapper<AgentKnowledgeChunkEntity> wrapper = captureUpdateWrapper(mapper);
        assertOptimisticConditions(wrapper, "chunk-2", 5);
        assertSetValue(wrapper, "vector_status", ChunkVectorStatus.SYNCED);
        assertSetValue(wrapper, "vector_version", null);
        assertSetValue(wrapper, "retry_count", 0);
        assertSetValue(wrapper, "error_msg", null);
    }

    @Test
    void migrationShouldAddAndBackfillKnowledgeChunkWorkbenchFields() throws IOException {
        String migration = readClasspathResource("sql/migration/V20260606__knowledge_chunk_workbench.sql");

        assertThat(migration).contains(
                "ALTER TABLE agent_knowledge_chunk",
                "ADD COLUMN name VARCHAR(255)",
                "ADD COLUMN name_locked TINYINT NOT NULL DEFAULT 0",
                "ADD COLUMN content_version INT NOT NULL DEFAULT 1",
                "ADD COLUMN vector_version INT NULL",
                "ADD COLUMN vector_status VARCHAR(32) NOT NULL DEFAULT 'PENDING'",
                "ADD COLUMN retry_count INT NOT NULL DEFAULT 0",
                "ADD INDEX idx_knowledge_vector_status (knowledge_id, vector_status)",
                "name = CONCAT('分块 #', chunk_order)",
                "WHEN status = 'VECTOR_STORED' THEN 1",
                "WHEN status = 'VECTOR_STORED' THEN 'SYNCED'",
                "WHEN status = 'FAILED' THEN 'FAILED'"
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private UpdateWrapper<AgentKnowledgeChunkEntity> captureUpdateWrapper(AgentKnowledgeChunkMapper mapper) {
        ArgumentCaptor<Wrapper<AgentKnowledgeChunkEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        return (UpdateWrapper<AgentKnowledgeChunkEntity>) captor.getValue();
    }

    private void assertOptimisticConditions(UpdateWrapper<AgentKnowledgeChunkEntity> wrapper,
                                            String chunkId,
                                            Integer contentVersion) {
        String sqlSegment = wrapper.getSqlSegment();
        Matcher chunkIdMatcher = conditionMatcher(sqlSegment, "chunk_id");
        Matcher contentVersionMatcher = conditionMatcher(sqlSegment, "content_version");

        assertThat(sqlSegment.substring(chunkIdMatcher.end(), contentVersionMatcher.start())).contains("AND");
        Map<String, Object> parameters = wrapper.getParamNameValuePairs();
        assertThat(parameters).containsEntry(chunkIdMatcher.group(1), chunkId);
        assertThat(parameters).containsEntry(contentVersionMatcher.group(1), contentVersion);
    }

    private Matcher conditionMatcher(String sqlSegment, String column) {
        Pattern pattern = Pattern.compile(column + "\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.([^}]+)}");
        Matcher matcher = pattern.matcher(sqlSegment);
        assertThat(matcher.find()).isTrue();
        return matcher;
    }

    private void assertSetValue(UpdateWrapper<AgentKnowledgeChunkEntity> wrapper, String column, Object expectedValue) {
        String sqlSet = wrapper.getSqlSet();
        String marker = column + "=#{ew.paramNameValuePairs.";
        int valueStart = sqlSet.indexOf(marker) + marker.length();
        assertThat(valueStart).isGreaterThanOrEqualTo(marker.length());
        int valueEnd = sqlSet.indexOf('}', valueStart);
        assertThat(valueEnd).isGreaterThan(valueStart);
        String parameterName = sqlSet.substring(valueStart, valueEnd);
        assertThat(wrapper.getParamNameValuePairs()).containsEntry(parameterName, expectedValue);
    }

    private String readClasspathResource(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
