package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 分析任务准入队列表实体。
 *
 * <p>用于记录前端分析请求的排队、运行、完成、失败和取消状态，支持展示当前任务前方位次。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_workflow_queue")
public class ChatWorkflowQueueEntity {

    /** 队列记录主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 队列任务 ID */
    private String queueId;

    /** 用户 ID */
    private Long userId;

    /** 会话 ID */
    private String sessionId;

    /** 智能体 ID */
    private Integer agentId;

    /** 用户原始问题 */
    private String query;

    /** 队列状态：WAITING、RUNNING、COMPLETED、FAILED、CANCELLED */
    private String status;

    /** 队列范围 */
    private String queueScope;

    /** 入队时间 */
    private LocalDateTime queuedAt;

    /** 开始运行时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 取消原因 */
    private String cancelReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
