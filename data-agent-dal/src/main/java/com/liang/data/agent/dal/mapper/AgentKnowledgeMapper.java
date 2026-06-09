package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.AgentKnowledgeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 智能体知识源管理表 Mapper
 */
@Mapper
public interface AgentKnowledgeMapper extends BaseMapper<AgentKnowledgeEntity> {

    /**
     * 当知识文档仍处于处理中或失败后重试成功时，将其标记为向量化完成。
     */
    @Update("""
            UPDATE agent_knowledge
            SET embedding_status = 'COMPLETED',
                error_msg = NULL,
                update_time = NOW()
            WHERE id = #{knowledgeId}
              AND embedding_status IN ('PROCESSING', 'FAILED')
              AND del_flag = 0
            """)
    int completeEmbeddingIfProcessing(@Param("knowledgeId") Integer knowledgeId);

    /**
     * 当知识文档仍处于处理中时，将其标记为向量化失败。
     */
    @Update("""
            UPDATE agent_knowledge
            SET embedding_status = 'FAILED',
                error_msg = #{errorMsg},
                update_time = NOW()
            WHERE id = #{knowledgeId}
              AND embedding_status = 'PROCESSING'
              AND del_flag = 0
            """)
    int failEmbeddingIfProcessing(@Param("knowledgeId") Integer knowledgeId,
                                  @Param("errorMsg") String errorMsg);
}
