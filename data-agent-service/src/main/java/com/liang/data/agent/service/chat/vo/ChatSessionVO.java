package com.liang.data.agent.service.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天会话 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID (UUID) */
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

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
