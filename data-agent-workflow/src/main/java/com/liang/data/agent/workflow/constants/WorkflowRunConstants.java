package com.liang.data.agent.workflow.constants;

import lombok.NoArgsConstructor;

/**
 * 工作流运行状态常量。
 */
@NoArgsConstructor
public final class WorkflowRunConstants {

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_INTERRUPTED = "interrupted";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final long FALLBACK_PERSIST_INTERVAL_MILLIS = 2000L;
}
