package com.liang.data.agent.workflow.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 分析任务队列视图对象。
 */
@Data
@Builder
public class WorkflowQueueVO {

    /** 队列任务 ID */
    private String queueId;

    /** 队列状态 */
    private String status;

    /** 当前任务前方等待任务数 */
    private long aheadTaskCount;

    /** 当前任务前方等待用户数 */
    private long aheadUserCount;

    /** 当前用户运行中分析任务数 */
    private long runningTaskCount;

    /** 当前用户最大运行中分析任务数 */
    private int maxUserRunningLimit;
}
