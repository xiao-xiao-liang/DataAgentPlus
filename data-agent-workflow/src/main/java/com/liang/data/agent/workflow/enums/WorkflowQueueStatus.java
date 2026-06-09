package com.liang.data.agent.workflow.enums;

/**
 * 分析任务队列状态。
 */
public enum WorkflowQueueStatus {

    /** 等待运行 */
    WAITING,

    /** 运行中 */
    RUNNING,

    /** 已完成 */
    COMPLETED,

    /** 已失败 */
    FAILED,

    /** 已取消 */
    CANCELLED
}
