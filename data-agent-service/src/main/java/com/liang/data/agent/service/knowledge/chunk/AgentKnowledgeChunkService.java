package com.liang.data.agent.service.knowledge.chunk;

import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkDetailVO;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkOutlineVO;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkUpdateRequest;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkUpdateResultVO;

import java.util.List;

/**
 * 知识分块工作台服务。
 */
public interface AgentKnowledgeChunkService {

    List<KnowledgeChunkOutlineVO> listOutlines(Integer agentId, Integer knowledgeId, String keyword, String vectorStatus);

    KnowledgeChunkDetailVO getDetail(Integer agentId, Integer knowledgeId, String chunkId);

    KnowledgeChunkUpdateResultVO update(Integer agentId, Integer knowledgeId, String chunkId,
                                        KnowledgeChunkUpdateRequest request);

    KnowledgeChunkUpdateResultVO retry(Integer agentId, Integer knowledgeId, String chunkId);

    KnowledgeChunkUpdateResultVO generateName(Integer agentId, Integer knowledgeId, String chunkId);
}
