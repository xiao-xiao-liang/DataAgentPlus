package com.liang.data.agent.service.knowledge.impl;

import com.liang.data.agent.ai.vectorstore.AgentVectorStoreService;
import com.liang.data.agent.common.constant.VectorMetadataKey;
import com.liang.data.agent.common.enums.VectorType;
import com.liang.data.agent.dal.entity.BusinessKnowledgeEntity;
import com.liang.data.agent.dal.mapper.BusinessKnowledgeMapper;
import com.liang.data.agent.service.knowledge.BusinessKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessKnowledgeServiceImpl implements BusinessKnowledgeService {

    private final BusinessKnowledgeMapper businessKnowledgeMapper;

    private final AgentVectorStoreService vectorStoreService;

    @Override
    public Integer createFromCandidate(Integer agentId, String businessTerm, String description, String synonyms) {
        BusinessKnowledgeEntity entity = new BusinessKnowledgeEntity();
        entity.setAgentId(agentId);
        entity.setBusinessTerm(businessTerm);
        entity.setDescription(description);
        entity.setSynonyms(synonyms);
        entity.setIsRecall(1);
        entity.setEmbeddingStatus("PENDING");
        entity.setDelFlag(0);
        businessKnowledgeMapper.insert(entity);

        try {
            String text = """
                    业务术语：%s
                    业务定义：%s
                    同义词：%s
                    """.formatted(businessTerm, description, synonyms == null ? "" : synonyms).trim();
            Document document = new Document(text, Map.of(
                    VectorMetadataKey.AGENT_ID, agentId.toString(),
                    VectorMetadataKey.VECTOR_TYPE, VectorType.BUSINESS_TERM.getCode(),
                    VectorMetadataKey.NAME, businessTerm,
                    VectorMetadataKey.DESCRIPTION, description == null ? "" : description,
                    "businessKnowledgeId", entity.getId()
            ));
            vectorStoreService.addDocuments(agentId.toString(), List.of(document));
            entity.setEmbeddingStatus("COMPLETED");
            businessKnowledgeMapper.updateById(entity);
        } catch (Exception e) {
            log.error("候选知识发布后向量化失败, businessKnowledgeId={}", entity.getId(), e);
            entity.setEmbeddingStatus("FAILED");
            entity.setErrorMsg(e.getMessage());
            businessKnowledgeMapper.updateById(entity);
        }
        return entity.getId();
    }
}
