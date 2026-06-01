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
 * 聊天工作流运行快照表实体。
 *
 * <p>用于记录一次会话分析的执行状态、图 checkpoint 和节点输出进度，支持异常中断后的继续执行。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_workflow_run")
public class ChatWorkflowRunEntity {

    /** 运行记录主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID */
    private String sessionId;

    /** 智能体 ID */
    private Integer agentId;

    /** 用户原始问题 */
    private String query;

    /** 运行状态：running、interrupted、completed、failed */
    private String status;

    /** 最近完成的节点名称 */
    private String lastNodeName;

    /** 图框架下一节点名称 */
    private String nextNodeName;

    /** 图框架 checkpoint ID */
    private String checkpointId;

    /** 最近一次图状态快照 JSON */
    private String stateSnapshot;

    /** 当前已累计输出内容 */
    private String accumulatedContent;

    /** 中断或失败原因 */
    private String interruptReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
