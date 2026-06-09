package com.liang.data.agent.controller;

import com.liang.data.agent.service.knowledge.AgentKnowledgeService;
import com.liang.data.agent.service.knowledge.vo.AgentKnowledgeVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 智能体知识接口单元测试。
 */
class AgentKnowledgeControllerTest {

    @Test
    void uploadShouldUseFileStreamWithoutReadingWholeFileIntoMemory() throws Exception {
        AgentKnowledgeService service = mock(AgentKnowledgeService.class);
        AgentKnowledgeController controller = new AgentKnowledgeController(service);
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("metro.md");
        when(file.getSize()).thenReturn(12L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("hello".getBytes()));
        when(service.upload(eq(1), eq(1001L), eq("metro"), eq("metro.md"), any(InputStream.class), eq(12L), eq("title")))
                .thenReturn(new AgentKnowledgeVO().setId(7));

        controller.upload(1, 1001L, "metro", "title", file);

        verify(file).getInputStream();
        verify(file, never()).getBytes();
        ArgumentCaptor<InputStream> streamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(service).upload(eq(1), eq(1001L), eq("metro"), eq("metro.md"), streamCaptor.capture(), eq(12L), eq("title"));
        assertThat(streamCaptor.getValue()).isNotNull();
    }
}
