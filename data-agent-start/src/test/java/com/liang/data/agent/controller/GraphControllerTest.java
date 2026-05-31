package com.liang.data.agent.controller;

import com.liang.data.agent.service.agent.AgentService;
import com.liang.data.agent.service.chat.ChatMessageService;
import com.liang.data.agent.service.chat.ChatSessionService;
import com.liang.data.agent.service.chat.SessionTitleService;
import com.liang.data.agent.service.chat.dto.ChatMessageDTO;
import com.liang.data.agent.service.chat.vo.ChatSessionVO;
import com.liang.data.agent.workflow.dto.GraphRequest;
import com.liang.data.agent.workflow.service.GraphService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphControllerTest {

    private final GraphService graphService = mock(GraphService.class);
    private final ChatSessionService chatSessionService = mock(ChatSessionService.class);
    private final ChatMessageService chatMessageService = mock(ChatMessageService.class);
    private final SessionTitleService sessionTitleService = mock(SessionTitleService.class);
    private final AgentService agentService = mock(AgentService.class);

    private final GraphController controller = new GraphController(
            graphService,
            chatSessionService,
            chatMessageService,
            sessionTitleService,
            agentService
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
        when(graphService.chat(any(GraphRequest.class), anyString()))
                .thenReturn(Flux.error(new RuntimeException("boom")));

        List<String> chunks = controller.chat(request).collectList().block();

        assertThat(String.join("", chunks)).contains("data:");
        assertThat(String.join("", chunks)).contains("系统繁忙，请稍后再试");
        verify(chatMessageService).saveMessageAsync(any(), anyString());
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
        when(graphService.chat(any(GraphRequest.class), anyString()))
                .thenReturn(Flux.just("正在归纳您的澄清..."));

        controller.chat(request).collectList().block();

        ArgumentCaptor<ChatMessageDTO> captor = ArgumentCaptor.forClass(ChatMessageDTO.class);
        verify(chatMessageService).saveMessage(captor.capture(), anyString());
        ChatMessageDTO saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo("user");
        assertThat(saved.getContent()).isEqualTo("核心瓶颈按平均耗时衡量");
        assertThat(saved.getMessageType()).isEqualTo("text");
    }
}
