package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.AgentKnowledgeJobEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 智能体知识处理任务 Mapper。
 */
@Mapper
public interface AgentKnowledgeJobMapper extends BaseMapper<AgentKnowledgeJobEntity> {

    /**
     * 统计当前知识任务前方等待任务数。
     *
     * @param agentId 智能体 ID
     * @param jobType 任务类型
     * @param id      当前任务主键
     * @return 前方任务数
     */
    @Select("""
            SELECT COUNT(1) FROM agent_knowledge_job
            WHERE agent_id = #{agentId}
              AND job_type = #{jobType}
              AND status IN ('PENDING', 'RETRYING')
              AND id < #{id}
            """)
    long countAheadJobs(@Param("agentId") Integer agentId, @Param("jobType") String jobType, @Param("id") Long id);

    /**
     * 统计当前知识任务前方等待用户数。
     *
     * @param agentId 智能体 ID
     * @param jobType 任务类型
     * @param id      当前任务主键
     * @return 前方用户数
     */
    @Select("""
            SELECT COUNT(DISTINCT user_id) FROM agent_knowledge_job
            WHERE agent_id = #{agentId}
              AND job_type = #{jobType}
              AND status IN ('PENDING', 'RETRYING')
              AND id < #{id}
            """)
    long countAheadUsers(@Param("agentId") Integer agentId, @Param("jobType") String jobType, @Param("id") Long id);
}
