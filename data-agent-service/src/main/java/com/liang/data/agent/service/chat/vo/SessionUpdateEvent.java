package com.liang.data.agent.service.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 聊天会话更新事件，供 SSE 接口推送给前端
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionUpdateEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_TITLE_UPDATED = "title-updated";

    /** 事件类型 */
    private String type;

    /** 会话ID */
    private String sessionId;

    /** 会话新标题 */
    private String title;

    public static SessionUpdateEvent titleUpdated(String sessionId, String title) {
        return SessionUpdateEvent.builder()
                .type(TYPE_TITLE_UPDATED)
                .sessionId(sessionId)
                .title(title)
                .build();
    }
}
