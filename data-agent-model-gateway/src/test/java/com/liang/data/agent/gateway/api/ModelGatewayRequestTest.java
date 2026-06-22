package com.liang.data.agent.gateway.api;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模型网关请求协议测试。
 *
 * <p>验证请求对象的不可变性、默认值及调用参数校验规则。</p>
 */
class ModelGatewayRequestTest {

    @Test
    void shouldCopyMessagesAndTags() {
        List<ModelMessage> messages = new ArrayList<>();
        messages.add(new ModelMessage(ModelMessageRole.USER, "查询销售额"));
        Map<String, String> tags = new HashMap<>();
        tags.put("feature", "nl2sql");

        ModelGatewayRequest request = new ModelGatewayRequest(
                "SQL_GENERATION",
                ModelPrompt.direct(messages),
                ModelCallMode.BLOCK,
                GatewayConstraints.defaults(),
                tags
        );

        // 1. 修改构造请求时传入的原始集合。
        messages.clear();
        tags.clear();

        // 2. 验证请求内部状态未被外部修改影响。
        assertThat(request.prompt().messages()).hasSize(1);
        assertThat(request.tags()).containsEntry("feature", "nl2sql");
    }

    @Test
    void shouldRejectBlankSceneCode() {
        assertThatThrownBy(() -> new ModelGatewayRequest(
                " ",
                ModelPrompt.direct(List.of(new ModelMessage(ModelMessageRole.USER, "问题"))),
                ModelCallMode.BLOCK,
                GatewayConstraints.defaults(),
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("场景编码");
    }

    @Test
    void shouldRequireExactlyOnePromptMode() {
        assertThatThrownBy(() -> new ModelPrompt(null, Map.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须且只能提供一种");

        assertThatThrownBy(() -> new ModelPrompt(
                "sql-template",
                Map.of("question", "销售额"),
                List.of(new ModelMessage(ModelMessageRole.USER, "查询销售额"))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须且只能提供一种");
    }

    @Test
    void shouldCopyTemplateVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("question", "查询销售额");

        ModelPrompt prompt = ModelPrompt.template("sql-template", variables);
        variables.clear();

        assertThat(prompt.variables()).containsEntry("question", "查询销售额");
    }

    @Test
    void shouldRejectNonPositiveTimeout() {
        assertThatThrownBy(() -> new GatewayConstraints(Duration.ZERO, null, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超时");
        assertThatThrownBy(() -> new GatewayConstraints(Duration.ofSeconds(-1), null, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超时");
    }

    @Test
    void shouldRejectNonPositiveMaxOutputTokens() {
        assertThatThrownBy(() -> new GatewayConstraints(Duration.ofSeconds(30), 0, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最大输出Token");
        assertThatThrownBy(() -> new GatewayConstraints(Duration.ofSeconds(30), -1, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最大输出Token");
    }

    @Test
    void shouldRejectNegativeBudgetLimit() {
        assertThatThrownBy(() -> new GatewayConstraints(
                Duration.ofSeconds(30),
                null,
                new BigDecimal("-0.01"),
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("预算上限");
    }

    @Test
    void shouldUseRequestDefaults() {
        ModelGatewayRequest request = new ModelGatewayRequest(
                "CHAT",
                ModelPrompt.direct(List.of(new ModelMessage(ModelMessageRole.USER, "你好"))),
                null,
                null,
                null
        );

        assertThat(request.mode()).isEqualTo(ModelCallMode.BLOCK);
        assertThat(request.constraints().timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(request.tags()).isEmpty();
    }
}
