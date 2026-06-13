package com.liang.data.agent.workflow.constants;

import lombok.NoArgsConstructor;

/**
 * 工作流事件协议常量。
 */
@NoArgsConstructor
public final class WorkflowEventConstants {

    public static final String EVENT_NODE_OUTPUT = "node_output";
    public static final String EVENT_NODE_STARTED = "node_started";
    public static final String EVENT_NODE_COMPLETED = "node_completed";
    public static final String EVENT_WAITING_USER_INPUT = "waiting_user_input";
    public static final String EVENT_WORKFLOW_ERROR = "workflow_error";
    public static final String EVENT_WORKFLOW_DONE = "workflow_done";
    public static final String EVENT_PREFIX = "@@DATA_AGENT_EVENT@@";
    public static final String EVENT_SUFFIX = "@@END_DATA_AGENT_EVENT@@";
}
