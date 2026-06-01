package com.liang.data.agent.workflow.dto;

import org.springframework.util.StringUtils;

/**
 * 工作流流式输出事件，用于在后端传递文本片段和节点完成信号。
 *
 * @param content       需要发送给前端的文本内容
 * @param nodeName      当前事件关联的节点名称
 * @param nodeCompleted 是否表示节点已经完成
 */
public record GraphStreamChunk(String content, String nodeName, boolean nodeCompleted) {

    /**
     * 构建普通文本输出事件。
     *
     * @param content  输出内容
     * @param nodeName 当前节点名称
     * @return 工作流流式输出事件
     */
    public static GraphStreamChunk content(String content, String nodeName) {
        return new GraphStreamChunk(content, nodeName, false);
    }

    /**
     * 构建节点完成事件。
     *
     * @param nodeName 已完成节点名称
     * @return 工作流流式输出事件
     */
    public static GraphStreamChunk nodeCompleted(String nodeName) {
        return new GraphStreamChunk("", nodeName, true);
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
