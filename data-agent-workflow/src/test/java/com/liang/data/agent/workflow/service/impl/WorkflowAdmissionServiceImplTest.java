package com.liang.data.agent.workflow.service.impl;

import com.liang.data.agent.dal.entity.ChatWorkflowQueueEntity;
import com.liang.data.agent.dal.mapper.ChatWorkflowQueueMapper;
import com.liang.data.agent.workflow.vo.WorkflowQueueVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分析任务准入服务单元测试。
 */
class WorkflowAdmissionServiceImplTest {

    private final ChatWorkflowQueueMapper chatWorkflowQueueMapper = mock(ChatWorkflowQueueMapper.class);
    private final WorkflowAdmissionServiceImpl service = new WorkflowAdmissionServiceImpl(
            chatWorkflowQueueMapper,
            2,
            10
    );

    @Test
    void enqueueShouldCreateWaitingQueueRecord() {
        when(chatWorkflowQueueMapper.insert(any(ChatWorkflowQueueEntity.class))).thenAnswer(invocation -> {
            ChatWorkflowQueueEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });
        when(chatWorkflowQueueMapper.countAheadTasks(any(), any(), any())).thenReturn(0L);
        when(chatWorkflowQueueMapper.countAheadUsers(any(), any(), any())).thenReturn(0L);
        when(chatWorkflowQueueMapper.countRunningByUser(1001L, "CHAT_WORKFLOW")).thenReturn(0L);

        WorkflowQueueVO result = service.enqueue(1001L, "session-1", 2, "分析客流");

        ArgumentCaptor<ChatWorkflowQueueEntity> captor = ArgumentCaptor.forClass(ChatWorkflowQueueEntity.class);
        verify(chatWorkflowQueueMapper).insert(captor.capture());
        ChatWorkflowQueueEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1001L);
        assertThat(saved.getSessionId()).isEqualTo("session-1");
        assertThat(saved.getAgentId()).isEqualTo(2);
        assertThat(saved.getQuery()).isEqualTo("分析客流");
        assertThat(saved.getStatus()).isEqualTo("WAITING");
        assertThat(saved.getQueueScope()).isEqualTo("CHAT_WORKFLOW");
        assertThat(saved.getQueueId()).isNotBlank();

        assertThat(result.getStatus()).isEqualTo("WAITING");
        assertThat(result.getAheadTaskCount()).isZero();
        assertThat(result.getAheadUserCount()).isZero();
        assertThat(result.getRunningTaskCount()).isZero();
        assertThat(result.getMaxUserRunningLimit()).isEqualTo(2);
    }

    @Test
    void tryPromoteShouldKeepWaitingWhenUserAlreadyHasTwoRunningTasks() {
        ChatWorkflowQueueEntity queue = waitingQueue("queue-1", 1001L, 1L);
        when(chatWorkflowQueueMapper.selectByQueueId("queue-1")).thenReturn(queue);
        when(chatWorkflowQueueMapper.countRunningByUser(1001L, "CHAT_WORKFLOW")).thenReturn(2L);
        when(chatWorkflowQueueMapper.countRunningByScope("CHAT_WORKFLOW")).thenReturn(2L);

        WorkflowQueueVO result = service.tryPromote("queue-1");

        assertThat(result.getStatus()).isEqualTo("WAITING");
        assertThat(result.getRunningTaskCount()).isEqualTo(2);
    }

    @Test
    void tryPromoteShouldPromoteQueueHeadWhenCapacityAvailable() {
        ChatWorkflowQueueEntity queue = waitingQueue("queue-1", 1001L, 1L);
        when(chatWorkflowQueueMapper.selectByQueueId("queue-1")).thenReturn(queue);
        when(chatWorkflowQueueMapper.countRunningByUser(1001L, "CHAT_WORKFLOW")).thenReturn(1L);
        when(chatWorkflowQueueMapper.countRunningByScope("CHAT_WORKFLOW")).thenReturn(5L);
        when(chatWorkflowQueueMapper.existsEarlierWaiting("CHAT_WORKFLOW", queue.getQueuedAt(), queue.getId())).thenReturn(false);
        when(chatWorkflowQueueMapper.markRunning("queue-1")).thenReturn(1);

        WorkflowQueueVO result = service.tryPromote("queue-1");

        assertThat(result.getStatus()).isEqualTo("RUNNING");
        verify(chatWorkflowQueueMapper).markRunning("queue-1");
    }

    @Test
    void tryPromoteShouldNotPromoteWhenEarlierWaitingTaskExists() {
        ChatWorkflowQueueEntity queue = waitingQueue("queue-2", 1001L, 2L);
        when(chatWorkflowQueueMapper.selectByQueueId("queue-2")).thenReturn(queue);
        when(chatWorkflowQueueMapper.countRunningByUser(1001L, "CHAT_WORKFLOW")).thenReturn(0L);
        when(chatWorkflowQueueMapper.countRunningByScope("CHAT_WORKFLOW")).thenReturn(0L);
        when(chatWorkflowQueueMapper.existsEarlierWaiting("CHAT_WORKFLOW", queue.getQueuedAt(), queue.getId())).thenReturn(true);

        WorkflowQueueVO result = service.tryPromote("queue-2");

        assertThat(result.getStatus()).isEqualTo("WAITING");
    }

    @Test
    void queryPositionShouldReturnAheadTaskAndUserCounts() {
        ChatWorkflowQueueEntity queue = waitingQueue("queue-3", 1003L, 3L);
        when(chatWorkflowQueueMapper.selectByQueueId("queue-3")).thenReturn(queue);
        when(chatWorkflowQueueMapper.countAheadTasks("CHAT_WORKFLOW", queue.getQueuedAt(), queue.getId())).thenReturn(5L);
        when(chatWorkflowQueueMapper.countAheadUsers("CHAT_WORKFLOW", queue.getQueuedAt(), queue.getId())).thenReturn(2L);
        when(chatWorkflowQueueMapper.countRunningByUser(1003L, "CHAT_WORKFLOW")).thenReturn(1L);

        WorkflowQueueVO result = service.queryPosition("queue-3");

        assertThat(result.getAheadTaskCount()).isEqualTo(5);
        assertThat(result.getAheadUserCount()).isEqualTo(2);
        assertThat(result.getRunningTaskCount()).isEqualTo(1);
    }

    private ChatWorkflowQueueEntity waitingQueue(String queueId, Long userId, Long id) {
        return ChatWorkflowQueueEntity.builder()
                .id(id)
                .queueId(queueId)
                .userId(userId)
                .sessionId("session-" + id)
                .agentId(2)
                .query("query")
                .status("WAITING")
                .queueScope("CHAT_WORKFLOW")
                .queuedAt(LocalDateTime.now())
                .build();
    }
}
