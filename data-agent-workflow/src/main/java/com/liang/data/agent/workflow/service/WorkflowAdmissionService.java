package com.liang.data.agent.workflow.service;

import com.liang.data.agent.workflow.vo.WorkflowQueueVO;

/**
 * 分析任务准入服务。
 *
 * <p>负责创建分析排队记录、推进可运行任务、查询当前位次并维护任务终态。</p>
 */
public interface WorkflowAdmissionService {

    /**
     * 创建等待中的分析任务。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param agentId   智能体 ID
     * @param query     用户问题
     * @return 队列信息
     */
    WorkflowQueueVO enqueue(String userId, String sessionId, Integer agentId, String query);

    /**
     * 尝试将等待任务推进为运行中。
     *
     * @param queueId 队列任务 ID
     * @return 队列信息
     */
    WorkflowQueueVO tryPromote(String queueId);

    /**
     * 查询当前队列位次。
     *
     * @param queueId 队列任务 ID
     * @return 队列信息
     */
    WorkflowQueueVO queryPosition(String queueId);

    /**
     * 标记任务完成。
     *
     * @param queueId 队列任务 ID
     */
    void complete(String queueId);

    /**
     * 标记任务失败。
     *
     * @param queueId 队列任务 ID
     * @param reason  失败原因
     */
    void fail(String queueId, String reason);

    /**
     * 标记任务取消。
     *
     * @param queueId 队列任务 ID
     * @param reason  取消原因
     */
    void cancel(String queueId, String reason);
}
