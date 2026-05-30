package com.liang.data.agent.service.knowledgecandidate.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.common.enums.KnowledgeCandidateScope;
import com.liang.data.agent.common.enums.KnowledgeCandidateStatus;
import com.liang.data.agent.common.enums.KnowledgeCandidateType;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.common.util.BeanUtil;
import com.liang.data.agent.dal.entity.KnowledgeCandidateEntity;
import com.liang.data.agent.dal.mapper.KnowledgeCandidateMapper;
import com.liang.data.agent.service.knowledge.BusinessKnowledgeService;
import com.liang.data.agent.service.knowledgecandidate.KnowledgeCandidateService;
import com.liang.data.agent.service.knowledgecandidate.dto.KnowledgeCandidateDTO;
import com.liang.data.agent.service.knowledgecandidate.dto.PublishKnowledgeCandidateDTO;
import com.liang.data.agent.service.knowledgecandidate.vo.KnowledgeCandidateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class KnowledgeCandidateServiceImpl extends ServiceImpl<KnowledgeCandidateMapper, KnowledgeCandidateEntity>
        implements KnowledgeCandidateService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BusinessKnowledgeService businessKnowledgeService;

    @Override
    public Long create(KnowledgeCandidateDTO dto) {
        KnowledgeCandidateEntity entity = BeanUtil.copyProperties(dto, KnowledgeCandidateEntity.class);
        entity.setScope(defaultIfBlank(dto.getScope(), KnowledgeCandidateScope.AGENT.getCode()));
        entity.setStatus(defaultIfBlank(dto.getStatus(), KnowledgeCandidateStatus.DRAFT.getCode()));
        entity.setDelFlag(0);
        save(entity);
        return entity.getId();
    }

    @Override
    public List<KnowledgeCandidateVO> listByAgent(Integer agentId, String status) {
        return lambdaQuery()
                .eq(KnowledgeCandidateEntity::getAgentId, agentId)
                .eq(StringUtils.hasText(status), KnowledgeCandidateEntity::getStatus, status)
                .orderByDesc(KnowledgeCandidateEntity::getUpdateTime)
                .list()
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public KnowledgeCandidateVO getDetail(Long id) {
        return toVO(getCandidate(id));
    }

    @Override
    public void submitForReview(Long id) {
        KnowledgeCandidateEntity entity = getCandidate(id);
        entity.setStatus(KnowledgeCandidateStatus.PENDING_REVIEW.getCode());
        updateById(entity);
    }

    @Override
    public void reject(Long id, PublishKnowledgeCandidateDTO dto) {
        KnowledgeCandidateEntity entity = getCandidate(id);
        entity.setStatus(KnowledgeCandidateStatus.REJECTED.getCode());
        if (dto != null) {
            entity.setReviewerId(dto.getReviewerId());
            entity.setReviewComment(dto.getReviewComment());
        }
        updateById(entity);
    }

    @Override
    public Long publish(Long id, PublishKnowledgeCandidateDTO dto) {
        KnowledgeCandidateEntity entity = getCandidate(id);
        String targetType = dto == null ? null : dto.getTargetType();
        String publishType = defaultIfBlank(targetType, KnowledgeCandidateType.BUSINESS_KNOWLEDGE.getCode());
        if (!KnowledgeCandidateType.BUSINESS_KNOWLEDGE.getCode().equals(publishType)) {
            throw new ServiceException("当前 MVP 仅支持发布为 BUSINESS_KNOWLEDGE");
        }

        Map<String, Object> content = parseNormalizedContent(entity.getNormalizedContent());
        String businessTerm = String.valueOf(content.getOrDefault("businessTerm", entity.getTitle()));
        String description = String.valueOf(content.getOrDefault("description", entity.getNormalizedContent()));
        String synonyms = normalizeSynonyms(content.get("synonyms"));
        Integer businessKnowledgeId = businessKnowledgeService.createFromCandidate(
                entity.getAgentId(),
                businessTerm,
                description,
                synonyms
        );

        entity.setStatus(KnowledgeCandidateStatus.PUBLISHED.getCode());
        entity.setPublishedTargetType(KnowledgeCandidateType.BUSINESS_KNOWLEDGE.getCode());
        entity.setPublishedTargetId(businessKnowledgeId.longValue());
        if (dto != null) {
            entity.setReviewerId(dto.getReviewerId());
            entity.setReviewComment(dto.getReviewComment());
        }
        updateById(entity);
        return entity.getPublishedTargetId();
    }

    private Map<String, Object> parseNormalizedContent(String normalizedContent) {
        try {
            return OBJECT_MAPPER.readValue(normalizedContent, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new ServiceException("候选知识内容不是合法 JSON，无法发布");
        }
    }

    private String normalizeSynonyms(Object synonyms) {
        if (synonyms == null) {
            return "";
        }
        if (synonyms instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder();
            for (Object item : iterable) {
                if (!builder.isEmpty()) {
                    builder.append(",");
                }
                builder.append(item);
            }
            return builder.toString();
        }
        return synonyms.toString();
    }

    private KnowledgeCandidateEntity getCandidate(Long id) {
        if (Objects.isNull(id)) {
            throw new ServiceException("候选知识ID不能为空");
        }
        return lambdaQuery()
                .eq(KnowledgeCandidateEntity::getId, id)
                .oneOpt()
                .orElseThrow(() -> new ServiceException("未找到候选知识, id=" + id));
    }

    private KnowledgeCandidateVO toVO(KnowledgeCandidateEntity entity) {
        return BeanUtil.copyProperties(entity, KnowledgeCandidateVO.class);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
