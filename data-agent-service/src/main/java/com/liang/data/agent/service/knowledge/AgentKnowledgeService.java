package com.liang.data.agent.service.knowledge;

import com.liang.data.agent.service.knowledge.vo.AgentKnowledgeChunkVO;
import com.liang.data.agent.service.knowledge.vo.AgentKnowledgeVO;

import java.io.InputStream;
import java.util.List;

/**
 * 智能体知识源服务。
 *
 * <p>负责知识文件的存储、切分、向量化和查询。</p>
 */
public interface AgentKnowledgeService {

    /**
     * 查询智能体下的知识文件列表。
     *
     * @param agentId 智能体 ID
     * @return 知识文件列表
     */
    List<AgentKnowledgeVO> listByAgent(Integer agentId);

    /**
     * 上传并向量化知识文件。
     *
     * @param agentId        智能体 ID
     * @param title          知识标题
     * @param sourceFilename 原始文件名
     * @param splitterType   切分策略
     * @return 知识文件信息
     */
    AgentKnowledgeVO upload(Integer agentId, Long userId, String title, String sourceFilename, InputStream inputStream,
                            long contentLength, String splitterType);

    /**
     * 查询知识文件的切分片段。
     *
     * @param id 知识文件 ID
     * @return 切分片段列表
     */
    List<AgentKnowledgeChunkVO> listChunks(Integer agentId, Integer id);

    /**
     * 删除知识文件，并清理向量数据。
     *
     * @param id 知识文件 ID
     */
    void delete(Integer agentId, Integer id);
}
