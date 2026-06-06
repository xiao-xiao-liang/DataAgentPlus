package com.liang.data.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.service.knowledge.chunk.AgentKnowledgeChunkService;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkDetailVO;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkOutlineVO;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkUpdateRequest;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkUpdateResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 知识分块工作台接口单元测试。
 */
class AgentKnowledgeChunkControllerTest {

    private final AgentKnowledgeChunkService service = mock(AgentKnowledgeChunkService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AgentKnowledgeChunkController(service)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listShouldForwardSearchAndStatus() throws Exception {
        when(service.listOutlines(1, 10, "延误", "FAILED"))
                .thenReturn(List.of(new KnowledgeChunkOutlineVO().setId("chunk-1").setName("延误规则")));

        mockMvc.perform(get("/api/v1/agent-knowledge/10/chunks")
                        .param("agentId", "1")
                        .param("keyword", "延误")
                        .param("vectorStatus", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].name").value("延误规则"));
    }

    @Test
    void updateShouldReturnMessageSubmissionState() throws Exception {
        KnowledgeChunkDetailVO detail = new KnowledgeChunkDetailVO();
        detail.setId("chunk-1");
        when(service.update(any(), any(), any(), any()))
                .thenReturn(new KnowledgeChunkUpdateResultVO(detail, false));
        KnowledgeChunkUpdateRequest request = new KnowledgeChunkUpdateRequest();
        request.setName("延误规则");
        request.setContent("正文");
        request.setContentVersion(2);
        request.setManualNameChanged(true);

        mockMvc.perform(put("/api/v1/agent-knowledge/10/chunks/chunk-1")
                        .param("agentId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageSubmitted").value(false));
    }

    @Test
    void retryAndGenerateNameShouldForwardCommands() throws Exception {
        when(service.retry(1, 10, "chunk-1"))
                .thenReturn(new KnowledgeChunkUpdateResultVO(new KnowledgeChunkDetailVO(), true));
        when(service.generateName(1, 10, "chunk-1"))
                .thenReturn(new KnowledgeChunkUpdateResultVO(new KnowledgeChunkDetailVO(), true));

        mockMvc.perform(post("/api/v1/agent-knowledge/10/chunks/chunk-1/retry").param("agentId", "1"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/agent-knowledge/10/chunks/chunk-1/generate-name").param("agentId", "1"))
                .andExpect(status().isOk());

        verify(service).retry(1, 10, "chunk-1");
        verify(service).generateName(1, 10, "chunk-1");
    }
}
