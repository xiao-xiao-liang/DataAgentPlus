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
import java.time.LocalDateTime;

import static com.liang.data.agent.common.constant.VectorMetadataKey.*;
import static com.liang.data.agent.service.knowledge.chunk.KnowledgeChunkConstraint.MAX_ERROR_MESSAGE_LENGTH;

/**
 * 知识分块向量化消息消费者。
 *
 * <p>通过正文版本和同步状态 CAS 防止旧消息覆盖最新向量。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = KnowledgeChunkMqConstant.TOPIC,
        selectorExpression = KnowledgeChunkMqConstant.TAG_VECTORIZE,
        consumerGroup = KnowledgeChunkMqConstant.VECTOR_CONSUMER_GROUP)
public class KnowledgeChunkVectorConsumer implements RocketMQListener<KnowledgeChunkMessage> {

    private final AgentKnowledgeMapper knowledgeMapper;
    private final AgentKnowledgeChunkMapper chunkMapper;
    private final AgentVectorStoreService vectorStoreService;

    @Override
    public void onMessage(KnowledgeChunkMessage message) {
        AgentKnowledgeEntity knowledge = knowledgeMapper.selectById(message.knowledgeId());
        AgentKnowledgeChunkEntity chunk = getChunk(message);
        if (knowledge == null || !message.agentId().equals(knowledge.getAgentId()) || chunk == null
                || !message.contentVersion().equals(chunk.getContentVersion())
                || !message.taskVersion().equals(chunk.getVectorTaskVersion())) {
            log.warn("忽略归属或版本已变化的分块向量化消息：chunkId={}，contentVersion={}",
                    message.chunkId(), message.contentVersion());
            return;
        }
        if (chunkMapper.claimVectorProcessing(message.chunkId(), message.contentVersion(),
                message.taskVersion(), LocalDateTime.now()) == 0) {
            return;
        }

        String newVectorId = vectorId(message.chunkId(), message.contentVersion(), message.taskVersion());
        String oldVectorId = chunk.getEmbeddingId();
        try {
            vectorStoreService.addDocuments(message.agentId().toString(),
                    List.of(toDocument(knowledge, chunk, newVectorId, message.contentVersion(), message.taskVersion())));
            if (chunkMapper.completeVectorIfProcessing(
                    message.chunkId(), message.contentVersion(), message.taskVersion(), newVectorId) == 0) {
                vectorStoreService.deleteDocumentsByIds(List.of(newVectorId));
                return;
            }
            completeKnowledgeIfAllChunksSynced(message.knowledgeId());
        } catch (RuntimeException exception) {
            chunkMapper.recordVectorRetry(
                    message.chunkId(), message.contentVersion(), message.taskVersion(), summarize(exception));
            throw exception;
        }
        if (oldVectorId != null && !oldVectorId.equals(newVectorId)) {
            try {
                vectorStoreService.deleteDocumentsByIds(List.of(oldVectorId));
            } catch (RuntimeException exception) {
                log.error("旧分块向量清理失败，不影响新向量生效：chunkId={}，embeddingId={}",
                        message.chunkId(), oldVectorId, exception);
            }
        }
    }

    private AgentKnowledgeChunkEntity getChunk(KnowledgeChunkMessage message) {
        return chunkMapper.selectOne(Wrappers.lambdaQuery(AgentKnowledgeChunkEntity.class)
                .eq(AgentKnowledgeChunkEntity::getKnowledgeId, message.knowledgeId())
                .eq(AgentKnowledgeChunkEntity::getChunkId, message.chunkId()));
    }

    private Document toDocument(AgentKnowledgeEntity knowledge, AgentKnowledgeChunkEntity chunk,
                                String vectorId, Integer contentVersion, Integer taskVersion) {
        return new Document(vectorId, chunk.getContent(), Map.of(
                AGENT_ID, knowledge.getAgentId().toString(),
                VECTOR_TYPE, VectorType.KNOWLEDGE.getCode(),
                NAME, knowledge.getTitle(),
                DESCRIPTION, knowledge.getSourceFilename(),
                AGENT_KNOWLEDGE_ID, knowledge.getId().toString(),
                CHUNK_ID, chunk.getChunkId(),
                CHUNK_ORDER, chunk.getChunkOrder().toString(),
                CONTENT_VERSION, contentVersion.toString(),
                VECTOR_TASK_VERSION, taskVersion.toString(),
                SPLITTER_TYPE, chunk.getSplitterType()
        ));
    }

    private String vectorId(String chunkId, Integer contentVersion, Integer taskVersion) {
        return chunkId + "-c" + contentVersion + "-t" + taskVersion;
    }

    private void completeKnowledgeIfAllChunksSynced(Integer knowledgeId) {
        if (chunkMapper.countUnfinishedVectorChunks(knowledgeId) == 0) {
            knowledgeMapper.completeEmbeddingIfProcessing(knowledgeId);
        }
    }

    private String summarize(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "分块向量化失败"
                : message.substring(0, Math.min(message.length(), MAX_ERROR_MESSAGE_LENGTH));
    }
}
