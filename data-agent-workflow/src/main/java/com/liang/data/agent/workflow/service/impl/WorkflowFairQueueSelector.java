package com.liang.data.agent.workflow.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分析任务公平队列选择器。
 *
 * <p>根据等待顺序、同用户运行上限和全局可用槽位，选择本轮可以推进的队列任务。</p>
 */
final class WorkflowFairQueueSelector {

    private WorkflowFairQueueSelector() {
    }

    /**
     * 选择本轮可推进的队列任务 ID。
     *
     * @param waitingTasks       按入队顺序排列的等待任务
     * @param userRunningCounts  用户当前运行中任务数
     * @param availableSlots     全局可用运行槽位
     * @param maxUserRunning     同用户最大运行中任务数
     * @return 本轮可推进的队列任务 ID
     */
    static List<String> selectRunnableQueueIds(List<WaitingTask> waitingTasks,
                                               Map<Long, Long> userRunningCounts,
                                               int availableSlots,
                                               int maxUserRunning) {
        List<String> selectedQueueIds = new ArrayList<>();
        Set<Long> selectedUsers = new HashSet<>();
        if (waitingTasks == null || waitingTasks.isEmpty() || availableSlots <= 0) {
            return selectedQueueIds;
        }

        for (WaitingTask waitingTask : waitingTasks) {
            if (selectedQueueIds.size() >= availableSlots) {
                break;
            }
            Long userId = waitingTask.userId();
            long userRunning = userRunningCounts.getOrDefault(userId, 0L);

            // 1. 已达到同用户运行上限的任务，本轮不推进。
            if (userRunning >= maxUserRunning) {
                continue;
            }

            // 2. 同一轮内同一个用户最多推进一个任务，避免单用户批量提交占满队列前部。
            if (!selectedUsers.add(userId)) {
                continue;
            }

            // 3. 记录本轮可运行任务，交由调用方完成 Redis/DB 状态更新。
            selectedQueueIds.add(waitingTask.queueId());
        }
        return selectedQueueIds;
    }

    /**
     * 等待中的队列任务。
     *
     * @param queueId 队列任务 ID
     * @param userId  用户 ID
     */
    record WaitingTask(String queueId, Long userId) {
    }
}
