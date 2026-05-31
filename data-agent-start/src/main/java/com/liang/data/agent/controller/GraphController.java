package com.liang.data.agent.controller;

import com.liang.data.agent.common.exception.ClientException;
import com.liang.data.agent.common.enums.InteractionType;
import com.liang.data.agent.service.agent.AgentService;
import com.liang.data.agent.service.chat.ChatMessageService;
import com.liang.data.agent.service.chat.ChatSessionService;
import com.liang.data.agent.service.chat.SessionTitleService;
import com.liang.data.agent.service.chat.dto.ChatMessageDTO;
import com.liang.data.agent.service.chat.vo.ChatSessionVO;
import com.liang.data.agent.workflow.dto.GraphRequest;
import com.liang.data.agent.workflow.service.GraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * 重构后的核心工作流流式图交互控制器
 *
 * <p>集成了会话与消息持久化、多轮历史检索、异步落库保护机制</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final SessionTitleService sessionTitleService;
    private final AgentService agentService;

    /**
     * 核心 SSE 流式对话接口
     *
     * @param request 对话流式请求体
     * @return 返回 String 类型的 SSE 响应流
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody GraphRequest request) {
        log.info("Received graph chat request - agentId: {}, threadId: {}, query: {}", 
                request.getAgentId(), request.getThreadId(), request.getQuery());

        // 0. 校验并解析 agentId（如果数据库不存在该智能体将抛出 ServiceException 被全局异常处理器捕获）
        int agentIdInt;
        try {
            if (!StringUtils.hasText(request.getAgentId()) || "default".equals(request.getAgentId())) {
                throw new ClientException("请先选择一个智能体再开始对话");
            }
            agentIdInt = Integer.parseInt(request.getAgentId());
        } catch (NumberFormatException e) {
            throw new ClientException("智能体ID格式错误");
        }
        agentService.getById(agentIdInt);

        // 1. 双重校验并自动创建会话
        String threadId = request.getThreadId();
        boolean isNewSession = false;
        if (!StringUtils.hasText(threadId)) {
            threadId = UUID.randomUUID().toString();
            request.setThreadId(threadId);
            isNewSession = true;
        }

        ChatSessionVO session = chatSessionService.findBySessionId(threadId);
        if (session == null) {
            synchronized (this) {
                session = chatSessionService.findBySessionId(threadId);
                if (session == null) {
                    session = chatSessionService.createSession(agentIdInt, "新会话", null);
                    isNewSession = true;
                }
            }
        }

        // 2. 保存用户消息（澄清答复需要落库，避免历史会话缺失用户补充内容）
        InteractionType interactionType = InteractionType.fromCode(request.getInteractionType());
        String userMessageContent = null;
        if (interactionType == InteractionType.CLARIFICATION_ANSWER || interactionType == InteractionType.CLARIFICATION_CONFIRM) {
            userMessageContent = request.getInteractionContent();
        } else if (!StringUtils.hasText(request.getHumanFeedbackContent())
                && interactionType != InteractionType.HUMAN_PLAN_FEEDBACK
                && StringUtils.hasText(request.getQuery())) {
            userMessageContent = request.getQuery();
        }
        if (StringUtils.hasText(userMessageContent)) {
            ChatMessageDTO userMsgDto = ChatMessageDTO.builder()
                    .role("user")
                    .content(userMessageContent)
                    .messageType("text")
                    .build();
            chatMessageService.saveMessage(userMsgDto, threadId);
        }

        // 3. 构建多轮对话上下文 (获取最近 10 条消息，即 5 轮)
        String multiTurnContext = "(无)";
        if (!isNewSession) {
            multiTurnContext = chatMessageService.getMultiTurnContext(threadId, 10);
        }

        // 4. 调用工作流引擎获取流式输出
        Flux<String> streamFlux = graphService.chat(request, multiTurnContext);

        // 5. 还原 AI 输出，并在流结束时使用 Spring 异步离线落库，防止阻塞 WebFlux Netty 核心线程
        final String finalSessionId = threadId;
        final String userQuery = request.getQuery();
        final boolean triggerTitleGen = isNewSession;
        
        StringBuilder accumulatedResponse = new StringBuilder();

        return streamFlux
                .doOnNext(accumulatedResponse::append)
                .doOnComplete(() -> {
                    String fullResponse = accumulatedResponse.toString();
                    log.info("Graph stream interaction completed for session: {}, response length: {}", 
                            finalSessionId, fullResponse.length());

                    // 构建 AI 的回复 DTO 并异步落库
                    ChatMessageDTO assistantMsgDto = ChatMessageDTO.builder()
                            .role("assistant")
                            .content(fullResponse)
                            .messageType("text")
                            .build();
                    chatMessageService.saveMessageAsync(assistantMsgDto, finalSessionId);

                    // 更新会话更新时间
                    chatSessionService.updateSessionTime(finalSessionId);

                    // 如果是第一轮对话，后台异步触发会话标题智能生成与 SSE 广播推送
                    if (triggerTitleGen) {
                        sessionTitleService.scheduleTitleGeneration(finalSessionId, userQuery);
                    }
                })
                .doOnError(err -> {
                    log.error("Error occurred during graph stream for session: {}", finalSessionId, err);
                    
                    // 异步保存错误消息，防止会话流程直接挂起
                    ChatMessageDTO errorMsgDto = ChatMessageDTO.builder()
                            .role("assistant")
                            .content("系统繁忙，请稍后再试（" + err.getMessage() + "）")
                            .messageType("error")
                            .build();
                    chatMessageService.saveMessageAsync(errorMsgDto, finalSessionId);
                })
                .onErrorResume(err -> Flux.just(formatSseData("系统繁忙，请稍后再试（" + safeMessage(err) + "）")))
                .doOnCancel(() -> {
                    log.info("客户端断开连接, threadId: {}", finalSessionId);
                    graphService.stopStreamProcessing(finalSessionId);
                });
    }

    /**
     * 获取工作流图结构 (PlantUML)
     */
    @GetMapping("/visualization")
    public String visualization() {
        return graphService.getGraphVisualization();
    }

    private String formatSseData(String message) {
        return "data:" + message.replace("\r", " ").replace("\n", " ") + "\n\n";
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || !StringUtils.hasText(throwable.getMessage())) {
            return "对话请求失败";
        }
        return throwable.getMessage();
    }
}
