package com.liang.data.agent.workflow.service;

import com.liang.data.agent.gateway.context.GatewayExecutionContext;
import com.liang.data.agent.workflow.vo.WorkflowRunVO;

import java.util.Map;

/**
 * 工作流运行状态服务。
 *
 * <p>负责记录图执行状态、节点进度和中断状态，为会话异常断开后的继续分析提供依据。</p>
 */
public interface WorkflowRunService {

    /**
     * 创建或重置会话当前运行记录。
     *
     * @param sessionId 会话 ID
     * @param agentId   智能体 ID
     * @param userId    用户 ID
     * @param query     用户问题
     */
    void startRun(String sessionId, Integer agentId, Long userId, String query);

    /**
     * 创建单次工作流运行记录，并持久化运行与追踪标识。
     *
     * @param context 模型网关执行上下文
     * @param query   用户问题
     */
    void startRun(GatewayExecutionContext context, String query);

    /**
     * 标记节点完成并保存图状态快照。
     *
     * @param runId              单次工作流运行ID；兼容旧调用方传会话ID，后续上下文接入后统一传运行ID
     * @param nodeName           节点名称
     * @param nextNodeName       下一节点名称
     * @param checkpointId       checkpoint ID
     * @param stateSnapshot      状态快照
     * @param accumulatedContent 已累计输出内容
     */
    void markNodeCompleted(String runId, String nodeName, String nextNodeName, String checkpointId,
                           Map<String, Object> stateSnapshot, String accumulatedContent);

    /**
     * 标记运行完成。
     *
     * @param runId 单次工作流运行ID；兼容旧调用方传会话ID，后续上下文接入后统一传运行ID
     */
    void markCompleted(String runId);

    /**
     * 标记运行中断。
     *
     * @param runId  单次工作流运行ID；兼容旧调用方传会话ID，后续上下文接入后统一传运行ID
     * @param reason 中断原因
     */
    void markInterrupted(String runId, String reason);

    /**
     * 标记运行失败。
     *
     * @param runId  单次工作流运行ID；兼容旧调用方传会话ID，后续上下文接入后统一传运行ID
     * @param reason 失败原因
     */
    default void markFailed(String runId, String reason) {
        markFailed(runId, null, reason);
    }

    /**
     * 标记运行失败，并记录失败节点名称。
     *
     * @param runId          单次工作流运行ID；兼容旧调用方传会话ID，后续上下文接入后统一传运行ID
     * @param failedNodeName 失败节点名称
     * @param reason         失败原因
     */
    void markFailed(String runId, String failedNodeName, String reason);

    /**
     * 查询最近一次运行状态。
     *
     * @param sessionId 会话 ID
     * @return 运行状态
     */
    WorkflowRunVO findLatest(String sessionId);

    /**
     * 返回空实现，供单元测试直接构造 GraphService 时使用。
     *
     * @return 空实现
     */
    static WorkflowRunService noop() {
        return new WorkflowRunService() {
            @Override
            public void startRun(String sessionId, Integer agentId, Long userId, String query) {
            }

            @Override
            public void startRun(GatewayExecutionContext context, String query) {
            }

            @Override
            public void markNodeCompleted(String sessionId, String nodeName, String nextNodeName, String checkpointId,
                                          Map<String, Object> stateSnapshot, String accumulatedContent) {
            }

            @Override
            public void markCompleted(String sessionId) {
            }

            @Override
            public void markInterrupted(String sessionId, String reason) {
            }

            @Override
            public void markFailed(String runId, String failedNodeName, String reason) {
            }

            @Override
            public WorkflowRunVO findLatest(String sessionId) {
                return null;
            }
        };
    }
}
