package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体知识分块表 Mapper。
 *
 * <p>普通单表操作由 MyBatis-Plus Service 完成，本接口仅保留需要原子 CAS 或批量版本查询的 SQL。</p>
 */
@Mapper
public interface AgentKnowledgeChunkMapper extends BaseMapper<AgentKnowledgeChunkEntity> {

    /**
     * 仅更新分块名称，不触发向量化。
     */
    @Update("""
            UPDATE agent_knowledge_chunk
            SET name = #{name}, name_locked = 1
            WHERE chunk_id = #{chunkId}
              AND content_version = #{contentVersion}
              AND del_flag = 0
            """)
    int updateNameOnly(@Param("chunkId") String chunkId,
                       @Param("contentVersion") Integer contentVersion,
                       @Param("name") String name);

    /**
     * 更新正文并创建新向量任务。
     */
    @Update("""
            UPDATE agent_knowledge_chunk
            SET name = #{name},
                name_locked = #{nameLocked},
                content = #{content},
                content_length = #{contentLength},
                content_version = content_version + 1,
                vector_task_version = vector_task_version + 1,
                vector_status = 'PENDING',
                vector_processing_started_at = NULL,
                retry_count = 0,
                error_msg = NULL
            WHERE chunk_id = #{chunkId}
              AND content_version = #{expectedContentVersion}
              AND vector_task_version = #{expectedTaskVersion}
              AND vector_status IN ('SYNCED', 'FAILED')
              AND del_flag = 0
            """)
    int updateContentAndCreateTask(@Param("chunkId") String chunkId,
                                   @Param("expectedContentVersion") Integer expectedContentVersion,
                                   @Param("expectedTaskVersion") Integer expectedTaskVersion,
                                   @Param("name") String name,
                                   @Param("nameLocked") Integer nameLocked,
                                   @Param("content") String content,
                                   @Param("contentLength") Integer contentLength);

    /**
     * 将失败任务重置为新的等待任务。
     */
    @Update("""
            UPDATE agent_knowledge_chunk
            SET vector_task_version = vector_task_version + 1,
                vector_status = 'PENDING',
                vector_processing_started_at = NULL,
                retry_count = 0,
                error_msg = NULL
            WHERE chunk_id = #{chunkId}
              AND content_version = #{contentVersion}
              AND vector_task_version = #{taskVersion}
              AND vector_status = 'FAILED'
              AND del_flag = 0
            """)
    int retryFailedTask(@Param("chunkId") String chunkId,
                        @Param("contentVersion") Integer contentVersion,
                        @Param("taskVersion") Integer taskVersion);

    /**
     * 领取当前等待任务。
     */
    @Update("""
            UPDATE agent_knowledge_chunk
            SET vector_status = 'PROCESSING',
                vector_processing_started_at = #{startedAt}
            WHERE chunk_id = #{chunkId}
              AND content_version = #{contentVersion}
              AND vector_task_version = #{taskVersion}
              AND vector_status = 'PENDING'
              AND del_flag = 0
            """)
    int claimVectorProcessing(@Param("chunkId") String chunkId,
                              @Param("contentVersion") Integer contentVersion,
                              @Param("taskVersion") Integer taskVersion,
                              @Param("startedAt") LocalDateTime startedAt);

    /**
     * 完成当前处理中的任务并切换有效向量。
     */
    @Update("""
            UPDATE agent_knowledge_chunk
            SET vector_status = 'SYNCED',
                vector_version = #{contentVersion},
                embedding_id = #{embeddingId},
                vector_processing_started_at = NULL,
                retry_count = 0,
                error_msg = NULL
            WHERE chunk_id = #{chunkId}
              AND content_version = #{contentVersion}
              AND vector_task_version = #{taskVersion}
              AND vector_status = 'PROCESSING'
              AND del_flag = 0
            """)
    int completeVectorIfProcessing(@Param("chunkId") String chunkId,
                                   @Param("contentVersion") Integer contentVersion,
                                   @Param("taskVersion") Integer taskVersion,
                                   @Param("embeddingId") String embeddingId);

