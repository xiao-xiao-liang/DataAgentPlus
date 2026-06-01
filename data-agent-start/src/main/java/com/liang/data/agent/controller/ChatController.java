package com.liang.data.agent.controller;

import com.liang.data.agent.common.result.Result;
import com.liang.data.agent.common.result.Results;
import com.liang.data.agent.service.chat.ChatMessageService;
import com.liang.data.agent.service.chat.ChatSessionService;
import com.liang.data.agent.service.chat.SessionTitleService;
import com.liang.data.agent.service.chat.dto.ChatMessageDTO;
import com.liang.data.agent.service.chat.util.ReportTemplateUtil;
import com.liang.data.agent.service.chat.vo.ChatMessageVO;
import com.liang.data.agent.service.chat.vo.ChatSessionVO;
import com.liang.data.agent.workflow.service.WorkflowRunService;
import com.liang.data.agent.workflow.vo.WorkflowRunVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 聊天会话与消息管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final SessionTitleService sessionTitleService;
    private final ReportTemplateUtil reportTemplateUtil;
    private final WorkflowRunService workflowRunService;

    /**
     * 获取指定智能体的所有聊天会话
     *
     * @param id 智能体 ID
     * @return 会话列表
     */
    @GetMapping("/agent/{id}/sessions")
    public Result<List<ChatSessionVO>> getAgentSessions(@PathVariable(value = "id") Integer id) {
        List<ChatSessionVO> sessions = chatSessionService.findByAgentId(id);
        return Results.success(sessions);
    }

    /**
     * 创建一个新会话
     *
     * @param id      智能体 ID
     * @param request 请求体，可包含 title 和 userId
     * @return 创建好的会话详情
     */
    @PostMapping("/agent/{id}/sessions")
    public Result<ChatSessionVO> createSession(@PathVariable(value = "id") Integer id,
                                               @RequestBody(required = false) Map<String, Object> request) {
        String title = request != null ? (String) request.get("title") : null;
        Long userId = null;
        if (request != null && request.get("userId") != null) {
            try {
                userId = Long.valueOf(request.get("userId").toString());
            } catch (Exception e) {
                log.warn("Failed to parse userId: {}", request.get("userId"));
            }
        }

        ChatSessionVO session = chatSessionService.createSession(id, title, userId);
        return Results.success(session);
    }

    /**
     * 清空指定智能体下的所有会话
     *
     * @param id 智能体 ID
     * @return 操作结果
     */
    @DeleteMapping("/agent/{id}/sessions")
    public Result<Void> clearAgentSessions(@PathVariable(value = "id") Integer id) {
        chatSessionService.clearSessionsByAgentId(id);
        return Results.success();
    }

    /**
     * 获取会话的所有历史消息
     *
     * @param sessionId 会话 ID
     * @return 消息列表
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageVO>> getSessionMessages(@PathVariable(value = "sessionId") String sessionId) {
        List<ChatMessageVO> messages = chatMessageService.findBySessionId(sessionId);
        return Results.success(messages);
    }

    /**
     * 获取会话最近一次工作流运行状态。
     *
     * @param sessionId 会话 ID
     * @return 工作流运行状态
     */
    @GetMapping("/sessions/{sessionId}/workflow-run")
    public Result<WorkflowRunVO> getSessionWorkflowRun(@PathVariable(value = "sessionId") String sessionId) {
        return Results.success(workflowRunService.findLatest(sessionId));
    }

    /**
     * 手动保存单条消息到会话（并触发标题异步生成）
     *
     * @param sessionId 会话 ID
     * @param request   消息内容 DTO
     * @return 保存后的消息详情
     */
    @PostMapping("/sessions/{sessionId}/messages")
    public Result<ChatMessageVO> saveMessage(@PathVariable(value = "sessionId") String sessionId,
                                             @Valid @RequestBody ChatMessageDTO request) {
        ChatMessageVO savedMessage = chatMessageService.saveMessage(request, sessionId);

        // 更新会话的最后活跃时间
        chatSessionService.updateSessionTime(sessionId);

        // 如果需要，调度后台线程异步生成标题
        if (request.isTitleNeeded()) {
            sessionTitleService.scheduleTitleGeneration(sessionId, request.getContent());
        }

        return Results.success(savedMessage);
    }

    /**
     * 置顶/取消置顶会话
     *
     * @param sessionId 会话 ID
     * @param isPinned  是否置顶
     * @return 操作结果
     */
    @PutMapping("/sessions/{sessionId}/pin")
    public Result<Void> pinSession(@PathVariable(value = "sessionId") String sessionId,
                                   @RequestParam(value = "isPinned") Boolean isPinned) {
        chatSessionService.pinSession(sessionId, isPinned);
        return Results.success();
    }

    /**
     * 会话重命名
     *
     * @param sessionId 会话 ID
     * @param title     新标题
     * @return 操作结果
     */
    @PutMapping("/sessions/{sessionId}/rename")
    public Result<Void> renameSession(@PathVariable(value = "sessionId") String sessionId,
                                      @RequestParam(value = "title") String title) {
        if (!StringUtils.hasText(title)) {
            return Results.failure("PARAM_ERROR", "标题不能为空");
        }
        chatSessionService.renameSession(sessionId, title.trim());
        return Results.success();
    }

    /**
     * 删除单个会话
     *
     * @param sessionId 会话 ID
     * @return 操作结果
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable(value = "sessionId") String sessionId) {
        chatSessionService.deleteSession(sessionId);
        return Results.success();
    }

    /**
     * 下载网页分析报告
     *
     * @param sessionId 会话 ID
     * @param content   Markdown 报告内容
     * @return 下载响应
     */
    @PostMapping("/sessions/{sessionId}/reports/html")
    public ResponseEntity<byte[]> convertAndDownloadHtml(@PathVariable(value = "sessionId") String sessionId,
                                                         @RequestBody String content) {
        if (!StringUtils.hasText(content)) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Downloading HTML report for session {}", sessionId);
        
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append(reportTemplateUtil.getHeader());
        htmlContent.append(content);
        htmlContent.append(reportTemplateUtil.getFooter());
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String filename = "report_" + timestamp + ".html";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "html", StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment", filename);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(htmlContent.toString().getBytes(StandardCharsets.UTF_8));
    }
}
