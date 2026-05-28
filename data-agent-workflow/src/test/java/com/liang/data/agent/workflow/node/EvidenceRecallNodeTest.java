package com.liang.data.agent.workflow.node;

import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.vectorstore.AgentVectorStoreService;
import com.liang.data.agent.common.enums.VectorType;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.dal.mapper.AgentKnowledgeMapper;
import com.liang.data.agent.workflow.dto.node.EvidenceQueryRewriteDTO;
import com.liang.data.agent.workflow.util.JsonParseUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;

import static com.liang.data.agent.common.constant.NodeOutputKey.EVIDENCE_OUTPUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceRecallNodeTest {

    @Test
    void shouldReturnKnowledgeEvidenceWhenKnowledgeVectorStoreSucceeds() {
        AgentVectorStoreService vectorStoreService = mock(AgentVectorStoreService.class);
        JsonParseUtil jsonParseUtil = mock(JsonParseUtil.class);

        EvidenceQueryRewriteDTO dto = new EvidenceQueryRewriteDTO();
        dto.setStandaloneQuery("重写后的问题");
        when(jsonParseUtil.tryConvertToObject(anyString(), eq(EvidenceQueryRewriteDTO.class))).thenReturn(dto);
        when(vectorStoreService.search("test-agent-001", "重写后的问题", VectorType.KNOWLEDGE))
                .thenReturn(List.of(new Document("知识证据")));

        EvidenceRecallNode node = new EvidenceRecallNode(
                mock(LlmService.class), jsonParseUtil, vectorStoreService, mock(AgentKnowledgeMapper.class));

        Map<String, ?> result = ReflectionTestUtils.invokeMethod(
                node,
                "getEvidences",
                "```json\n{\"standalone_query\":\"重写后的问题\"}\n```",
                "test-agent-001",
                Sinks.many().replay().all()
        );

        assertTrue(result.get(EVIDENCE_OUTPUT).toString().contains("知识证据"));
        verify(vectorStoreService).search("test-agent-001", "重写后的问题", VectorType.KNOWLEDGE);
    }

    @Test
    void shouldReturnNoWhenStandaloneQueryIsBlank() {
        AgentVectorStoreService vectorStoreService = mock(AgentVectorStoreService.class);
        JsonParseUtil jsonParseUtil = mock(JsonParseUtil.class);

        EvidenceQueryRewriteDTO dto = new EvidenceQueryRewriteDTO();
        dto.setStandaloneQuery("   ");
        when(jsonParseUtil.tryConvertToObject(anyString(), eq(EvidenceQueryRewriteDTO.class))).thenReturn(dto);

        EvidenceRecallNode node = new EvidenceRecallNode(
                mock(LlmService.class), jsonParseUtil, vectorStoreService, mock(AgentKnowledgeMapper.class));

        Map<String, ?> result = ReflectionTestUtils.invokeMethod(
                node,
                "getEvidences",
                "```json\n{\"standalone_query\":\"\"}\n```",
                "test-agent-001",
                Sinks.many().replay().all()
        );

        assertEquals("无", result.get(EVIDENCE_OUTPUT));
    }

    @Test
    void shouldReturnNoWhenNoDocumentsFound() {
        AgentVectorStoreService vectorStoreService = mock(AgentVectorStoreService.class);
        JsonParseUtil jsonParseUtil = mock(JsonParseUtil.class);

        EvidenceQueryRewriteDTO dto = new EvidenceQueryRewriteDTO();
        dto.setStandaloneQuery("重写后的问题");
        when(jsonParseUtil.tryConvertToObject(anyString(), eq(EvidenceQueryRewriteDTO.class))).thenReturn(dto);
        when(vectorStoreService.search("test-agent-001", "重写后的问题", VectorType.KNOWLEDGE)).thenReturn(List.of());
        when(vectorStoreService.search("test-agent-001", "重写后的问题", VectorType.BUSINESS_TERM)).thenReturn(List.of());

        EvidenceRecallNode node = new EvidenceRecallNode(
                mock(LlmService.class), jsonParseUtil, vectorStoreService, mock(AgentKnowledgeMapper.class));

        Map<String, ?> result = ReflectionTestUtils.invokeMethod(
                node,
                "getEvidences",
                "```json\n{\"standalone_query\":\"重写后的问题\"}\n```",
                "test-agent-001",
                Sinks.many().replay().all()
        );

        assertEquals("无", result.get(EVIDENCE_OUTPUT));
    }

    @Test
    void shouldKeepAvailableDocumentsWhenSingleVectorStoreFails() {
        AgentVectorStoreService vectorStoreService = mock(AgentVectorStoreService.class);
        JsonParseUtil jsonParseUtil = mock(JsonParseUtil.class);

        EvidenceQueryRewriteDTO dto = new EvidenceQueryRewriteDTO();
        dto.setStandaloneQuery("重写后的问题");
        when(jsonParseUtil.tryConvertToObject(anyString(), eq(EvidenceQueryRewriteDTO.class))).thenReturn(dto);
        doThrow(new ServiceException("vector down"))
                .when(vectorStoreService).search("test-agent-001", "重写后的问题", VectorType.KNOWLEDGE);
        when(vectorStoreService.search("test-agent-001", "重写后的问题", VectorType.BUSINESS_TERM))
                .thenReturn(List.of(new Document("业务术语证据")));

        EvidenceRecallNode node = new EvidenceRecallNode(
                mock(LlmService.class), jsonParseUtil, vectorStoreService, mock(AgentKnowledgeMapper.class));

        Map<String, ?> result = ReflectionTestUtils.invokeMethod(
                node,
                "getEvidences",
                "```json\n{\"standalone_query\":\"重写后的问题\"}\n```",
                "test-agent-001",
                Sinks.many().replay().all()
        );

        assertTrue(result.get(EVIDENCE_OUTPUT).toString().contains("业务术语证据"));
    }

    @Test
    void shouldExposeStableVectorTypeCodes() {
        assertEquals("KNOWLEDGE", VectorType.KNOWLEDGE.getCode());
        assertEquals("BUSINESS_TERM", VectorType.fromCode("BUSINESS_TERM").getCode());
        assertThrows(ServiceException.class, () -> VectorType.fromCode("UNKNOWN"));
    }
}
