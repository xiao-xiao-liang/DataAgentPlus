package com.liang.data.agent.workflow.dto;

import com.liang.data.agent.common.enums.TextType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流节点 SSE 响应体
 *
 * <p>每个节点的流式输出都会被包装为此对象，通过 ServerSentEvent 推送给前端</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphNodeResponse {

    /**
     * 智能体 ID
     */
    private String agentId;

    /**
     * 线程 ID
     */
    private String threadId;

    /**
     * 当前输出的节点名称 (对应 StateKey 中的节点常量)
     */
    private String nodeName;

    /**
     * 文本类型 (前端据此切换渲染模式)
     */
    private TextType textType;

    /**
     * 文本内容 (逐 chunk 推送)
     */
    private String text;

    /**
     * 是否为错误响应
     */
    @Builder.Default
    private boolean error = false;

    /**
     * 是否为完成标记
     */
    @Builder.Default
    private boolean complete = false;

    /**
     * 构建错误响应
     */
    public static GraphNodeResponse error(String agentId, String threadId, String text) {
        return GraphNodeResponse.builder()
                .agentId(agentId)
                .threadId(threadId)
                .text(text)
                .error(true)
                .textType(TextType.TEXT)
                .build();
    }

    /**
     * 构建完成标记响应
     */
    public static GraphNodeResponse complete(String agentId, String threadId) {
        return GraphNodeResponse.builder()
                .agentId(agentId)
                .threadId(threadId)
                .complete(true)
                .textType(TextType.TEXT)
                .build();
    }
}
