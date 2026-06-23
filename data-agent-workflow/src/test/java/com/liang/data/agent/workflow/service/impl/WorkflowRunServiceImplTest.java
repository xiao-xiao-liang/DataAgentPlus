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

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        service.markNodeCompleted("run-1", "节点A", "节点B", "checkpoint-1", Map.of("step", 1), "内容");

        Wrapper<ChatWorkflowRunEntity> wrapper = captureUpdateWrapper();
        assertThat(wrapper.getSqlSegment()).contains("run_id").doesNotContain("session_id");
        verify(chatWorkflowRunMapper, never()).selectOne(any());
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
}
