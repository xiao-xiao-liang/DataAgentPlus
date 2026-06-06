package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能体知识分块表 Mapper。
 */
@Mapper
public interface AgentKnowledgeChunkMapper extends BaseMapper<AgentKnowledgeChunkEntity> {

    /**
     * 按内容版本更新分块可编辑内容。
     *
     * @param chunkId         分块业务 ID
     * @param expectedVersion 预期内容版本
     * @param name            分块名称
     * @param nameLocked      名称是否锁定
     * @param content         分块内容
     * @param contentLength   分块内容长度
     * @return 更新记录数
     */
    default int updateContentWithVersion(String chunkId,
                                         Integer expectedVersion,
                                         String name,
                                         Integer nameLocked,
                                         String content,
                                         Integer contentLength) {
        UpdateWrapper<AgentKnowledgeChunkEntity> wrapper = new UpdateWrapper<>();
        wrapper.eq("chunk_id", chunkId)
                .eq("content_version", expectedVersion)
                .set("name", name)
                .set("name_locked", nameLocked)
                .set("content", content)
                .set("content_length", contentLength)
                .setSql("content_version = content_version + 1");
        return update(null, wrapper);
    }

    /**
     * 在内容版本未变化时更新向量同步状态。
     *
     * @param chunkId        分块业务 ID
     * @param contentVersion 当前内容版本
     * @param vectorStatus   向量同步状态
     * @param vectorVersion  向量版本
     * @param retryCount     重试次数
     * @param errorMsg       错误信息
     * @return 更新记录数
     */
    default int updateVectorStatusIfCurrent(String chunkId,
                                            Integer contentVersion,
                                            String vectorStatus,
                                            Integer vectorVersion,
                                            Integer retryCount,
                                            String errorMsg) {
        UpdateWrapper<AgentKnowledgeChunkEntity> wrapper = new UpdateWrapper<>();
        wrapper.eq("chunk_id", chunkId)
                .eq("content_version", contentVersion)
                .set("vector_status", vectorStatus)
                .set("vector_version", vectorVersion)
                .set("retry_count", retryCount)
                .set("error_msg", errorMsg);
        return update(null, wrapper);
    }
}
