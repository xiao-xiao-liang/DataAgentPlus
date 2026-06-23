package com.liang.data.agent.gateway.error;

import com.liang.data.agent.gateway.api.ModelMessage;
import com.liang.data.agent.gateway.api.ModelMessageRole;
import com.liang.data.agent.gateway.prompt.ResolvedPrompt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模型网关结构化错误与提示词契约测试。
 *
 * <p>验证错误码语义、模型网关异常，以及解析后提示词对象的不可变契约。</p>
 */
class ModelGatewayErrorCodeTest {

    @Test
    void shouldExposeRetryableAndDegradableSemantics() {
        assertThat(ModelGatewayErrorCode.PROVIDER_TIMEOUT.retryable()).isTrue();
        assertThat(ModelGatewayErrorCode.PROVIDER_TIMEOUT.degradable()).isTrue();
        assertThat(ModelGatewayErrorCode.AUTHENTICATION_FAILED.retryable()).isFalse();
        assertThat(ModelGatewayErrorCode.BUDGET_EXCEEDED.degradable()).isFalse();
    }

    @Test
    void shouldExposeNonBlankAliErrorCodeSemanticFields() {
        for (ModelGatewayErrorCode errorCode : ModelGatewayErrorCode.values()) {
            assertThat(errorCode.code()).isNotBlank();
            assertThat(errorCode.message()).isNotBlank();
            assertThat(errorCode.code()).matches("^[ABC]\\d{6}$");
        }
    }

    @Test
    void shouldKeepErrorCodeUnique() {
        Set<String> codes = List.of(ModelGatewayErrorCode.values())
                .stream()
                .map(ModelGatewayErrorCode::code)
                .collect(Collectors.toSet());

        assertThat(codes).hasSize(ModelGatewayErrorCode.values().length);
    }

    @Test
    void shouldFallbackExceptionMessageToErrorCodeMessage() {
        ModelGatewayException exception = new ModelGatewayException(ModelGatewayErrorCode.PROVIDER_TIMEOUT);

        assertThat(exception.getMessage()).isEqualTo(ModelGatewayErrorCode.PROVIDER_TIMEOUT.message());
        assertThat(exception.getErrorMessage()).isEqualTo(ModelGatewayErrorCode.PROVIDER_TIMEOUT.message());
        assertThat(exception.getGatewayErrorCode()).isEqualTo(ModelGatewayErrorCode.PROVIDER_TIMEOUT);
    }

    @Test
    void shouldFallbackBlankExceptionMessageToErrorCodeMessage() {
        ModelGatewayException emptyMessageException = new ModelGatewayException(
                ModelGatewayErrorCode.PROVIDER_TIMEOUT,
                ""
        );
        RuntimeException cause = new RuntimeException("连接超时");
        ModelGatewayException blankMessageException = new ModelGatewayException(
                ModelGatewayErrorCode.PROVIDER_TIMEOUT,
                "   ",
                cause
        );

        assertThat(emptyMessageException.getMessage()).isEqualTo(ModelGatewayErrorCode.PROVIDER_TIMEOUT.message());
        assertThat(emptyMessageException.getErrorMessage()).isEqualTo(ModelGatewayErrorCode.PROVIDER_TIMEOUT.message());
        assertThat(blankMessageException.getMessage()).isEqualTo(ModelGatewayErrorCode.PROVIDER_TIMEOUT.message());
        assertThat(blankMessageException.getErrorMessage()).isEqualTo(ModelGatewayErrorCode.PROVIDER_TIMEOUT.message());
        assertThat(blankMessageException.getCause()).isSameAs(cause);
    }

    @Test
    void shouldKeepCustomExceptionMessageAndCause() {
        RuntimeException cause = new RuntimeException("连接超时");

        ModelGatewayException exception = new ModelGatewayException(
                ModelGatewayErrorCode.PROVIDER_TIMEOUT,
                "供应商响应超时",
                cause
        );

        assertThat(exception.getMessage()).isEqualTo("供应商响应超时");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getGatewayErrorCode()).isEqualTo(ModelGatewayErrorCode.PROVIDER_TIMEOUT);
    }

    @Test
    void shouldRejectNullGatewayErrorCode() {
        assertThatThrownBy(() -> new ModelGatewayException(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("模型网关错误码");
    }

    @Test
    void shouldCopyResolvedPromptMessages() {
        List<ModelMessage> messages = new ArrayList<>();
        messages.add(new ModelMessage(ModelMessageRole.USER, "查询销售额"));

        ResolvedPrompt prompt = new ResolvedPrompt("sql-template", "v1", messages);
        messages.clear();

        assertThat(prompt.messages()).containsExactly(new ModelMessage(ModelMessageRole.USER, "查询销售额"));
        assertThatThrownBy(() -> prompt.messages().add(new ModelMessage(ModelMessageRole.USER, "新增问题")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectInvalidResolvedPromptFields() {
        List<ModelMessage> messages = List.of(new ModelMessage(ModelMessageRole.USER, "查询销售额"));

        assertThatThrownBy(() -> new ResolvedPrompt(" ", "v1", messages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模板标识");
        assertThatThrownBy(() -> new ResolvedPrompt(null, "v1", messages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模板标识");
        assertThatThrownBy(() -> new ResolvedPrompt("sql-template", " ", messages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模板版本");
        assertThatThrownBy(() -> new ResolvedPrompt("sql-template", null, messages))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模板版本");
        assertThatThrownBy(() -> new ResolvedPrompt("sql-template", "v1", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("提示词消息");
        assertThatThrownBy(() -> new ResolvedPrompt("sql-template", "v1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("提示词消息");
    }

    @Test
    void shouldRejectNullResolvedPromptMessage() {
        List<ModelMessage> messages = new ArrayList<>();
        messages.add(null);

        assertThatThrownBy(() -> new ResolvedPrompt("sql-template", "v1", messages))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("提示词消息");
    }
}
