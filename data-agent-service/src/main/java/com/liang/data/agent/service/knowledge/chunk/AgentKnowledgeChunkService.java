package com.liang.data.agent.service.knowledge.chunk;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkDetailVO;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkOutlineVO;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkUpdateRequest;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkUpdateResultVO;

import java.util.List;

/**
 * 知识分块工作台服务。
 */
public interface AgentKnowledgeChunkService extends IService<AgentKnowledgeChunkEntity> {

    /**
     * 查询知识文件的分块大纲。
     */
    List<KnowledgeChunkOutlineVO> listOutlines(Integer agentId, Integer knowledgeId, String keyword, String vectorStatus);

    /**
     * 查询分块编辑详情。
     */
    KnowledgeChunkDetailVO getDetail(Integer agentId, Integer knowledgeId, String chunkId);

    /**
     * 保存分块名称或正文。
     */
    KnowledgeChunkUpdateResultVO update(Integer agentId, Integer knowledgeId, String chunkId,
                                        KnowledgeChunkUpdateRequest request);

    /**
     * 重新提交失败的向量任务。
     */
    KnowledgeChunkUpdateResultVO retry(Integer agentId, Integer knowledgeId, String chunkId);

    /**
     * 恢复超时的向量任务。
     */
    KnowledgeChunkUpdateResultVO recover(Integer agentId, Integer knowledgeId, String chunkId);

    /**
     * 提交分块 AI 命名任务。
     */
    KnowledgeChunkUpdateResultVO generateName(Integer agentId, Integer knowledgeId, String chunkId);
}
