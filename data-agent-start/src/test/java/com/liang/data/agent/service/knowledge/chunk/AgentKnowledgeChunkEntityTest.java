package com.liang.data.agent.service.knowledge.chunk;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识分块持久化字段与乐观更新能力测试。
 */
class AgentKnowledgeChunkEntityTest {

    @Test
    void shouldAccessEditingAndVectorVersionFields() {
        AgentKnowledgeChunkEntity chunk = new AgentKnowledgeChunkEntity();

        chunk.setName("第一节");
        chunk.setNameLocked(1);
        chunk.setContentVersion(3);
        chunk.setVectorVersion(2);
        chunk.setVectorStatus(ChunkVectorStatus.SYNCED);
        chunk.setRetryCount(4);

        assertThat(chunk.getName()).isEqualTo("第一节");
        assertThat(chunk.getNameLocked()).isEqualTo(1);
        assertThat(chunk.getContentVersion()).isEqualTo(3);
        assertThat(chunk.getVectorVersion()).isEqualTo(2);
        assertThat(chunk.getVectorStatus()).isEqualTo("SYNCED");
        assertThat(chunk.getRetryCount()).isEqualTo(4);
    }

    @Test
    void updateContentWithVersionShouldUseChunkIdAndContentVersionConditions() {
        AgentKnowledgeChunkMapper mapper = mock(AgentKnowledgeChunkMapper.class, CALLS_REAL_METHODS);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        int updated = mapper.updateContentWithVersion("chunk-1", 3, "新名称", 1, "新内容", 3);

        assertThat(updated).isEqualTo(1);
        UpdateWrapper<AgentKnowledgeChunkEntity> wrapper = captureUpdateWrapper(mapper);
        assertOptimisticConditions(wrapper, "chunk-1", 3);
        assertThat(wrapper.getSqlSet()).contains("name=", "name_locked=", "content=", "content_length=",
                "content_version = content_version + 1");
    }

    @Test
    void updateVectorStatusIfCurrentShouldUseChunkIdAndContentVersionConditions() {
        AgentKnowledgeChunkMapper mapper = mock(AgentKnowledgeChunkMapper.class, CALLS_REAL_METHODS);
        when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        int updated = mapper.updateVectorStatusIfCurrent(
                "chunk-2", 5, ChunkVectorStatus.FAILED, 4, 2, "向量同步失败"
        );

        assertThat(updated).isEqualTo(1);
        UpdateWrapper<AgentKnowledgeChunkEntity> wrapper = captureUpdateWrapper(mapper);
        assertOptimisticConditions(wrapper, "chunk-2", 5);
        assertThat(wrapper.getSqlSet()).contains(
                "vector_status=", "vector_version=", "retry_count=", "error_msg="
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private UpdateWrapper<AgentKnowledgeChunkEntity> captureUpdateWrapper(AgentKnowledgeChunkMapper mapper) {
        ArgumentCaptor<Wrapper<AgentKnowledgeChunkEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        return (UpdateWrapper<AgentKnowledgeChunkEntity>) captor.getValue();
    }

    private void assertOptimisticConditions(UpdateWrapper<AgentKnowledgeChunkEntity> wrapper,
                                            String chunkId,
                                            Integer contentVersion) {
        assertThat(wrapper.getSqlSegment()).contains("chunk_id", "content_version");
        Map<String, Object> parameters = wrapper.getParamNameValuePairs();
        assertThat(parameters).containsValue(chunkId).containsValue(contentVersion);
    }
}
