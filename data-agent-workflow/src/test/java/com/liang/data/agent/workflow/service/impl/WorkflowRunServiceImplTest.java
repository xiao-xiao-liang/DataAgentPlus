package com.liang.data.agent.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.liang.data.agent.dal.entity.ChatWorkflowRunEntity;
import com.liang.data.agent.dal.mapper.ChatWorkflowRunMapper;
import com.liang.data.agent.gateway.context.GatewayExecutionContext;
import com.liang.data.agent.workflow.vo.WorkflowRunVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作流运行记录服务单元测试。
 */
class WorkflowRunServiceImplTest {

    private final ChatWorkflowRunMapper chatWorkflowRunMapper = mock(ChatWorkflowRunMapper.class);
    private final WorkflowRunServiceImpl service = new WorkflowRunServiceImpl(chatWorkflowRunMapper);

    @BeforeAll
    static void initMyBatisPlusTableInfo() {
        // 1. 初始化 MyBatis-Plus 表元数据，保证 LambdaWrapper 在纯单元测试中可解析列名。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ChatWorkflowRunEntity.class
        );
    }

    @Test
    void startRunShouldPersistRunAndTraceIdentity() {
        GatewayExecutionContext context = new GatewayExecutionContext("run-1", "trace-1", "session-1", 1L, 2, null);

        service.startRun(context, "查询销售额");

        ArgumentCaptor<ChatWorkflowRunEntity> captor = ArgumentCaptor.forClass(ChatWorkflowRunEntity.class);
        verify(chatWorkflowRunMapper).insert(captor.capture());
        ChatWorkflowRunEntity saved = captor.getValue();
        assertThat(saved.getRunId()).isEqualTo("run-1");
        assertThat(saved.getTraceId()).isEqualTo("trace-1");
        assertThat(saved.getSessionId()).isEqualTo("session-1");
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getAgentId()).isEqualTo(2);
        assertThat(saved.getQuery()).isEqualTo("查询销售额");
        assertThat(saved.getStatus()).isEqualTo("running");
        assertThat(saved.getStartTime()).isNotNull();
    }

    @Test
    void markNodeCompletedShouldUpdateByRunId() {
        when(chatWorkflowRunMapper.selectOne(any())).thenReturn(runningEntity());

        service.markNodeCompleted("run-1", "节点A", "节点B", "checkpoint-1", Map.of("step", 1), "内容");

        Wrapper<ChatWorkflowRunEntity> queryWrapper = captureSelectWrapper();
        Wrapper<ChatWorkflowRunEntity> wrapper = captureUpdateWrapper();
        assertThat(queryWrapper.getSqlSegment()).contains("run_id").doesNotContain("session_id");
        assertThat(wrapper.getSqlSegment()).contains("run_id").doesNotContain("session_id");
    }

    @Test
    void markNodeCompletedShouldFallbackToLatestSessionRunWhenRunIdNotFound() {
        when(chatWorkflowRunMapper.insert(any(ChatWorkflowRunEntity.class))).thenAnswer(invocation -> {
            ChatWorkflowRunEntity entity = invocation.getArgument(0);
            entity.setId(9L);
            return 1;
        });
        when(chatWorkflowRunMapper.selectOne(any())).thenReturn(null, sessionEntity());

        service.startRun("session-1", 2, 1L, "查询");
        service.markNodeCompleted("session-1", "节点A", "节点B", "checkpoint-1", Map.of("step", 1), "内容");

        Wrapper<ChatWorkflowRunEntity> updateWrapper = captureUpdateWrapper();
        assertThat(captureSelectWrappers()).satisfies(wrappers -> {
            assertThat(wrappers.get(0).getSqlSegment()).contains("run_id").doesNotContain("session_id");
            assertThat(wrappers.get(1).getSqlSegment()).contains("session_id").doesNotContain("run_id");
        });
        assertThat(updateWrapper.getSqlSegment()).contains("id").doesNotContain("run_id").doesNotContain("session_id");
        assertThat(updateWrapper.getSqlSet()).contains("last_node_name", "next_node_name", "checkpoint_id");
    }

    @Test
    void markCompletedShouldUpdateEndTimeAndDurationByRunId() {
        when(chatWorkflowRunMapper.selectOne(any())).thenReturn(runningEntity());

        service.markCompleted("run-1");

        Wrapper<ChatWorkflowRunEntity> queryWrapper = captureSelectWrapper();
        Wrapper<ChatWorkflowRunEntity> updateWrapper = captureUpdateWrapper();
        assertThat(queryWrapper.getSqlSegment()).contains("run_id").doesNotContain("session_id");
        assertThat(updateWrapper.getSqlSegment()).contains("run_id").doesNotContain("session_id");
        assertThat(updateWrapper.getSqlSet()).contains("status", "end_time", "duration_ms");
    }

    @Test
    void markCompletedShouldFallbackToLatestSessionRunWhenRunIdNotFound() {
        when(chatWorkflowRunMapper.insert(any(ChatWorkflowRunEntity.class))).thenAnswer(invocation -> {
            ChatWorkflowRunEntity entity = invocation.getArgument(0);
            entity.setId(9L);
            return 1;
        });
        when(chatWorkflowRunMapper.selectOne(any())).thenReturn(null, sessionEntity());

        service.startRun("session-1", 2, 1L, "查询");
        service.markCompleted("session-1");

        Wrapper<ChatWorkflowRunEntity> updateWrapper = captureUpdateWrapper();
        assertThat(captureSelectWrappers()).satisfies(wrappers -> {
            assertThat(wrappers.get(0).getSqlSegment()).contains("run_id").doesNotContain("session_id");
            assertThat(wrappers.get(1).getSqlSegment()).contains("session_id").doesNotContain("run_id");
        });
        assertThat(updateWrapper.getSqlSegment()).contains("id").doesNotContain("run_id").doesNotContain("session_id");
        assertThat(updateWrapper.getSqlSet()).contains("status", "end_time", "duration_ms");
    }

    @Test
    void markCompletedShouldNotOverwriteFailedTerminalStatus() {
        when(chatWorkflowRunMapper.selectOne(any())).thenReturn(failedEntity());

        service.markCompleted("run-1");

        verify(chatWorkflowRunMapper).selectOne(any());
        verify(chatWorkflowRunMapper, never()).update(any(), any());
    }

    @Test
    void markInterruptedShouldUpdateEndTimeAndDurationByRunId() {
        when(chatWorkflowRunMapper.selectOne(any())).thenReturn(runningEntity());

        service.markInterrupted("run-1", "用户中断");

        Wrapper<ChatWorkflowRunEntity> updateWrapper = captureUpdateWrapper();
        assertThat(updateWrapper.getSqlSegment()).contains("run_id").doesNotContain("session_id");
        assertThat(updateWrapper.getSqlSet()).contains("status", "interrupt_reason", "end_time", "duration_ms");
    }

    @Test
    void markFailedShouldPersistFailedNodeNameAndDurationByRunId() {
        when(chatWorkflowRunMapper.selectOne(any())).thenReturn(runningEntity());

        service.markFailed("run-1", "节点A", "执行失败");

        Wrapper<ChatWorkflowRunEntity> updateWrapper = captureUpdateWrapper();
        assertThat(updateWrapper.getSqlSegment()).contains("run_id").doesNotContain("session_id");
        assertThat(updateWrapper.getSqlSet())
                .contains("status", "interrupt_reason", "failed_node_name", "end_time", "duration_ms");
    }

    @Test
    void workflowRunSqlShouldContainTraceIdentityColumnsAndManualConfirmationComment() throws IOException {
        String schemaSql = Files.readString(resolveProjectPath("data-agent-start/src/main/resources/sql/schema.sql"));
        String migrationSql = Files.readString(resolveProjectPath(
                "data-agent-start/src/main/resources/sql/migration/V20260623_01__workflow_run_trace_identity.sql"
        ));

        assertThat(schemaSql)
                .contains("run_id")
                .contains("trace_id")
                .contains("start_time")
                .contains("end_time")
                .contains("duration_ms")
                .contains("failed_node_name")
                .contains("UNIQUE KEY uk_run_id")
                .contains("INDEX idx_trace_id");
        assertThat(migrationSql)
                .contains("执行前需由运维确认目标库尚未添加这些列和索引")
                .contains("ADD COLUMN run_id")
                .contains("ADD COLUMN trace_id")
                .contains("ADD UNIQUE KEY uk_run_id")
                .contains("ADD INDEX idx_trace_id");
    }

    @Test
    void findLatestShouldReturnRunAndTraceFields() {
        ChatWorkflowRunEntity entity = runningEntity();
        entity.setEndTime(LocalDateTime.now());
        entity.setDurationMs(3000L);
        entity.setFailedNodeName("节点A");
        when(chatWorkflowRunMapper.selectOne(any())).thenReturn(entity);

        WorkflowRunVO result = service.findLatest("session-1");

        assertThat(result.getRunId()).isEqualTo("run-1");
        assertThat(result.getTraceId()).isEqualTo("trace-1");
        assertThat(result.getStartTime()).isEqualTo(entity.getStartTime());
        assertThat(result.getEndTime()).isEqualTo(entity.getEndTime());
        assertThat(result.getDurationMs()).isEqualTo(3000L);
        assertThat(result.getFailedNodeName()).isEqualTo("节点A");
        assertThat(captureSelectWrapper().getSqlSegment()).contains("session_id");
    }

    private ChatWorkflowRunEntity runningEntity() {
        return ChatWorkflowRunEntity.builder()
                .id(1L)
                .runId("run-1")
                .traceId("trace-1")
                .sessionId("session-1")
                .agentId(2)
                .userId(1L)
                .status("running")
                .startTime(LocalDateTime.now().minusSeconds(3))
                .build();
    }

    private ChatWorkflowRunEntity failedEntity() {
        ChatWorkflowRunEntity entity = runningEntity();
        entity.setStatus("failed");
        entity.setInterruptReason("已失败");
        entity.setFailedNodeName("节点A");
        entity.setEndTime(LocalDateTime.now().minusSeconds(1));
        entity.setDurationMs(2000L);
        return entity;
    }

    private ChatWorkflowRunEntity sessionEntity() {
        return ChatWorkflowRunEntity.builder()
                .id(9L)
                .sessionId("session-1")
                .agentId(2)
                .userId(1L)
                .status("running")
                .startTime(LocalDateTime.now().minusSeconds(3))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Wrapper<ChatWorkflowRunEntity> captureUpdateWrapper() {
        ArgumentCaptor<Wrapper<ChatWorkflowRunEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(chatWorkflowRunMapper).update(isNull(), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Wrapper<ChatWorkflowRunEntity> captureSelectWrapper() {
        ArgumentCaptor<Wrapper<ChatWorkflowRunEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(chatWorkflowRunMapper).selectOne(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<Wrapper<ChatWorkflowRunEntity>> captureSelectWrappers() {
        ArgumentCaptor<Wrapper<ChatWorkflowRunEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(chatWorkflowRunMapper, times(2)).selectOne(captor.capture());
        return captor.getAllValues();
    }

    private Path resolveProjectPath(String relativePath) {
        Path rootPath = Path.of(relativePath);
        if (Files.exists(rootPath)) {
            return rootPath;
        }
        return Path.of("..", relativePath);
    }
}
