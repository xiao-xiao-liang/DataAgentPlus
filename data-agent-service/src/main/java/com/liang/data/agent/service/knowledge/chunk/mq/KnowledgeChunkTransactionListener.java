package com.liang.data.agent.service.knowledge.chunk.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.liang.data.agent.dal.entity.AgentKnowledgeChunkEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识分块向量任务事务消息监听器。
 */
@Slf4j
@RequiredArgsConstructor
@RocketMQTransactionListener
public class KnowledgeChunkTransactionListener implements RocketMQLocalTransactionListener {

    private final AgentKnowledgeChunkMapper chunkMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RocketMQLocalTransactionState executeLocalTransaction(Message message, Object argument) {
        if (!(argument instanceof KnowledgeChunkTransactionOperation operation)) {
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        int rows = switch (operation.type()) {
            case UPDATE_CONTENT -> chunkMapper.updateContentAndCreateTask(
                    operation.chunkId(), operation.expectedContentVersion(), operation.expectedTaskVersion(),
                    operation.name(), operation.nameLocked(), operation.content(), operation.content().length());
            case RETRY_FAILED -> chunkMapper.retryFailedTask(
                    operation.chunkId(), operation.expectedContentVersion(), operation.expectedTaskVersion());
            case RECOVER_TIMEOUT -> chunkMapper.recoverTimedOutTask(
                    operation.chunkId(), operation.expectedContentVersion(), operation.expectedTaskVersion(),
                    operation.expectedStatus(), operation.deadline());
        };
        return rows == 1 ? RocketMQLocalTransactionState.COMMIT : RocketMQLocalTransactionState.ROLLBACK;
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message message) {
        String chunkId = stringHeader(message, KnowledgeChunkMqConstant.HEADER_CHUNK_ID);
        Integer contentVersion = integerHeader(message, KnowledgeChunkMqConstant.HEADER_CONTENT_VERSION);
        Integer taskVersion = integerHeader(message, KnowledgeChunkMqConstant.HEADER_TASK_VERSION);
        if (chunkId == null || contentVersion == null || taskVersion == null) {
            log.warn("无法识别分块事务消息回查参数");
            return RocketMQLocalTransactionState.UNKNOWN;
        }
        AgentKnowledgeChunkEntity chunk = chunkMapper.selectOne(Wrappers.lambdaQuery(AgentKnowledgeChunkEntity.class)
                .eq(AgentKnowledgeChunkEntity::getChunkId, chunkId));
        if (chunk == null) {
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        boolean matches = contentVersion.equals(chunk.getContentVersion())
                && taskVersion.equals(chunk.getVectorTaskVersion());
        return matches ? RocketMQLocalTransactionState.COMMIT : RocketMQLocalTransactionState.ROLLBACK;
    }

    private String stringHeader(Message message, String name) {
        Object value = message.getHeaders().get(name);
        return value == null ? null : value.toString();
    }

    private Integer integerHeader(Message message, String name) {
        Object value = message.getHeaders().get(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
