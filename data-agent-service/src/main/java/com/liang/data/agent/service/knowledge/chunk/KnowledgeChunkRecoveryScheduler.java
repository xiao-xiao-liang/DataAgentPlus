package com.liang.data.agent.service.knowledge.chunk;

import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.entity.AgentKnowledgeEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import com.liang.data.agent.dal.mapper.AgentKnowledgeMapper;
import com.liang.data.agent.service.knowledge.chunk.mq.KnowledgeChunkTransactionOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识分块超时向量任务恢复器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeChunkRecoveryScheduler {

    private final AgentKnowledgeChunkMapper chunkMapper;
    private final AgentKnowledgeMapper knowledgeMapper;
    private final KnowledgeChunkAsyncPublisher asyncPublisher;
    private final KnowledgeChunkProperties properties;

    /**
     * 恢复长期未领取或长期未完成的向量任务。
     */
    @Scheduled(fixedDelayString = "#{@knowledgeChunkProperties.vectorRecoveryInterval.toMillis()}")
    public void recoverTimedOutTasks() {
        LocalDateTime deadline = LocalDateTime.now().minus(properties.getVectorTaskTimeout());
        var timedOutChunks = chunkMapper.selectTimedOutTasks(deadline, properties.getRecoveryBatchSize());
        if (timedOutChunks.isEmpty()) {
            return;
        }
        Map<Integer, AgentKnowledgeEntity> knowledgeById = knowledgeMapper.selectByIds(
                        timedOutChunks.stream().map(AgentKnowledgeChunkEntity::getKnowledgeId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(AgentKnowledgeEntity::getId, Function.identity()));
        for (AgentKnowledgeChunkEntity chunk : timedOutChunks) {
            try {
                var knowledge = knowledgeById.get(chunk.getKnowledgeId());
                if (knowledge == null) {
                    continue;
                }
                asyncPublisher.publishVectorizeTransaction(new KnowledgeChunkTransactionOperation(
                        KnowledgeChunkTransactionOperation.Type.RECOVER_TIMEOUT,
                        knowledge.getAgentId(), chunk.getKnowledgeId(), chunk.getChunkId(), chunk.getContentVersion(),
                        chunk.getVectorTaskVersion(), null, null, null, chunk.getVectorStatus(), deadline));
            } catch (RuntimeException exception) {
                log.warn("恢复超时分块向量任务失败：chunkId={}，contentVersion={}，taskVersion={}",
                        chunk.getChunkId(), chunk.getContentVersion(), chunk.getVectorTaskVersion(), exception);
            }
        }
    }
}
