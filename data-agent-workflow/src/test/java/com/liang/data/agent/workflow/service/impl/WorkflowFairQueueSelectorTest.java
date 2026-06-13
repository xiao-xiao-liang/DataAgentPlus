package com.liang.data.agent.workflow.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分析任务公平队列选择器单元测试。
 */
class WorkflowFairQueueSelectorTest {

    @Test
    void selectRunnableQueueIdsShouldLimitOneTaskPerUserInSameRound() {
        List<WorkflowFairQueueSelector.WaitingTask> waitingTasks = List.of(
                new WorkflowFairQueueSelector.WaitingTask("queue-1", 1001L),
                new WorkflowFairQueueSelector.WaitingTask("queue-2", 1001L),
                new WorkflowFairQueueSelector.WaitingTask("queue-3", 1002L)
        );

        List<String> selectedQueueIds = WorkflowFairQueueSelector.selectRunnableQueueIds(
                waitingTasks,
                Map.of(),
                2,
                2
        );

        assertThat(selectedQueueIds).containsExactly("queue-1", "queue-3");
    }
}
