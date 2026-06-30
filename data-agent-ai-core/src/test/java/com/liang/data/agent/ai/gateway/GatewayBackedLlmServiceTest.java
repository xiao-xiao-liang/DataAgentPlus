package com.liang.data.agent.ai.gateway;

import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.common.enums.LlmServiceMode;
import com.liang.data.agent.gateway.api.GatewayChunk;
import com.liang.data.agent.gateway.api.GatewayResult;
import com.liang.data.agent.gateway.api.ModelCallMode;
import com.liang.data.agent.gateway.api.ModelGateway;
import com.liang.data.agent.gateway.api.ModelGatewayRequest;
import com.liang.data.agent.gateway.api.ModelGatewayScenes;
import com.liang.data.agent.gateway.api.ModelMessage;
import com.liang.data.agent.gateway.api.ModelMessageRole;
import com.liang.data.agent.gateway.api.ModelRoute;
import com.liang.data.agent.gateway.api.ModelUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

        List<ChatResponse> responses = service.callUser(ModelGatewayScenes.SQL_GENERATION, "生成SQL")
                .collectList()
                .block();

        ArgumentCaptor<ModelGatewayRequest> requestCaptor = ArgumentCaptor.forClass(ModelGatewayRequest.class);
        verify(modelGateway).call(requestCaptor.capture());
        verify(modelGateway, never()).stream(any(ModelGatewayRequest.class));
        ModelGatewayRequest request = requestCaptor.getValue();
        assertThat(request.sceneCode()).isEqualTo(ModelGatewayScenes.SQL_GENERATION);
        assertThat(request.mode()).isEqualTo(ModelCallMode.BLOCK);
        assertThat(request.prompt().messages()).containsExactly(new ModelMessage(ModelMessageRole.USER, "生成SQL"));
        assertThat(ChatResponseUtil.getText(responses.getFirst())).isEqualTo("ok");
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
        assertThat(request.sceneCode()).isEqualTo(ModelGatewayScenes.LEGACY_SYSTEM_USER);
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
        assertThat(request.sceneCode()).isEqualTo(ModelGatewayScenes.LEGACY_SYSTEM_ONLY);
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
        assertThat(requestCaptor.getValue().sceneCode()).isEqualTo(ModelGatewayScenes.LEGACY_USER_ONLY);
        assertThat(requestCaptor.getValue().mode()).isEqualTo(ModelCallMode.STREAM);
        assertThat(responses).containsExactly("你", "好");
    }
}
