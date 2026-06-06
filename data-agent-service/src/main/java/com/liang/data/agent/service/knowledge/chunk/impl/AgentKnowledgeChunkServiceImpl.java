package com.liang.data.agent.service.knowledge.chunk.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.liang.data.agent.common.errorcode.BaseErrorCode;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.entity.AgentKnowledgeEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import com.liang.data.agent.dal.mapper.AgentKnowledgeMapper;
import com.liang.data.agent.service.knowledge.chunk.AgentKnowledgeChunkService;
import com.liang.data.agent.service.knowledge.chunk.KnowledgeChunkAsyncPublisher;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkDetailVO;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkOutlineVO;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkUpdateRequest;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkUpdateResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 知识分块工作台服务实现。
 *
 * <p>负责分块归属校验、乐观锁编辑以及异步任务发布。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentKnowledgeChunkServiceImpl implements AgentKnowledgeChunkService {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_CONTENT_LENGTH = 200_000;

    private final AgentKnowledgeMapper knowledgeMapper;
    private final AgentKnowledgeChunkMapper chunkMapper;
    private final KnowledgeChunkAsyncPublisher asyncPublisher;

    @Override
    public List<KnowledgeChunkOutlineVO> listOutlines(Integer agentId, Integer knowledgeId,
                                                      String keyword, String vectorStatus) {
        validateKnowledge(agentId, knowledgeId);
        var query = Wrappers.lambdaQuery(AgentKnowledgeChunkEntity.class)
                .eq(AgentKnowledgeChunkEntity::getKnowledgeId, knowledgeId)
                .orderByAsc(AgentKnowledgeChunkEntity::getChunkOrder);
        if (StringUtils.hasText(keyword)) {
            String normalized = keyword.trim();
            query.and(wrapper -> wrapper.like(AgentKnowledgeChunkEntity::getName, normalized)
                    .or().like(AgentKnowledgeChunkEntity::getContent, normalized));
        }
        if (StringUtils.hasText(vectorStatus)) {
            query.eq(AgentKnowledgeChunkEntity::getVectorStatus, vectorStatus.trim());
        }
        return chunkMapper.selectList(query).stream().map(this::toOutline).toList();
    }

    @Override
    public KnowledgeChunkDetailVO getDetail(Integer agentId, Integer knowledgeId, String chunkId) {
        validateKnowledge(agentId, knowledgeId);
        return toDetail(getChunk(knowledgeId, chunkId));
    }

    @Override
    public KnowledgeChunkUpdateResultVO update(Integer agentId, Integer knowledgeId, String chunkId,
                                               KnowledgeChunkUpdateRequest request) {
        validateKnowledge(agentId, knowledgeId);
        AgentKnowledgeChunkEntity current = getChunk(knowledgeId, chunkId);
        validateRequest(request);
        int nameLocked = Boolean.TRUE.equals(request.getManualNameChanged()) ? 1
                : Optional.ofNullable(current.getNameLocked()).orElse(0);
        int rows = chunkMapper.updateContentWithVersion(chunkId, request.getContentVersion(),
                request.getName().trim(), nameLocked, request.getContent(), request.getContent().length());
        if (rows == 0) {
            throw new ServiceException("分块已被其他操作更新，请重新加载", BaseErrorCode.CLIENT_ERROR);
        }
        AgentKnowledgeChunkEntity updated = getChunk(knowledgeId, chunkId);
        boolean submitted = publishVectorize(agentId, knowledgeId, chunkId, updated.getContentVersion());
        return new KnowledgeChunkUpdateResultVO(toDetail(updated), submitted);
    }

    @Override
    public KnowledgeChunkUpdateResultVO retry(Integer agentId, Integer knowledgeId, String chunkId) {
        validateKnowledge(agentId, knowledgeId);
        AgentKnowledgeChunkEntity current = getChunk(knowledgeId, chunkId);
        if (chunkMapper.resetVectorStatus(chunkId, current.getContentVersion()) == 0) {
            throw new ServiceException("分块已被其他操作更新，请重新加载", BaseErrorCode.CLIENT_ERROR);
        }
        AgentKnowledgeChunkEntity updated = getChunk(knowledgeId, chunkId);
        boolean submitted = publishVectorize(agentId, knowledgeId, chunkId, updated.getContentVersion());
        return new KnowledgeChunkUpdateResultVO(toDetail(updated), submitted);
    }

    @Override
    public KnowledgeChunkUpdateResultVO generateName(Integer agentId, Integer knowledgeId, String chunkId) {
        validateKnowledge(agentId, knowledgeId);
        AgentKnowledgeChunkEntity current = getChunk(knowledgeId, chunkId);
        if (chunkMapper.unlockName(chunkId, current.getContentVersion()) == 0) {
            throw new ServiceException("分块已被其他操作更新，请重新加载", BaseErrorCode.CLIENT_ERROR);
        }
        AgentKnowledgeChunkEntity updated = getChunk(knowledgeId, chunkId);
        boolean submitted = publishGenerateName(agentId, knowledgeId, chunkId, updated.getContentVersion());
        return new KnowledgeChunkUpdateResultVO(toDetail(updated), submitted);
    }

    private boolean publishVectorize(Integer agentId, Integer knowledgeId, String chunkId, Integer contentVersion) {
        try {
            return asyncPublisher.publishVectorize(agentId, knowledgeId, chunkId, contentVersion);
        } catch (RuntimeException exception) {
            log.warn("分块向量化消息提交失败，等待用户重试：chunkId={}，contentVersion={}", chunkId, contentVersion, exception);
            return false;
        }
    }

    private boolean publishGenerateName(Integer agentId, Integer knowledgeId, String chunkId, Integer contentVersion) {
        try {
            return asyncPublisher.publishGenerateName(agentId, knowledgeId, chunkId, contentVersion);
        } catch (RuntimeException exception) {
            log.warn("分块名称生成消息提交失败，等待用户重试：chunkId={}，contentVersion={}", chunkId, contentVersion, exception);
            return false;
        }
    }

    private void validateKnowledge(Integer agentId, Integer knowledgeId) {
        if (agentId == null || agentId <= 0 || knowledgeId == null || knowledgeId <= 0) {
            throw new ServiceException("智能体 ID 和知识文件 ID 不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        AgentKnowledgeEntity knowledge = knowledgeMapper.selectOne(Wrappers.lambdaQuery(AgentKnowledgeEntity.class)
                .eq(AgentKnowledgeEntity::getId, knowledgeId)
                .eq(AgentKnowledgeEntity::getAgentId, agentId));
        if (knowledge == null) {
            throw new ServiceException("知识文件不存在", BaseErrorCode.CLIENT_ERROR);
        }
    }

    private AgentKnowledgeChunkEntity getChunk(Integer knowledgeId, String chunkId) {
        if (!StringUtils.hasText(chunkId)) {
            throw new ServiceException("分块 ID 不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        return Optional.ofNullable(chunkMapper.selectOne(Wrappers.lambdaQuery(AgentKnowledgeChunkEntity.class)
                        .eq(AgentKnowledgeChunkEntity::getKnowledgeId, knowledgeId)
                        .eq(AgentKnowledgeChunkEntity::getChunkId, chunkId)))
                .orElseThrow(() -> new ServiceException("知识分块不存在", BaseErrorCode.CLIENT_ERROR));
    }

    private void validateRequest(KnowledgeChunkUpdateRequest request) {
        if (request == null || request.getContentVersion() == null || request.getContentVersion() <= 0) {
            throw new ServiceException("分块内容版本不能为空", BaseErrorCode.CLIENT_ERROR);
        }
        if (!StringUtils.hasText(request.getName()) || request.getName().trim().length() > MAX_NAME_LENGTH) {
            throw new ServiceException("分块名称不能为空且不能超过 255 个字符", BaseErrorCode.CLIENT_ERROR);
        }
        if (!StringUtils.hasText(request.getContent()) || request.getContent().length() > MAX_CONTENT_LENGTH) {
            throw new ServiceException("分块正文不能为空且不能超过 200000 个字符", BaseErrorCode.CLIENT_ERROR);
        }
    }

    private KnowledgeChunkOutlineVO toOutline(AgentKnowledgeChunkEntity entity) {
        return new KnowledgeChunkOutlineVO()
                .setId(entity.getChunkId())
                .setSeq(entity.getChunkOrder())
                .setName(entity.getName())
                .setLength(entity.getContentLength())
                .setContentVersion(entity.getContentVersion())
                .setVectorVersion(entity.getVectorVersion())
                .setVectorStatus(entity.getVectorStatus())
                .setUpdateTime(entity.getUpdateTime());
    }

    private KnowledgeChunkDetailVO toDetail(AgentKnowledgeChunkEntity entity) {
        KnowledgeChunkDetailVO detail = new KnowledgeChunkDetailVO();
        detail.setKnowledgeId(entity.getKnowledgeId());
        detail.setContent(entity.getContent());
        detail.setNameLocked(Integer.valueOf(1).equals(entity.getNameLocked()));
        detail.setRetryCount(entity.getRetryCount());
        detail.setErrorMsg(entity.getErrorMsg());
        detail.setId(entity.getChunkId());
        detail.setSeq(entity.getChunkOrder());
        detail.setName(entity.getName());
        detail.setLength(entity.getContentLength());
        detail.setContentVersion(entity.getContentVersion());
        detail.setVectorVersion(entity.getVectorVersion());
        detail.setVectorStatus(entity.getVectorStatus());
        detail.setUpdateTime(entity.getUpdateTime());
        return detail;
    }
}