    /**
     * 统计指定知识文档下尚未同步完成的分块数量。
     */
    @Select("""
            SELECT COUNT(1)
            FROM agent_knowledge_chunk
            WHERE knowledge_id = #{knowledgeId}
              AND del_flag = 0
              AND vector_status <> 'SYNCED'
            """)
    int countUnfinishedVectorChunks(@Param("knowledgeId") Integer knowledgeId);

    /**
     * 记录可重试失败并恢复为等待状态。
     */
    @Update("""
            UPDATE agent_knowledge_chunk
            SET vector_status = 'PENDING',
                vector_processing_started_at = NULL,
                retry_count = retry_count + 1,
                error_msg = #{errorMsg}
            WHERE chunk_id = #{chunkId}
              AND content_version = #{contentVersion}
              AND vector_task_version = #{taskVersion}
              AND vector_status = 'PROCESSING'
              AND del_flag = 0
            """)
    int recordVectorRetry(@Param("chunkId") String chunkId,
                          @Param("contentVersion") Integer contentVersion,
                          @Param("taskVersion") Integer taskVersion,
                          @Param("errorMsg") String errorMsg);

    /**
     * 将当前等待或处理中的任务标记为最终失败。
     */
    @Update("""
            UPDATE agent_knowledge_chunk
            SET vector_status = 'FAILED',
                vector_processing_started_at = NULL,
                error_msg = #{errorMsg}
            WHERE chunk_id = #{chunkId}
              AND content_version = #{contentVersion}
              AND vector_task_version = #{taskVersion}
              AND vector_status IN ('PENDING', 'PROCESSING')
              AND del_flag = 0
            """)
    int markVectorFailedIfCurrent(@Param("chunkId") String chunkId,
                                  @Param("contentVersion") Integer contentVersion,
                                  @Param("taskVersion") Integer taskVersion,
                                  @Param("errorMsg") String errorMsg);

    /**
     * 恢复超时任务并创建新任务版本。
     */
    @Update("""
            UPDATE agent_knowledge_chunk
            SET vector_task_version = vector_task_version + 1,
                vector_status = 'PENDING',
                vector_processing_started_at = NULL,
                retry_count = 0,
                error_msg = NULL
            WHERE chunk_id = #{chunkId}
              AND content_version = #{contentVersion}
              AND vector_task_version = #{taskVersion}
              AND vector_status = #{status}
              AND (
                (vector_status = 'PENDING' AND update_time <= #{deadline})
                OR
                (vector_status = 'PROCESSING' AND vector_processing_started_at <= #{deadline})
              )
              AND del_flag = 0
            """)
    int recoverTimedOutTask(@Param("chunkId") String chunkId,
                            @Param("contentVersion") Integer contentVersion,
                            @Param("taskVersion") Integer taskVersion,
                            @Param("status") String status,
                            @Param("deadline") LocalDateTime deadline);

    /**
     * 在版本未变化且名称未锁定时写入 AI 生成名称。
     */
    @Update("""
            UPDATE agent_knowledge_chunk
            SET name = #{name}
            WHERE chunk_id = #{chunkId}
              AND content_version = #{contentVersion}
              AND name_locked = 0
              AND del_flag = 0
            """)
    int updateNameIfUnlocked(@Param("chunkId") String chunkId,
                             @Param("contentVersion") Integer contentVersion,
                             @Param("name") String name);

    /**
     * 解除当前正文版本的名称锁定。
     */
    @Update("""
            UPDATE agent_knowledge_chunk
            SET name_locked = 0
            WHERE chunk_id = #{chunkId}
              AND content_version = #{contentVersion}
              AND del_flag = 0
            """)
    int unlockName(@Param("chunkId") String chunkId, @Param("contentVersion") Integer contentVersion);

    /**
     * 查询达到恢复期限的任务。
     */
    @Select("""
            SELECT *
            FROM agent_knowledge_chunk
            WHERE del_flag = 0
              AND (
                (vector_status = 'PENDING' AND update_time <= #{deadline})
                OR
                (vector_status = 'PROCESSING' AND vector_processing_started_at <= #{deadline})
              )
            ORDER BY update_time ASC
            LIMIT #{limit}
            """)
    List<AgentKnowledgeChunkEntity> selectTimedOutTasks(@Param("deadline") LocalDateTime deadline,
                                                         @Param("limit") int limit);
}
