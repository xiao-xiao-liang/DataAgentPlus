package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息表
 *
 * <p>消息不做软删除, 删会话时级联删除消息</p>
 */
@Data
@TableName("chat_message")
public class ChatMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private String sessionId;

    /** 角色：user, assistant, system */
    private String role;

    /** 消息内容 */
    private String content;

    /** 消息类型：text, sql, result, error */
    private String messageType;

    /** 元数据 (JSON) */
    private String metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
