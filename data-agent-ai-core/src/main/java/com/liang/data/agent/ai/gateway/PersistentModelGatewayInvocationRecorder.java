package com.liang.data.agent.ai.gateway;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.liang.data.agent.dal.entity.ModelGatewayAttemptEntity;
import com.liang.data.agent.dal.entity.ModelGatewayInvocationEntity;
import com.liang.data.agent.dal.mapper.ModelGatewayAttemptMapper;
import com.liang.data.agent.dal.mapper.ModelGatewayInvocationMapper;
import com.liang.data.agent.gateway.request.ModelCallMode;
import com.liang.data.agent.gateway.response.ModelRoute;
import com.liang.data.agent.gateway.response.ModelUsage;
import com.liang.data.agent.gateway.context.GatewayExecutionContext;
import com.liang.data.agent.gateway.error.ModelGatewayErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持久化模型网关调用明细记录器，将调用主记录和尝试记录写入数据库。
 */
@Slf4j
public class PersistentModelGatewayInvocationRecorder implements ModelGatewayInvocationRecorder {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 512;

    private final ModelGatewayInvocationMapper invocationMapper;

    private final ModelGatewayAttemptMapper attemptMapper;

    private final boolean persistenceEnabled;

    private final Map<String, LocalDateTime> invocationStartTimeMap = new ConcurrentHashMap<>();

    private final Map<String, LocalDateTime> attemptStartTimeMap = new ConcurrentHashMap<>();

    public PersistentModelGatewayInvocationRecorder(ModelGatewayInvocationMapper invocationMapper,
                                                    ModelGatewayAttemptMapper attemptMapper,
                                                    boolean persistenceEnabled) {
        this.invocationMapper = Objects.requireNonNull(invocationMapper, "invocationMapper");
        this.attemptMapper = Objects.requireNonNull(attemptMapper, "attemptMapper");
        this.persistenceEnabled = persistenceEnabled;
    }

    @Override
    public void startInvocation(String invocationId, GatewayExecutionContext context, String sceneCode, ModelCallMode mode) {
        if (!persistenceEnabled) {
            return;
        }
        try {
            // 1. 生成开始时间，供数据库主记录和结束时耗时计算使用。
            LocalDateTime startTime = LocalDateTime.now();
            // 2. 组装运行中主调用记录，Token 初始值置为 0。
            ModelGatewayInvocationEntity entity = ModelGatewayInvocationEntity.builder()
                    .invocationId(invocationId)
                    .runId(context.runId())
                    .traceId(context.traceId())
                    .sessionId(context.sessionId())
                    .userId(context.userId())
                    .agentId(context.agentId())
                    .tenantId(context.tenantId())
                    .sceneCode(sceneCode)
                    .callMode(mode.name())
                    .status(ModelGatewayCallStatus.RUNNING.name())
                    .startTime(startTime)
                    .inputTokens(0L)
                    .outputTokens(0L)
                    .totalTokens(0L)
                    .build();
            // 3. 插入数据库明细，失败仅记录告警。
            invocationMapper.insert(entity);
            // 4. 数据库插入成功后再缓存开始时间，避免失败记录残留耗时。
            invocationStartTimeMap.put(invocationId, startTime);
        } catch (RuntimeException exception) {
            log.warn("记录模型网关调用开始失败，调用标识：{}，异常类型：{}", invocationId, exception.getClass().getSimpleName());
        }
    }

    @Override
    public void finishInvocation(String invocationId, ModelGatewayCallStatus status, ModelRoute route, ModelUsage usage,
                                 ModelGatewayErrorCode errorCode, String errorMessage) {
        if (!persistenceEnabled) {
            return;
        }
        try {
            // 1. 计算结束时间和耗时，缺少开始时间时耗时保持为空。
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = invocationStartTimeMap.remove(invocationId);
            Long durationMs = calculateDurationMs(startTime, endTime);
            // 2. 按调用标识构建更新条件和更新字段。
            LambdaUpdateWrapper<ModelGatewayInvocationEntity> updateWrapper =
                    buildFinishInvocationWrapper(invocationId, status, route, usage, errorCode, errorMessage, endTime, durationMs);
            // 3. 执行数据库更新，失败仅记录告警。
            invocationMapper.update(null, updateWrapper);
        } catch (RuntimeException exception) {
            log.warn("记录模型网关调用结束失败，调用标识：{}，异常类型：{}", invocationId, exception.getClass().getSimpleName());
        }
    }

    @Override
    public void startAttempt(String invocationId, String attemptId, int attemptNo, String provider, String model) {
        if (!persistenceEnabled) {
            return;
        }
        try {
            // 1. 生成尝试开始时间，供数据库尝试记录和结束时耗时计算使用。
            LocalDateTime startTime = LocalDateTime.now();
            // 2. 组装运行中的尝试记录。
            ModelGatewayAttemptEntity entity = ModelGatewayAttemptEntity.builder()
                    .invocationId(invocationId)
                    .attemptId(attemptId)
                    .attemptNo(attemptNo)
                    .provider(provider)
                    .model(model)
                    .status(ModelGatewayCallStatus.RUNNING.name())
                    .startTime(startTime)
                    .build();
            // 3. 插入数据库明细，失败仅记录告警。
            attemptMapper.insert(entity);
            // 4. 数据库插入成功后再缓存开始时间，避免失败记录残留耗时。
            attemptStartTimeMap.put(attemptId, startTime);
        } catch (RuntimeException exception) {
            log.warn("记录模型网关尝试开始失败，尝试标识：{}，异常类型：{}", attemptId, exception.getClass().getSimpleName());
        }
    }

