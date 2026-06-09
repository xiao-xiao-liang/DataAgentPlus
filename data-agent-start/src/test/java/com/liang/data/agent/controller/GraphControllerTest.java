package com.liang.data.agent.controller;

import com.liang.data.agent.service.agent.AgentService;
import com.liang.data.agent.service.chat.ChatMessageService;
import com.liang.data.agent.service.chat.ChatSessionService;
import com.liang.data.agent.service.chat.SessionTitleService;
import com.liang.data.agent.service.chat.dto.ChatMessageDTO;
import com.liang.data.agent.service.chat.vo.ChatSessionVO;
import com.liang.data.agent.workflow.dto.GraphRequest;
import com.liang.data.agent.workflow.dto.GraphStreamChunk;
import com.liang.data.agent.workflow.service.GraphService;
import com.liang.data.agent.workflow.service.WorkflowAdmissionService;
import com.liang.data.agent.workflow.service.WorkflowRunService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphControllerTest {

    private final GraphService graphService = mock(GraphService.class);
    private final ChatSessionService chatSessionService = mock(ChatSessionService.class);
    private final ChatMessageService chatMessageService = mock(ChatMessageService.class);
    private final SessionTitleService sessionTitleService = mock(SessionTitleService.class);
    private final AgentService agentService = mock(AgentService.class);
    private final WorkflowRunService workflowRunService = mock(WorkflowRunService.class);
    private final WorkflowAdmissionService workflowAdmissionService = mock(WorkflowAdmissionService.class);

    private final GraphController controller = new GraphController(
            graphService,
            chatSessionService,
            chatMessageService,
            sessionTitleService,
            agentService,
            workflowRunService,
            workflowAdmissionService
    );

    @Test
    void chatReturnsSseErrorChunkWhenStreamFails() {
        GraphRequest request = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-1")
                .query("query")
                .build();

        when(chatSessionService.findBySessionId("thread-1"))
                .thenReturn(ChatSessionVO.builder().id("thread-1").agentId(2).build());
        when(chatMessageService.getMultiTurnContext("thread-1", 10)).thenReturn("(none)");
        when(graphService.chatStream(any(GraphRequest.class), anyString()))
                .thenReturn(Flux.error(new RuntimeException("boom")));

        List<String> chunks = controller.chat(request).collectList().block();

        assertThat(String.join("", chunks)).contains("data:");
        assertThat(String.join("", chunks)).contains("系统繁忙，请稍后再试");
        verify(chatMessageService).saveMessageAsync(any(), anyString());
    }

    @Test
    void chatPersistsAssistantContentOnceWhenStreamCompletes() {
        GraphRequest request = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-completed")
                .query("query")
                .build();

        when(chatSessionService.findBySessionId("thread-completed"))
                .thenReturn(ChatSessionVO.builder().id("thread-completed").agentId(2).build());
        when(chatMessageService.getMultiTurnContext("thread-completed", 10)).thenReturn("(none)");
        when(graphService.chatStream(any(GraphRequest.class), anyString()))
                .thenReturn(Flux.just(
                        GraphStreamChunk.content("node-1-output", "node-1"),
                        GraphStreamChunk.content("node-2-output", "node-1")
                ));

        List<String> chunks = controller.chat(request).collectList().block();

        assertThat(chunks).containsExactly("node-1-output", "node-2-output");
        verify(chatMessageService, never()).saveOrUpdateStreamingAssistantMessage(
                eq("thread-completed"),
                anyString(),
                eq("streaming"),
                eq(false)
        );
        verify(chatMessageService).saveOrUpdateStreamingAssistantMessage(
                "thread-completed",
                "node-1-outputnode-2-output",
                "text",
                true
        );
    }

    @Test
    void chatPersistsAssistantContentWhenNodeCompletes() {
        GraphRequest request = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-node-completed")
                .query("query")
                .build();

        when(chatSessionService.findBySessionId("thread-node-completed"))
                .thenReturn(ChatSessionVO.builder().id("thread-node-completed").agentId(2).build());
        when(chatMessageService.getMultiTurnContext("thread-node-completed", 10)).thenReturn("(none)");
        when(graphService.chatStream(any(GraphRequest.class), anyString()))
                .thenReturn(Flux.just(
                        GraphStreamChunk.content("node-1-output", "node-1"),
                        GraphStreamChunk.nodeCompleted("node-1"),
                        GraphStreamChunk.content("node-2-output", "node-2")
                ));

        List<String> chunks = controller.chat(request).collectList().block();

        assertThat(chunks).containsExactly("node-1-output", "node-2-output");
        verify(chatMessageService).saveOrUpdateStreamingAssistantMessage(
                "thread-node-completed",
                "node-1-output",
                "streaming",
                false
        );
        verify(chatMessageService).saveOrUpdateStreamingAssistantMessage(
                "thread-node-completed",
                "node-1-outputnode-2-output",
                "text",
                true
        );
    }

    @Test
    void chatPersistsClarificationAnswerAsUserMessage() {
        GraphRequest request = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-clarification")
                .query("")
                .interactionType("CLARIFICATION_ANSWER")
                .interactionContent("核心瓶颈按平均耗时衡量")
                .build();

        when(chatSessionService.findBySessionId("thread-clarification"))
                .thenReturn(ChatSessionVO.builder().id("thread-clarification").agentId(2).build());
        when(chatMessageService.getMultiTurnContext("thread-clarification", 10)).thenReturn("(none)");
        when(graphService.chatStream(any(GraphRequest.class), anyString()))
                .thenReturn(Flux.just(GraphStreamChunk.content("正在归纳您的澄清...", "node-1")));

        controller.chat(request).collectList().block();

        ArgumentCaptor<ChatMessageDTO> captor = ArgumentCaptor.forClass(ChatMessageDTO.class);
        verify(chatMessageService).saveMessage(captor.capture(), anyString());
        ChatMessageDTO saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo("user");
        assertThat(saved.getContent()).isEqualTo("核心瓶颈按平均耗时衡量");
        assertThat(saved.getMessageType()).isEqualTo("text");
    }

    @Test
    void chatPersistsHumanFeedbackAsUserMessage() {
        GraphRequest request = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-human-feedback")
                .query("")
                .humanFeedbackContent("可以的，就按这个来")
                .rejectedPlan(true)
                .build();

        when(chatSessionService.findBySessionId("thread-human-feedback"))
                .thenReturn(ChatSessionVO.builder().id("thread-human-feedback").agentId(2).build());
        when(chatMessageService.getMultiTurnContext("thread-human-feedback", 10)).thenReturn("(none)");
        when(graphService.chatStream(any(GraphRequest.class), anyString()))
                .thenReturn(Flux.just(GraphStreamChunk.content("正在尝试重新设计计划...", "node-1")));

        controller.chat(request).collectList().block();

        ArgumentCaptor<ChatMessageDTO> captor = ArgumentCaptor.forClass(ChatMessageDTO.class);
        verify(chatMessageService).saveMessage(captor.capture(), eq("thread-human-feedback"));
        ChatMessageDTO saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo("user");
        assertThat(saved.getContent()).isEqualTo("可以的，就按这个来");
        assertThat(saved.getMessageType()).isEqualTo("text");
    }

    @Test
    void chatPersistsHumanApprovalAsStartTaskUserMessage() {
        GraphRequest request = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-human-approve")
                .query("")
                .humanFeedbackContent("确认执行")
                .rejectedPlan(false)
                .build();

        when(chatSessionService.findBySessionId("thread-human-approve"))
                .thenReturn(ChatSessionVO.builder().id("thread-human-approve").agentId(2).build());
        when(chatMessageService.getMultiTurnContext("thread-human-approve", 10)).thenReturn("(none)");
        when(graphService.chatStream(any(GraphRequest.class), anyString()))
                .thenReturn(Flux.just(GraphStreamChunk.content("开始执行任务...", "node-1")));

        controller.chat(request).collectList().block();

        ArgumentCaptor<ChatMessageDTO> captor = ArgumentCaptor.forClass(ChatMessageDTO.class);
        verify(chatMessageService).saveMessage(captor.capture(), eq("thread-human-approve"));
        ChatMessageDTO saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo("user");
        assertThat(saved.getContent()).isEqualTo("开始任务");
        assertThat(saved.getMessageType()).isEqualTo("text");
    }

    @Test
    void chatPersistsOnlyCompletedNodeContentOnCancel() {
        GraphRequest request = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-cancel-with-partial-node")
                .query("query")
                .build();

        when(chatSessionService.findBySessionId("thread-cancel-with-partial-node"))
                .thenReturn(ChatSessionVO.builder().id("thread-cancel-with-partial-node").agentId(2).build());
        when(chatMessageService.getMultiTurnContext("thread-cancel-with-partial-node", 10)).thenReturn("(none)");
        when(graphService.chatStream(any(GraphRequest.class), anyString()))
                .thenReturn(Flux.just(
                        GraphStreamChunk.content("completed-node-output", "node-1"),
                        GraphStreamChunk.nodeCompleted("node-1"),
                        GraphStreamChunk.content("partial-node-output", "node-2")
                ));

        List<String> chunks = controller.chat(request).take(2).collectList().block();
        assertThat(chunks).containsExactly("completed-node-output", "partial-node-output");

        verify(chatMessageService).saveOrUpdateStreamingAssistantMessage(
                "thread-cancel-with-partial-node",
                "completed-node-output",
                "streaming",
                false
        );
        verify(chatMessageService).saveOrUpdateStreamingAssistantMessage(
                "thread-cancel-with-partial-node",
                "completed-node-output",
                "text",
                true
        );
        verify(chatMessageService, never()).saveOrUpdateStreamingAssistantMessage(
                eq("thread-cancel-with-partial-node"),
                eq("completed-node-outputpartial-node-output"),
                eq("text"),
                eq(true)
        );
    }

    @Test
    void chatMarksInterruptedWithoutPersistingPartialNodeOnCancel() {
        GraphRequest request = GraphRequest.builder()
                .agentId("2")
                .threadId("thread-running")
                .query("query")
                .build();

        when(chatSessionService.findBySessionId("thread-running"))
                .thenReturn(ChatSessionVO.builder().id("thread-running").agentId(2).build());
        when(chatMessageService.getMultiTurnContext("thread-running", 10)).thenReturn("(none)");
        when(graphService.chatStream(any(GraphRequest.class), anyString()))
                .thenReturn(Flux.just(
                        GraphStreamChunk.content("node-1-output", "node-1"),
                        GraphStreamChunk.content("node-2-output", "node-1")
                ));

        List<String> chunks = controller.chat(request).take(1).collectList().block();
        assertThat(chunks).containsExactly("node-1-output");

        verify(chatMessageService, never()).saveOrUpdateStreamingAssistantMessage(
                eq("thread-running"),
                anyString(),
                anyString(),
                eq(true)
        );
        verify(workflowRunService).markInterrupted("thread-running", "客户端连接断开");
    }
}
