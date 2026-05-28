package com.liang.data.agent.service.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天消息 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息主键 */
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

    /** 创建时间 */
    private LocalDateTime createTime;
}