    @Override
    public void finishAttempt(String attemptId, ModelGatewayCallStatus status, Integer httpStatus,
                              ModelGatewayErrorCode errorCode, String errorMessage) {
        if (!persistenceEnabled) {
            return;
        }
        try {
            // 1. 计算结束时间和耗时，缺少开始时间时耗时保持为空。
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = attemptStartTimeMap.remove(attemptId);
            Long durationMs = calculateDurationMs(startTime, endTime);
            // 2. 按尝试标识构建更新条件和更新字段。
            LambdaUpdateWrapper<ModelGatewayAttemptEntity> updateWrapper =
                    buildFinishAttemptWrapper(attemptId, status, httpStatus, errorCode, errorMessage, endTime, durationMs);
            // 3. 执行数据库更新，失败仅记录告警。
            attemptMapper.update(null, updateWrapper);
        } catch (RuntimeException exception) {
            log.warn("记录模型网关尝试结束失败，尝试标识：{}，异常类型：{}", attemptId, exception.getClass().getSimpleName());
        }
    }

    static String truncateErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private static LambdaUpdateWrapper<ModelGatewayInvocationEntity> buildFinishInvocationWrapper(
            String invocationId, ModelGatewayCallStatus status, ModelRoute route, ModelUsage usage,
            ModelGatewayErrorCode errorCode, String errorMessage, LocalDateTime endTime, Long durationMs) {
        ModelGatewayCallStatus finalStatus = normalizeStatus(status);
        LambdaUpdateWrapper<ModelGatewayInvocationEntity> updateWrapper =
                new LambdaUpdateWrapper<>(ModelGatewayInvocationEntity.class);
        updateWrapper.eq(ModelGatewayInvocationEntity::getInvocationId, invocationId)
                .set(ModelGatewayInvocationEntity::getStatus, finalStatus.name())
                .set(ModelGatewayInvocationEntity::getEndTime, endTime)
                .set(durationMs != null, ModelGatewayInvocationEntity::getDurationMs, durationMs)
                .set(errorMessage != null, ModelGatewayInvocationEntity::getErrorMessage, truncateErrorMessage(errorMessage));
        if (errorCode != null) {
            updateWrapper.set(ModelGatewayInvocationEntity::getErrorCode, errorCode.code());
        }
        if (route != null) {
            updateWrapper.set(ModelGatewayInvocationEntity::getProvider, route.provider())
                    .set(ModelGatewayInvocationEntity::getModel, route.model());
        }
        if (usage != null) {
            updateWrapper.set(ModelGatewayInvocationEntity::getInputTokens, usage.inputTokens())
                    .set(ModelGatewayInvocationEntity::getOutputTokens, usage.outputTokens())
                    .set(ModelGatewayInvocationEntity::getTotalTokens, usage.totalTokens());
        }
        return updateWrapper;
    }

    private static LambdaUpdateWrapper<ModelGatewayAttemptEntity> buildFinishAttemptWrapper(
            String attemptId, ModelGatewayCallStatus status, Integer httpStatus,
            ModelGatewayErrorCode errorCode, String errorMessage, LocalDateTime endTime, Long durationMs) {
        ModelGatewayCallStatus finalStatus = normalizeStatus(status);
        LambdaUpdateWrapper<ModelGatewayAttemptEntity> updateWrapper =
                new LambdaUpdateWrapper<>(ModelGatewayAttemptEntity.class);
        updateWrapper.eq(ModelGatewayAttemptEntity::getAttemptId, attemptId)
                .set(ModelGatewayAttemptEntity::getStatus, finalStatus.name())
                .set(ModelGatewayAttemptEntity::getEndTime, endTime)
                .set(durationMs != null, ModelGatewayAttemptEntity::getDurationMs, durationMs)
                .set(httpStatus != null, ModelGatewayAttemptEntity::getHttpStatus, httpStatus)
                .set(errorMessage != null, ModelGatewayAttemptEntity::getErrorMessage, truncateErrorMessage(errorMessage));
        if (errorCode != null) {
            updateWrapper.set(ModelGatewayAttemptEntity::getErrorCode, errorCode.code());
        }
        return updateWrapper;
    }

    /**
     * 归一化调用状态，保证旁路记录器不会因为空状态丢失更新。
     *
     * @param status 调用状态
     * @return 非空调用状态
     */
    private static ModelGatewayCallStatus normalizeStatus(ModelGatewayCallStatus status) {
        // 1. 空状态按失败处理，提升旁路记录器容错性。
        return status == null ? ModelGatewayCallStatus.FAILED : status;
    }

    private static Long calculateDurationMs(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null) {
            return null;
        }
        return Duration.between(startTime, endTime).toMillis();
    }
}
