package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天会话表
 *
 * <p>主键为 UUID 字符串, 非自增</p>
 */
@Data
@TableName("chat_session")
public class ChatSessionEntity {

    /** 会话ID (UUID) */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 智能体ID */
    private Integer agentId;

    /** 会话标题 */
    private String title;

    /** 状态：active-活跃, archived-归档 */
    private String status;

    /** 是否置顶：0-否, 1-是 */
    private Integer isPinned;

    /** 用户ID */
    private Long userId;

    @TableLogic
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
