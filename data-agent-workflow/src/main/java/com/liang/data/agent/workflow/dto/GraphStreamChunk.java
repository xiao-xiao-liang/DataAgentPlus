package com.liang.data.agent.workflow.dto;

import static com.liang.data.agent.workflow.constants.WorkflowEventConstants.*;

import org.springframework.util.StringUtils;

/**
 * 工作流流式输出事件，用于在后端传递文本片段和节点完成信号。
 *
 * @param eventType     工作流事件类型
 * @param content       需要发送给前端的文本内容
 * @param nodeName      当前事件关联的节点名称
 * @param nodeCompleted 是否表示节点已经完成
 */
public record GraphStreamChunk(String eventType, String content, String nodeName, boolean nodeCompleted) {

    /**
     * 构建普通文本输出事件。
     *
     * @param content  输出内容
     * @param nodeName 当前节点名称
     * @return 工作流流式输出事件
     */
    public static GraphStreamChunk content(String content, String nodeName) {
        return new GraphStreamChunk(EVENT_NODE_OUTPUT, content, nodeName, false);
    }

    /**
     * 构建节点开始事件。
     *
     * @param nodeName 当前节点名称
     * @return 工作流流式输出事件
     */
    public static GraphStreamChunk nodeStarted(String nodeName) {
        return new GraphStreamChunk(EVENT_NODE_STARTED, "", nodeName, false);
    }

    /**
     * 构建节点完成事件。
     *
     * @param nodeName 已完成节点名称
     * @return 工作流流式输出事件
     */
    public static GraphStreamChunk nodeCompleted(String nodeName) {
        return new GraphStreamChunk(EVENT_NODE_COMPLETED, "", nodeName, true);
    }

    /**
     * 构建等待用户输入事件。
     *
     * @param nodeName 当前节点名称
     * @param content  等待态关联的结构化内容
     * @return 工作流流式输出事件
     */
    public static GraphStreamChunk waitingUserInput(String nodeName, String content) {
        return new GraphStreamChunk(EVENT_WAITING_USER_INPUT, content, nodeName, false);
    }

    /**
     * 构建工作流错误事件。
     *
     * @param content  错误内容
     * @param nodeName 当前节点名称
     * @return 工作流流式输出事件
     */
    public static GraphStreamChunk error(String content, String nodeName) {
        return new GraphStreamChunk(EVENT_WORKFLOW_ERROR, content, nodeName, false);
    }

    /**
     * 构建工作流完成事件。
     *
     * @return 工作流流式输出事件
     */
    public static GraphStreamChunk done() {
        return new GraphStreamChunk(EVENT_WORKFLOW_DONE, "", "", false);
    }

    /**
     * 判断当前事件是否包含需要返回给前端的文本。
     *
     * @return 包含文本时返回 true
     */
    public boolean hasContent() {
        return StringUtils.hasLength(content);
    }
}
