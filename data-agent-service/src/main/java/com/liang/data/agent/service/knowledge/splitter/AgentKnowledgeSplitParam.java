package com.liang.data.agent.service.knowledge.splitter;

/**
 * 智能体知识分块参数。
 *
 * <p>用于控制分块策略、目标长度和重叠长度。</p>
 */
public record AgentKnowledgeSplitParam(String splitterType, int chunkSize, int overlap) {

    public static AgentKnowledgeSplitParam defaults(String splitterType) {
        return new AgentKnowledgeSplitParam(splitterType, 1000, 100);
    }
}
