package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.ChatWorkflowQueueEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分析任务准入队列 Mapper。
 */
@Mapper
public interface ChatWorkflowQueueMapper extends BaseMapper<ChatWorkflowQueueEntity> {

    /**
     * 根据队列任务 ID 查询队列记录。
     *
     * @param queueId 队列任务 ID
     * @return 队列记录
     */
    @Select("SELECT * FROM chat_workflow_queue WHERE queue_id = #{queueId} LIMIT 1")
    ChatWorkflowQueueEntity selectByQueueId(@Param("queueId") String queueId);

    /**
     * 查询指定范围内等待中的队列任务。
     *
     * @param queueScope 队列范围
     * @param limit      最大查询数量
     * @return 等待任务列表
     */
    @Select("""
            SELECT * FROM chat_workflow_queue
            WHERE queue_scope = #{queueScope}
              AND status = 'WAITING'
            ORDER BY queued_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<ChatWorkflowQueueEntity> selectWaitingByScope(@Param("queueScope") String queueScope, @Param("limit") int limit);

    /**
     * 查询指定范围内运行中的队列任务。
     *
     * @param queueScope 队列范围
     * @param limit      最大查询数量
     * @return 运行中任务列表
     */
    @Select("""
            SELECT * FROM chat_workflow_queue
            WHERE queue_scope = #{queueScope}
              AND status = 'RUNNING'
            ORDER BY started_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<ChatWorkflowQueueEntity> selectRunningByScope(@Param("queueScope") String queueScope, @Param("limit") int limit);

    /**
     * 统计用户运行中的分析任务数。
     *
     * @param userId     用户 ID
     * @param queueScope 队列范围
     * @return 运行中任务数
     */
    @Select("SELECT COUNT(1) FROM chat_workflow_queue WHERE user_id = #{userId} AND queue_scope = #{queueScope} AND status = 'RUNNING'")
    long countRunningByUser(@Param("userId") Long userId, @Param("queueScope") String queueScope);

    /**
     * 统计指定范围内运行中的任务数。
     *
     * @param queueScope 队列范围
     * @return 运行中任务数
     */
    @Select("SELECT COUNT(1) FROM chat_workflow_queue WHERE queue_scope = #{queueScope} AND status = 'RUNNING'")
    long countRunningByScope(@Param("queueScope") String queueScope);

    /**
     * 判断当前任务前方是否存在更早等待的任务。
     *
     * @param queueScope 队列范围
     * @param queuedAt   入队时间
     * @param id         当前记录主键
     * @return 是否存在更早等待任务
     */
    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM chat_workflow_queue
                WHERE queue_scope = #{queueScope}
                  AND status = 'WAITING'
                  AND (queued_at < #{queuedAt} OR (queued_at = #{queuedAt} AND id < #{id}))
                LIMIT 1
            )
            """)
    boolean existsEarlierWaiting(@Param("queueScope") String queueScope, @Param("queuedAt") LocalDateTime queuedAt,
                                 @Param("id") Long id);

    /**
     * 统计当前任务前方等待任务数。
     *
     * @param queueScope 队列范围
     * @param queuedAt   入队时间
     * @param id         当前记录主键
     * @return 前方任务数
     */
    @Select("""
            SELECT COUNT(1) FROM chat_workflow_queue
            WHERE queue_scope = #{queueScope}
              AND status = 'WAITING'
              AND (queued_at < #{queuedAt} OR (queued_at = #{queuedAt} AND id < #{id}))
            """)
    long countAheadTasks(@Param("queueScope") String queueScope, @Param("queuedAt") LocalDateTime queuedAt,
                         @Param("id") Long id);

    /**
     * 统计当前任务前方等待用户数。
     *
     * @param queueScope 队列范围
     * @param queuedAt   入队时间
     * @param id         当前记录主键
     * @return 前方用户数
     */
    @Select("""
            SELECT COUNT(DISTINCT user_id) FROM chat_workflow_queue
            WHERE queue_scope = #{queueScope}
              AND status = 'WAITING'
              AND (queued_at < #{queuedAt} OR (queued_at = #{queuedAt} AND id < #{id}))
            """)
    long countAheadUsers(@Param("queueScope") String queueScope, @Param("queuedAt") LocalDateTime queuedAt,
                         @Param("id") Long id);

    /**
     * 将等待任务标记为运行中。
     *
     * @param queueId 队列任务 ID
     * @return 更新行数
     */
    @Update("""
            UPDATE chat_workflow_queue
            SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP
            WHERE queue_id = #{queueId} AND status = 'WAITING'
            """)
    int markRunning(@Param("queueId") String queueId);

    /**
     * 更新队列任务终态。
     *
     * @param queueId 队列任务 ID
     * @param status  终态
     * @param reason  取消原因
     * @return 更新行数
     */
    @Update("""
            UPDATE chat_workflow_queue
            SET status = #{status}, finished_at = CURRENT_TIMESTAMP, cancel_reason = #{reason}
            WHERE queue_id = #{queueId}
            """)
    int markFinished(@Param("queueId") String queueId, @Param("status") String status, @Param("reason") String reason);
}
