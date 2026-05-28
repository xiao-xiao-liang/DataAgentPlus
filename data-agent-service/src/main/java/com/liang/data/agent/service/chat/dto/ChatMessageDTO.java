package com.liang.data.agent.service.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 聊天消息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色：user, assistant, system */
    @NotBlank(message = "角色不能为空")
    private String role;

    /** 消息内容 */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /** 消息类型：text, sql, result, error */
    private String messageType;

    /** 元数据 (JSON) */
    private String metadata;

    /** 是否需要生成标题 */
    private boolean titleNeeded;
}
