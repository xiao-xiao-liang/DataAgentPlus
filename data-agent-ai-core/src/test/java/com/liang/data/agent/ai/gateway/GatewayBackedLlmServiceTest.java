package com.liang.data.agent.ai.gateway;

import com.liang.data.agent.ai.config.AiCoreAutoConfiguration;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.model.AiModelRegistry;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.common.enums.LlmServiceMode;
import com.liang.data.agent.dal.mapper.ModelGatewayAttemptMapper;
import com.liang.data.agent.dal.mapper.ModelGatewayInvocationMapper;
import com.liang.data.agent.gateway.response.GatewayChunk;
import com.liang.data.agent.gateway.request.GatewayConstraints;
import com.liang.data.agent.gateway.response.GatewayResult;
import com.liang.data.agent.gateway.request.ModelCallMode;
import com.liang.data.agent.gateway.api.ModelGateway;
import com.liang.data.agent.gateway.request.ModelGatewayRequest;
import com.liang.data.agent.gateway.constants.ModelGatewayConstant;
import com.liang.data.agent.gateway.prompt.ModelMessage;
import com.liang.data.agent.gateway.prompt.ModelMessageRole;
import com.liang.data.agent.gateway.response.ModelRoute;
import com.liang.data.agent.gateway.response.ModelUsage;
import com.liang.data.agent.gateway.context.GatewayExecutionContext;
import com.liang.data.agent.gateway.error.ModelGatewayErrorCode;
import com.liang.data.agent.gateway.error.ModelGatewayException;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 网关驱动 LLM 服务测试，验证旧接口调用会统一进入模型网关。
 */
class GatewayBackedLlmServiceTest {

    private static final ModelRoute ROUTE = new ModelRoute("openai", "gpt-4o-mini", 1, false);

    private static final ModelUsage USAGE = new ModelUsage(1, 2, 3);

    @Test
    void callUserWithSceneCodeShouldUseGatewayBlockCallAndBuildUserMessage() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        DataAgentProperties properties = new DataAgentProperties();
        properties.setLlmServiceMode(LlmServiceMode.BLOCK);
        when(modelGateway.call(any(ModelGatewayRequest.class)))
                .thenReturn(Mono.just(new GatewayResult("invocation-001", "ok", USAGE, ROUTE, "stop")));
        GatewayBackedLlmService service = new GatewayBackedLlmService(modelGateway, properties);

        List<ChatResponse> responses = service.callUser(ModelGatewayConstant.SQL_GENERATION, "生成SQL")
                .collectList()
                .block();

