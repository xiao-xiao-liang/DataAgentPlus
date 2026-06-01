package com.liang.data.agent.service.chat;

import com.liang.data.agent.service.chat.dto.ChatMessageDTO;
import com.liang.data.agent.service.chat.vo.ChatMessageVO;

import java.util.List;

/**
 * 聊天消息服务接口
 */
public interface ChatMessageService {

    /**
     * 根据会话 ID 获取历史消息列表
     *
     * @param sessionId 会话 ID
     * @return 消息列表
     */
    List<ChatMessageVO> findBySessionId(String sessionId);

    /**
     * 保存消息
     *
     * @param dto       消息 DTO
     * @param sessionId 会话 ID
     * @return 保存后的消息 VO
     */
    ChatMessageVO saveMessage(ChatMessageDTO dto, String sessionId);

    /**
     * 保存或更新流式 assistant 消息。
     *
     * <p>同一轮分析过程中持续更新最近一条 streaming 消息，避免连接异常断开后前端已展示内容丢失。</p>
     *
     * @param sessionId   会话 ID
     * @param content     已累计的 assistant 输出
     * @param messageType 消息类型
     * @param complete    是否已完成
     * @return 保存后的消息 VO
     */
    ChatMessageVO saveOrUpdateStreamingAssistantMessage(String sessionId, String content, String messageType, boolean complete);

    /**
     * 异步保存消息（保护 WebFlux 核心线程不被传统 JDBC 阻塞）
     *
     * @param dto       消息 DTO
     * @param sessionId 会话 ID
     */
    void saveMessageAsync(ChatMessageDTO dto, String sessionId);

    /**
     * 获取多轮对话上下文文本
     *
     * <p>优化点：直接从数据库拉取最近几轮的问答对，并提取 AI 响应中生成的执行计划（Plan），拼接成无状态的上下文文本</p>
     *
     * @param sessionId 会话 ID
     * @param limit     限制的消息条数
     * @return 格式化后的多轮对话字符串
     */
    String getMultiTurnContext(String sessionId, int limit);
}
