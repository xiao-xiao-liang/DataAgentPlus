package com.liang.data.agent.service.knowledge.chunk;

import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import com.liang.data.agent.service.knowledge.chunk.impl.AiChunkNameGenerator;
import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkMessage;
import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkNameConsumer;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 知识分块名称生成消费者单元测试。
 */
class KnowledgeChunkNameConsumerTest {

    private final AgentKnowledgeChunkMapper chunkMapper = mock(AgentKnowledgeChunkMapper.class);
    private final ChunkNameGenerator nameGenerator = mock(ChunkNameGenerator.class);
    private final KnowledgeChunkNameConsumer consumer = new KnowledgeChunkNameConsumer(chunkMapper, nameGenerator);
    private final KnowledgeChunkMessage message = new KnowledgeChunkMessage(1, 10, "knowledge-10-chunk-3", 4);

    @Test
    void shouldPersistGeneratedNameWhenVersionAndLockMatch() {
        AgentKnowledgeChunkEntity chunk = chunk(4, 0);
        when(chunkMapper.selectOne(any())).thenReturn(chunk);
        when(nameGenerator.generate(chunk.getContent(), chunk.getChunkOrder())).thenReturn("运营延误判断");
        when(chunkMapper.updateNameIfUnlocked(message.chunkId(), 4, "运营延误判断")).thenReturn(1);

        consumer.onMessage(message);

        verify(chunkMapper).updateNameIfUnlocked(message.chunkId(), 4, "运营延误判断");
    }

    @Test
    void shouldIgnoreLockedOrStaleChunk() {
        when(chunkMapper.selectOne(any())).thenReturn(chunk(4, 1), chunk(5, 0));

        consumer.onMessage(message);
        consumer.onMessage(message);

        verifyNoInteractions(nameGenerator);
        verify(chunkMapper, never()).updateNameIfUnlocked(any(), any(), any());
    }

    @Test
    void aiGeneratorShouldFallbackToFirstNonBlankLine() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.callUser(any())).thenReturn(Flux.error(new IllegalStateException("模型不可用")));
        AiChunkNameGenerator generator = new AiChunkNameGenerator(llmService, new KnowledgeChunkProperties());

        assertThat(generator.generate("\n  第一行名称  \n第二行", 3)).isEqualTo("第一行名称");
        assertThat(generator.generate(" \n ", 3)).isEqualTo("分块 #3");
    }

    private AgentKnowledgeChunkEntity chunk(int contentVersion, int nameLocked) {
        AgentKnowledgeChunkEntity chunk = new AgentKnowledgeChunkEntity();
        chunk.setKnowledgeId(10);
        chunk.setChunkId(message.chunkId());
        chunk.setChunkOrder(3);
        chunk.setContent("延误判断规则");
        chunk.setContentVersion(contentVersion);
        chunk.setNameLocked(nameLocked);
        return chunk;
    }
}