        ArgumentCaptor<ModelGatewayRequest> requestCaptor = ArgumentCaptor.forClass(ModelGatewayRequest.class);
        verify(modelGateway).call(requestCaptor.capture());
        verify(modelGateway, never()).stream(any(ModelGatewayRequest.class));
        ModelGatewayRequest request = requestCaptor.getValue();
        assertThat(request.sceneCode()).isEqualTo(ModelGatewayConstant.SQL_GENERATION);
        assertThat(request.mode()).isEqualTo(ModelCallMode.BLOCK);
        assertThat(request.prompt().messages()).containsExactly(new ModelMessage(ModelMessageRole.USER, "生成SQL"));
        assertThat(request.constraints()).isEqualTo(GatewayConstraints.defaults());
        assertThat(request.tags()).isEmpty();
        assertThat(ChatResponseUtil.getText(responses.getFirst())).isEqualTo("ok");
    }

    @Test
    void callWithSceneCodeShouldUseGatewayBlockCallAndBuildSystemUserMessages() {
        // 1. 准备 BLOCK 模式网关服务，隔离外部模型调用。
        ModelGateway modelGateway = mock(ModelGateway.class);
        DataAgentProperties properties = new DataAgentProperties();
        properties.setLlmServiceMode(LlmServiceMode.BLOCK);
        when(modelGateway.call(any(ModelGatewayRequest.class)))
                .thenReturn(Mono.just(new GatewayResult("invocation-001", "ok", USAGE, ROUTE, "stop")));
        GatewayBackedLlmService service = new GatewayBackedLlmService(modelGateway, properties);

        // 2. 通过显式场景码的 system/user 入口发起调用。
        service.call(ModelGatewayConstant.SQL_GENERATION, "系统提示", "用户提示").collectList().block();

        // 3. 验证请求场景、模式、消息顺序、默认约束与空业务标签。
        ArgumentCaptor<ModelGatewayRequest> requestCaptor = ArgumentCaptor.forClass(ModelGatewayRequest.class);
        verify(modelGateway).call(requestCaptor.capture());
        verify(modelGateway, never()).stream(any(ModelGatewayRequest.class));
        assertGatewayRequest(requestCaptor.getValue(), ModelGatewayConstant.SQL_GENERATION, ModelCallMode.BLOCK,
                new ModelMessage(ModelMessageRole.SYSTEM, "系统提示"),
                new ModelMessage(ModelMessageRole.USER, "用户提示"));
    }

    @Test
    void callSystemWithSceneCodeShouldUseGatewayBlockCallAndBuildSystemMessage() {
        // 1. 准备 BLOCK 模式网关服务，隔离外部模型调用。
        ModelGateway modelGateway = mock(ModelGateway.class);
        DataAgentProperties properties = new DataAgentProperties();
        properties.setLlmServiceMode(LlmServiceMode.BLOCK);
        when(modelGateway.call(any(ModelGatewayRequest.class)))
                .thenReturn(Mono.just(new GatewayResult("invocation-001", "ok", USAGE, ROUTE, "stop")));
        GatewayBackedLlmService service = new GatewayBackedLlmService(modelGateway, properties);

        // 2. 通过显式场景码的 system 入口发起调用。
        service.callSystem(ModelGatewayConstant.SQL_GENERATION, "系统提示").collectList().block();

        // 3. 验证请求场景、模式、SYSTEM 消息、默认约束与空业务标签。
        ArgumentCaptor<ModelGatewayRequest> requestCaptor = ArgumentCaptor.forClass(ModelGatewayRequest.class);
        verify(modelGateway).call(requestCaptor.capture());
        verify(modelGateway, never()).stream(any(ModelGatewayRequest.class));
        assertGatewayRequest(requestCaptor.getValue(), ModelGatewayConstant.SQL_GENERATION, ModelCallMode.BLOCK,
                new ModelMessage(ModelMessageRole.SYSTEM, "系统提示"));
    }

    @Test
    void legacyCallShouldUseLegacySceneAndBuildSystemUserMessages() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        DataAgentProperties properties = new DataAgentProperties();
        properties.setLlmServiceMode(LlmServiceMode.BLOCK);
        when(modelGateway.call(any(ModelGatewayRequest.class)))
                .thenReturn(Mono.just(new GatewayResult("invocation-001", "ok", USAGE, ROUTE, "stop")));
        GatewayBackedLlmService service = new GatewayBackedLlmService(modelGateway, properties);

        service.call("系统提示", "用户提示").collectList().block();

        ArgumentCaptor<ModelGatewayRequest> requestCaptor = ArgumentCaptor.forClass(ModelGatewayRequest.class);
        verify(modelGateway).call(requestCaptor.capture());
        ModelGatewayRequest request = requestCaptor.getValue();
        assertThat(request.sceneCode()).isEqualTo(ModelGatewayConstant.LEGACY_SYSTEM_USER);
        assertThat(request.prompt().messages()).containsExactly(
                new ModelMessage(ModelMessageRole.SYSTEM, "系统提示"),
                new ModelMessage(ModelMessageRole.USER, "用户提示"));
    }

    @Test
    void legacyCallSystemShouldUseLegacySceneAndBuildSystemMessage() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        DataAgentProperties properties = new DataAgentProperties();
        properties.setLlmServiceMode(LlmServiceMode.BLOCK);
        when(modelGateway.call(any(ModelGatewayRequest.class)))
                .thenReturn(Mono.just(new GatewayResult("invocation-001", "ok", USAGE, ROUTE, "stop")));
        GatewayBackedLlmService service = new GatewayBackedLlmService(modelGateway, properties);

        service.callSystem("系统提示").collectList().block();

        ArgumentCaptor<ModelGatewayRequest> requestCaptor = ArgumentCaptor.forClass(ModelGatewayRequest.class);
        verify(modelGateway).call(requestCaptor.capture());
        ModelGatewayRequest request = requestCaptor.getValue();
        assertThat(request.sceneCode()).isEqualTo(ModelGatewayConstant.LEGACY_SYSTEM_ONLY);
        assertThat(request.prompt().messages()).containsExactly(new ModelMessage(ModelMessageRole.SYSTEM, "系统提示"));
    }

    @Test
    void defaultStreamModeShouldUseGatewayStreamAndSkipFinishedChunk() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        DataAgentProperties properties = new DataAgentProperties();
        when(modelGateway.stream(any(ModelGatewayRequest.class)))
                .thenReturn(Flux.just(
                        new GatewayChunk("invocation-001", "你", false, null, null, null),
                        new GatewayChunk("invocation-001", "好", false, null, null, null),
                        new GatewayChunk("invocation-001", "", true, USAGE, ROUTE, "stop")));
        GatewayBackedLlmService service = new GatewayBackedLlmService(modelGateway, properties);

        List<String> responses = service.callUser("你好")
                .map(ChatResponseUtil::getText)
                .collectList()
                .block();

        ArgumentCaptor<ModelGatewayRequest> requestCaptor = ArgumentCaptor.forClass(ModelGatewayRequest.class);
        verify(modelGateway).stream(requestCaptor.capture());
        verify(modelGateway, never()).call(any(ModelGatewayRequest.class));
        assertThat(requestCaptor.getValue().sceneCode()).isEqualTo(ModelGatewayConstant.LEGACY_USER_ONLY);
        assertThat(requestCaptor.getValue().mode()).isEqualTo(ModelCallMode.STREAM);
        assertThat(responses).containsExactly("你", "好");
    }

    @Test
    void streamModeShouldExposeInvalidResponseAsEmptyFluxForNodeRetry() {
        // 1. 准备流式网关响应无有效内容时抛出的结构化异常。
        ModelGateway modelGateway = mock(ModelGateway.class);
        DataAgentProperties properties = new DataAgentProperties();
        when(modelGateway.stream(any(ModelGatewayRequest.class)))
                .thenReturn(Flux.error(new ModelGatewayException(ModelGatewayErrorCode.RESPONSE_INVALID)));
        GatewayBackedLlmService service = new GatewayBackedLlmService(modelGateway, properties);

        // 2. 验证适配层将空响应类错误转换为空流，交给上层节点的空响应重试逻辑处理。
        List<ChatResponse> responses = service.callUser(ModelGatewayConstant.QUERY_ENHANCE, "增强查询")
                .collectList()
                .block();

        assertThat(responses).isEmpty();
        verify(modelGateway).stream(any(ModelGatewayRequest.class));
        verify(modelGateway, never()).call(any(ModelGatewayRequest.class));
    }

    @Test
    void autoConfigurationShouldExposeGatewayBackedLlmServiceBean() {
        // 1. 直接实例化自动配置，避免引入完整 Spring 上下文。
        DataAgentProperties properties = new DataAgentProperties();
        AiCoreAutoConfiguration configuration = new AiCoreAutoConfiguration(properties);

        // 2. 验证默认 LlmService Bean 已切换到网关适配实现。
        LlmService llmService = configuration.llmService(mock(ModelGateway.class));

        assertThat(llmService).isInstanceOf(GatewayBackedLlmService.class);
    }

    @Test
    void autoConfigurationShouldExposeOpenAiCompatibleGatewayProviderBean() {
        // 1. 直接实例化自动配置，避免引入完整 Spring 上下文。
        DataAgentProperties properties = new DataAgentProperties();
        AiCoreAutoConfiguration configuration = new AiCoreAutoConfiguration(properties);

        // 2. 验证默认 Provider Bean 使用 OpenAI 兼容实现。
        OpenAiCompatibleGatewayProvider provider =
                configuration.openAiCompatibleGatewayProvider(mock(AiModelRegistry.class));

        assertThat(provider).isInstanceOf(OpenAiCompatibleGatewayProvider.class);
    }

    @Test
    void autoConfigurationShouldExposeDefaultModelGatewayBeanWithoutMeterRegistry() throws Exception {
        // 1. 准备无 MeterRegistry 的依赖，并设置可识别的默认超时。
        DataAgentProperties properties = new DataAgentProperties();
        properties.getModelGateway().setDefaultTimeoutSeconds(7);
        AiCoreAutoConfiguration configuration = new AiCoreAutoConfiguration(properties);
        OpenAiCompatibleGatewayProvider provider =
                new OpenAiCompatibleGatewayProvider(mock(AiModelRegistry.class));
        ModelGatewayInvocationRecorder recorder = mock(ModelGatewayInvocationRecorder.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);
        when(meterRegistryProvider.getIfAvailable()).thenReturn(null);

        // 2. 调用自动配置方法，验证无指标注册器时仍能构造默认网关。
        ModelGateway modelGateway = configuration.modelGateway(provider, recorder, meterRegistryProvider);

        // 3. 通过反射读取私有字段，验证超时来自 modelGateway 配置。
        assertThat(modelGateway).isInstanceOf(DefaultModelGateway.class);
        assertThat(readDefaultTimeout(modelGateway)).isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void autoConfigurationShouldExposeDisabledPersistentRecorderWhenPersistenceDisabled() {
        // 1. 关闭持久化开关，直接调用自动配置的记录器 Bean 方法。
        DataAgentProperties properties = new DataAgentProperties();
        properties.getModelGateway().setPersistenceEnabled(false);
        AiCoreAutoConfiguration configuration = new AiCoreAutoConfiguration(properties);
        ModelGatewayInvocationMapper invocationMapper = mock(ModelGatewayInvocationMapper.class);
        ModelGatewayAttemptMapper attemptMapper = mock(ModelGatewayAttemptMapper.class);

        ModelGatewayInvocationRecorder recorder =
                configuration.modelGatewayInvocationRecorder(invocationMapper, attemptMapper);

        // 2. 调用完整生命周期入口，验证关闭持久化后不访问 Mapper。
        assertThat(recorder).isInstanceOf(PersistentModelGatewayInvocationRecorder.class);
        GatewayExecutionContext context =
                new GatewayExecutionContext("run-001", "trace-001", "session-001", null, null, null);
        assertThatCode(() -> {
            recorder.startInvocation("invocation-001", context, ModelGatewayConstant.SQL_GENERATION, ModelCallMode.BLOCK);
            recorder.finishInvocation("invocation-001", ModelGatewayCallStatus.SUCCEEDED, null, null, null, null);
            recorder.startAttempt("invocation-001", "attempt-001", 1, "openai", "gpt-4o-mini");
            recorder.finishAttempt("attempt-001", ModelGatewayCallStatus.SUCCEEDED, 200, null, null);
        }).doesNotThrowAnyException();
        verifyNoInteractions(invocationMapper, attemptMapper);
    }

    private static void assertGatewayRequest(ModelGatewayRequest request, String sceneCode, ModelCallMode mode,
                                             ModelMessage... messages) {
        assertThat(request.sceneCode()).isEqualTo(sceneCode);
        assertThat(request.mode()).isEqualTo(mode);
        assertThat(request.prompt().messages()).containsExactly(messages);
        assertThat(request.constraints()).isEqualTo(GatewayConstraints.defaults());
        assertThat(request.tags()).isEmpty();
    }

    private static Duration readDefaultTimeout(ModelGateway modelGateway) throws Exception {
        Field field = DefaultModelGateway.class.getDeclaredField("defaultTimeout");
        field.setAccessible(true);
        return (Duration) field.get(modelGateway);
    }
}
