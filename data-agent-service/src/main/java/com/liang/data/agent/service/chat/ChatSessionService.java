package com.liang.data.agent.service.chat;

import com.liang.data.agent.service.chat.vo.ChatSessionVO;

import java.util.List;

/**
 * 聊天会话服务接口
 */
public interface ChatSessionService {

    /**
     * 根据智能体 ID 获取会话列表
     *
     * @param agentId 智能体 ID
     * @return 会话列表
     */
    List<ChatSessionVO> findByAgentId(Integer agentId);

    /**
     * 创建一个新会话
     *
     * @param agentId 智能体 ID
     * @param title   标题，默认 "新会话"
     * @param userId  用户 ID (可选)
     * @return 创建出的会话 VO
     */
    ChatSessionVO createSession(Integer agentId, String title, Long userId);

    /**
     * 按照会话 ID 获取会话详情
     *
     * @param sessionId 会话 ID
     * @return 会话 VO
     */
    ChatSessionVO findBySessionId(String sessionId);

    /**
     * 清空某个智能体下的所有会话
     *
     * @param agentId 智能体 ID
     */
    void clearSessionsByAgentId(Integer agentId);

    /**
     * 更新会话的最后活跃时间
     *
     * @param sessionId 会话 ID
     */
    void updateSessionTime(String sessionId);

    /**
     * 置顶或取消置顶会话
     *
     * @param sessionId 会话 ID
     * @param isPinned  是否置顶
     */
    void pinSession(String sessionId, boolean isPinned);

    /**
     * 会话重命名
     *
     * @param sessionId 会话 ID
     * @param newTitle  新标题
     */
    void renameSession(String sessionId, String newTitle);

    /**
     * 删除单个会话，并物理清除其下所有消息
     *
     * @param sessionId 会话 ID
     */
    void deleteSession(String sessionId);
}
