package com.liang.data.agent.ai.gateway;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.liang.data.agent.dal.entity.ModelGatewayAttemptEntity;
import com.liang.data.agent.dal.entity.ModelGatewayInvocationEntity;
import com.liang.data.agent.dal.mapper.ModelGatewayAttemptMapper;
import com.liang.data.agent.dal.mapper.ModelGatewayInvocationMapper;
import com.liang.data.agent.gateway.request.ModelCallMode;
import com.liang.data.agent.gateway.response.ModelRoute;
import com.liang.data.agent.gateway.response.ModelUsage;
import com.liang.data.agent.gateway.context.GatewayExecutionContext;
import com.liang.data.agent.gateway.error.ModelGatewayErrorCode;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 持久化模型网关调用记录器测试，验证主记录与尝试记录的插入、更新、异常吞掉和关闭开关行为。
 */
class PersistentModelGatewayInvocationRecorderTest {

    private ModelGatewayInvocationMapper invocationMapper;

    private ModelGatewayAttemptMapper attemptMapper;

    private PersistentModelGatewayInvocationRecorder recorder;

    @BeforeAll
    static void initMyBatisPlusTableInfo() {
        // 1. 初始化 MyBatis-Plus 表元数据，确保 LambdaUpdateWrapper 在纯单元测试中可解析列名。
        TableInfo invocationTableInfo = TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ModelGatewayInvocationEntity.class
        );
        LambdaUtils.installCache(invocationTableInfo);
        TableInfo attemptTableInfo = TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ModelGatewayAttemptEntity.class
        );
        LambdaUtils.installCache(attemptTableInfo);
    }

    @BeforeEach
    void setUp() {
        invocationMapper = mock(ModelGatewayInvocationMapper.class);
        attemptMapper = mock(ModelGatewayAttemptMapper.class);
        recorder = new PersistentModelGatewayInvocationRecorder(invocationMapper, attemptMapper, true);
    }

    @Test
    void startInvocationShouldInsertRunningRecord() {
        GatewayExecutionContext context = new GatewayExecutionContext(
                "run-001", "trace-001", "session-001", 1001L, 2001, "tenant-001");

        recorder.startInvocation("invocation-001", context, "chat", ModelCallMode.BLOCK);

        ArgumentCaptor<ModelGatewayInvocationEntity> captor =
                ArgumentCaptor.forClass(ModelGatewayInvocationEntity.class);
        verify(invocationMapper).insert(captor.capture());
        ModelGatewayInvocationEntity entity = captor.getValue();
        assertThat(entity.getInvocationId()).isEqualTo("invocation-001");
        assertThat(entity.getRunId()).isEqualTo("run-001");
        assertThat(entity.getTraceId()).isEqualTo("trace-001");
        assertThat(entity.getSessionId()).isEqualTo("session-001");
        assertThat(entity.getUserId()).isEqualTo(1001L);
        assertThat(entity.getAgentId()).isEqualTo(2001);
        assertThat(entity.getTenantId()).isEqualTo("tenant-001");
        assertThat(entity.getSceneCode()).isEqualTo("chat");
        assertThat(entity.getCallMode()).isEqualTo(ModelCallMode.BLOCK.name());
        assertThat(entity.getStatus()).isEqualTo(ModelGatewayCallStatus.RUNNING.name());
        assertThat(entity.getStartTime()).isNotNull();
        assertThat(entity.getInputTokens()).isZero();
        assertThat(entity.getOutputTokens()).isZero();
        assertThat(entity.getTotalTokens()).isZero();
    }

    @Test
    void finishInvocationShouldUpdateRouteUsageAndErrorSummary() {
        GatewayExecutionContext context = new GatewayExecutionContext(
                "run-001", "trace-001", "session-001", 1001L, 2001, "tenant-001");
        recorder.startInvocation("invocation-001", context, "chat", ModelCallMode.BLOCK);
        String longMessage = "错".repeat(600);

        recorder.finishInvocation(
                "invocation-001",
                ModelGatewayCallStatus.FAILED,
                new ModelRoute("openai", "gpt-4.1", 1, false),
                new ModelUsage(10, 20, 30),
                ModelGatewayErrorCode.PROVIDER_TIMEOUT,
                longMessage);

        ArgumentCaptor<LambdaUpdateWrapper<ModelGatewayInvocationEntity>> captor = invocationUpdateCaptor();
        verify(invocationMapper).update(isNull(), captor.capture());
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        assertThat(captor.getValue().getSqlSegment()).contains("invocation_id");
        assertThat(params).containsValue(ModelGatewayCallStatus.FAILED.name());
        assertThat(params).containsValue("openai");
        assertThat(params).containsValue("gpt-4.1");
        assertThat(params).containsValue(10L);
        assertThat(params).containsValue(20L);
        assertThat(params).containsValue(30L);
        assertThat(params).containsValue(ModelGatewayErrorCode.PROVIDER_TIMEOUT.code());
        assertThat(params).containsValue("错".repeat(512));
        assertThat(params).doesNotContainValue(longMessage);
    }

    @Test
    void finishInvocationShouldNotSetDurationWhenStartInsertFailed() {
        doThrow(new RuntimeException("数据库异常")).when(invocationMapper).insert(any(ModelGatewayInvocationEntity.class));
        GatewayExecutionContext context = new GatewayExecutionContext(
                "run-001", "trace-001", "session-001", 1001L, 2001, "tenant-001");
        recorder.startInvocation("invocation-001", context, "chat", ModelCallMode.BLOCK);

        recorder.finishInvocation(
                "invocation-001",
                ModelGatewayCallStatus.FAILED,
                null,
                null,
                null,
                null);

        ArgumentCaptor<LambdaUpdateWrapper<ModelGatewayInvocationEntity>> captor = invocationUpdateCaptor();
        verify(invocationMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).doesNotContain("duration_ms");
    }

    @Test
    void finishInvocationShouldUseFailedWhenStatusIsNull() {
        recorder.finishInvocation(
                "invocation-001",
                null,
                null,
                null,
                null,
                null);

        ArgumentCaptor<LambdaUpdateWrapper<ModelGatewayInvocationEntity>> captor = invocationUpdateCaptor();
        verify(invocationMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs())
                .containsValue(ModelGatewayCallStatus.FAILED.name());
    }

    @Test
    void startAttemptShouldInsertRunningRecord() {
        recorder.startAttempt("invocation-001", "attempt-001", 1, "openai", "gpt-4.1");

        ArgumentCaptor<ModelGatewayAttemptEntity> captor =
                ArgumentCaptor.forClass(ModelGatewayAttemptEntity.class);
        verify(attemptMapper).insert(captor.capture());
        ModelGatewayAttemptEntity entity = captor.getValue();
        assertThat(entity.getInvocationId()).isEqualTo("invocation-001");
        assertThat(entity.getAttemptId()).isEqualTo("attempt-001");
        assertThat(entity.getAttemptNo()).isEqualTo(1);
        assertThat(entity.getProvider()).isEqualTo("openai");
        assertThat(entity.getModel()).isEqualTo("gpt-4.1");
        assertThat(entity.getStatus()).isEqualTo(ModelGatewayCallStatus.RUNNING.name());
        assertThat(entity.getStartTime()).isNotNull();
    }

    @Test
    void finishAttemptShouldUpdateStatusAndHttpStatus() {
        recorder.startAttempt("invocation-001", "attempt-001", 1, "openai", "gpt-4.1");

        recorder.finishAttempt(
                "attempt-001",
                ModelGatewayCallStatus.SUCCEEDED,
                200,
                null,
                null);

        ArgumentCaptor<LambdaUpdateWrapper<ModelGatewayAttemptEntity>> captor = attemptUpdateCaptor();
        verify(attemptMapper).update(isNull(), captor.capture());
        Map<String, Object> params = captor.getValue().getParamNameValuePairs();
        assertThat(captor.getValue().getSqlSegment()).contains("attempt_id");
        assertThat(params).containsValue(ModelGatewayCallStatus.SUCCEEDED.name());
        assertThat(params).containsValue(200);
    }

    @Test
    void finishAttemptShouldNotSetDurationWhenStartInsertFailed() {
        doThrow(new RuntimeException("数据库异常")).when(attemptMapper).insert(any(ModelGatewayAttemptEntity.class));
        recorder.startAttempt("invocation-001", "attempt-001", 1, "openai", "gpt-4.1");

        recorder.finishAttempt(
                "attempt-001",
                ModelGatewayCallStatus.FAILED,
                null,
                null,
                null);

        ArgumentCaptor<LambdaUpdateWrapper<ModelGatewayAttemptEntity>> captor = attemptUpdateCaptor();
        verify(attemptMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).doesNotContain("duration_ms");
    }

    @Test
    void finishAttemptShouldUseFailedWhenStatusIsNull() {
        recorder.finishAttempt(
                "attempt-001",
                null,
                null,
                null,
                null);

        ArgumentCaptor<LambdaUpdateWrapper<ModelGatewayAttemptEntity>> captor = attemptUpdateCaptor();
        verify(attemptMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs())
                .containsValue(ModelGatewayCallStatus.FAILED.name());
    }

    @Test
    void recorderShouldSwallowPersistenceFailure() {
        doThrow(new RuntimeException("数据库异常")).when(invocationMapper).insert(any(ModelGatewayInvocationEntity.class));
        doThrow(new RuntimeException("数据库异常")).when(invocationMapper).update(isNull(), any());
        doThrow(new RuntimeException("数据库异常")).when(attemptMapper).insert(any(ModelGatewayAttemptEntity.class));
        doThrow(new RuntimeException("数据库异常")).when(attemptMapper).update(isNull(), any());
        GatewayExecutionContext context = new GatewayExecutionContext(
                "run-001", "trace-001", "session-001", 1001L, 2001, "tenant-001");

        assertThatCode(() -> recorder.startInvocation("invocation-001", context, "chat", ModelCallMode.BLOCK))
                .doesNotThrowAnyException();
        assertThatCode(() -> recorder.finishInvocation(
                "invocation-001", ModelGatewayCallStatus.FAILED, null, null,
                ModelGatewayErrorCode.PROVIDER_TIMEOUT, "超时"))
                .doesNotThrowAnyException();
        assertThatCode(() -> recorder.startAttempt("invocation-001", "attempt-001", 1, "openai", "gpt-4.1"))
                .doesNotThrowAnyException();
        assertThatCode(() -> recorder.finishAttempt(
                "attempt-001", ModelGatewayCallStatus.FAILED, 500,
                ModelGatewayErrorCode.PROVIDER_TIMEOUT, "超时"))
                .doesNotThrowAnyException();
    }

    @Test
    void disabledRecorderShouldNotCallMappers() {
        PersistentModelGatewayInvocationRecorder disabledRecorder =
                new PersistentModelGatewayInvocationRecorder(invocationMapper, attemptMapper, false);
        GatewayExecutionContext context = new GatewayExecutionContext(
                "run-001", "trace-001", "session-001", 1001L, 2001, "tenant-001");

        disabledRecorder.startInvocation("invocation-001", context, "chat", ModelCallMode.BLOCK);
        disabledRecorder.finishInvocation(
                "invocation-001", ModelGatewayCallStatus.SUCCEEDED, null, null, null, null);
        disabledRecorder.startAttempt("invocation-001", "attempt-001", 1, "openai", "gpt-4.1");
        disabledRecorder.finishAttempt("attempt-001", ModelGatewayCallStatus.SUCCEEDED, 200, null, null);

        verifyNoInteractions(invocationMapper, attemptMapper);
    }

    @Test
    void errorMessageShouldBeTruncatedTo512Characters() {
        String longMessage = "错".repeat(600);

        String actual = PersistentModelGatewayInvocationRecorder.truncateErrorMessage(longMessage);

        assertThat(actual).hasSize(512);
        assertThat(actual).isEqualTo("错".repeat(512));
    }

    @Test
    void nullErrorMessageShouldKeepNull() {
        assertThat(PersistentModelGatewayInvocationRecorder.truncateErrorMessage(null)).isNull();
        verify(invocationMapper, never()).insert(any(ModelGatewayInvocationEntity.class));
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<LambdaUpdateWrapper<ModelGatewayInvocationEntity>> invocationUpdateCaptor() {
        return ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<LambdaUpdateWrapper<ModelGatewayAttemptEntity>> attemptUpdateCaptor() {
        return ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    }
}
