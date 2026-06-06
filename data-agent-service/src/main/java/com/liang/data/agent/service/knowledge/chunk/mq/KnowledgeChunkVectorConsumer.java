package com.liang.data.agent.service.knowledge.chunk.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.liang.data.agent.ai.vectorstore.AgentVectorStoreService;
import com.liang.data.agent.common.enums.VectorType;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.entity.AgentKnowledgeEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import com.liang.data.agent.dal.mapper.AgentKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.liang.data.agent.common.constant.VectorMetadataKey.*;

/**
 * 知识分块向量化消息消费者。
 *
 * <p>通过正文版本和同步状态 CAS 防止旧消息覆盖最新向量。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = KnowledgeChunkMessagePublisher.TOPIC,
        selectorExpression = "VECTORIZE",
        consumerGroup = "data-agent-knowledge-chunk-vector-consumer")
public class KnowledgeChunkVectorConsumer implements RocketMQListener<KnowledgeChunkMessage> {

    private final AgentKnowledgeMapper knowledgeMapper;
    private final AgentKnowledgeChunkMapper chunkMapper;
    private final AgentVectorStoreService vectorStoreService;

    @Override
    public void onMessage(KnowledgeChunkMessage message) {
        AgentKnowledgeEntity knowledge = knowledgeMapper.selectById(message.knowledgeId());
        AgentKnowledgeChunkEntity chunk = getChunk(message);
        if (knowledge == null || !message.agentId().equals(knowledge.getAgentId()) || chunk == null
                || !message.contentVersion().equals(chunk.getContentVersion())) {
            log.warn("忽略归属或版本已变化的分块向量化消息：chunkId={}，contentVersion={}",
                    message.chunkId(), message.contentVersion());
            return;
        }
        if (chunkMapper.claimVectorProcessing(message.chunkId(), message.contentVersion()) == 0) {
            return;
        }

        String newVectorId = vectorId(message.chunkId(), message.contentVersion());
        try {
            vectorStoreService.addDocuments(message.agentId().toString(),
                    List.of(toDocument(knowledge, chunk, newVectorId, message.contentVersion())));
            if (chunkMapper.completeVectorIfProcessing(message.chunkId(), message.contentVersion()) == 0) {
                vectorStoreService.deleteDocumentsByIds(List.of(newVectorId));
                return;
            }
            if (chunk.getEmbeddingId() != null && !chunk.getEmbeddingId().equals(newVectorId)) {
                vectorStoreService.deleteDocumentsByIds(List.of(chunk.getEmbeddingId()));
            }
        } catch (RuntimeException exception) {
            chunkMapper.recordVectorRetry(message.chunkId(), message.contentVersion(), summarize(exception));
            throw exception;
        }
    }

    private AgentKnowledgeChunkEntity getChunk(KnowledgeChunkMessage message) {
        return chunkMapper.selectOne(Wrappers.lambdaQuery(AgentKnowledgeChunkEntity.class)
                .eq(AgentKnowledgeChunkEntity::getKnowledgeId, message.knowledgeId())
                .eq(AgentKnowledgeChunkEntity::getChunkId, message.chunkId()));
    }

    private Document toDocument(AgentKnowledgeEntity knowledge, AgentKnowledgeChunkEntity chunk,
                                String vectorId, Integer contentVersion) {
        return new Document(vectorId, chunk.getContent(), Map.of(
                AGENT_ID, knowledge.getAgentId().toString(),
                VECTOR_TYPE, VectorType.KNOWLEDGE.getCode(),
                NAME, knowledge.getTitle(),
                DESCRIPTION, knowledge.getSourceFilename(),
                "agentKnowledgeId", knowledge.getId().toString(),
                "chunkId", chunk.getChunkId(),
                "chunkOrder", chunk.getChunkOrder().toString(),
                "chunkVersion", contentVersion.toString(),
                "splitterType", chunk.getSplitterType()
        ));
    }

    private String vectorId(String chunkId, Integer contentVersion) {
        return chunkId + "-v" + contentVersion;
    }

    private String summarize(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "分块向量化失败" : message.substring(0, Math.min(message.length(), 255));
    }
}
