package com.liang.data.agent.gateway.api;

import com.liang.data.agent.gateway.prompt.ModelMessage;
import com.liang.data.agent.gateway.prompt.ModelMessageRole;
import com.liang.data.agent.gateway.prompt.ModelPrompt;
import com.liang.data.agent.gateway.request.GatewayConstraints;
import com.liang.data.agent.gateway.request.ModelGatewayRequest;
import com.liang.data.agent.gateway.response.GatewayChunk;
import com.liang.data.agent.gateway.response.GatewayResult;
import com.liang.data.agent.gateway.request.ModelCallMode;
import com.liang.data.agent.gateway.response.ModelRoute;
import com.liang.data.agent.gateway.response.ModelUsage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    void shouldRejectTemplateVariablesInDirectMessageMode() {
        assertThatThrownBy(() -> new ModelPrompt(
                null,
                Map.of("question", "查询销售额"),
                List.of(new ModelMessage(ModelMessageRole.USER, "查询销售额"))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模板变量");
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

    @Test
    void shouldSnapshotNestedTemplateVariables() {
        List<Object> options = new ArrayList<>();
        options.add("按月");
        Map<String, Object> filter = new HashMap<>();
        filter.put("region", "华东");
        String[] metrics = new String[] {"销售额"};
        Map<String, Object> variables = new HashMap<>();
        variables.put("options", options);
        variables.put("filter", filter);
        variables.put("metrics", metrics);

        ModelPrompt prompt = ModelPrompt.template("sql-template", variables);
        options.add("按年");
        filter.put("region", "华南");
        metrics[0] = "利润";

        assertThat(prompt.variables().get("options")).isEqualTo(List.of("按月"));
        assertThat(prompt.variables().get("filter")).isEqualTo(Map.of("region", "华东"));
        assertThat(prompt.variables().get("metrics")).isEqualTo(List.of("销售额"));
    }

    @Test
    void shouldReturnUnmodifiableTemplateVariableCollections() {
        ModelPrompt prompt = ModelPrompt.template(
                "sql-template",
                Map.of(
                        "options", new ArrayList<>(List.of("按月")),
                        "filter", new HashMap<>(Map.of("region", "华东"))
                )
        );

        assertThatThrownBy(() -> variableList(prompt, "options").add("按年"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> variableMap(prompt, "filter").put("region", "华南"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectUnsupportedTemplateVariableValue() {
        assertThatThrownBy(() -> ModelPrompt.template("sql-template", Map.of("builder", new StringBuilder("问题"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模板变量");
        assertThatThrownBy(() -> ModelPrompt.template("sql-template", Map.of("counter", new AtomicInteger(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模板变量");
        assertThatThrownBy(() -> ModelPrompt.template("sql-template", Map.of("time", new MutableTemporalAccessor())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模板变量");
    }

    @Test
    void shouldRejectCircularTemplateVariableReferences() {
        Map<String, Object> selfReferenceMap = new HashMap<>();
        selfReferenceMap.put("self", selfReferenceMap);
        List<Object> selfReferenceList = new ArrayList<>();
        selfReferenceList.add(selfReferenceList);
        Object[] selfReferenceArray = new Object[1];
        selfReferenceArray[0] = selfReferenceArray;

        assertThatThrownBy(() -> ModelPrompt.template("sql-template", Map.of("map", selfReferenceMap)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("循环引用");
        assertThatThrownBy(() -> ModelPrompt.template("sql-template", Map.of("list", selfReferenceList)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("循环引用");
        assertThatThrownBy(() -> ModelPrompt.template("sql-template", Map.of("array", selfReferenceArray)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("循环引用");
    }

    @Test
    void shouldAcceptSupportedImmutableTemplateVariableValues() {
        assertThatCode(() -> ModelPrompt.template(
                "sql-template",
                Map.of(
                        "text", "问题",
                        "number", BigInteger.ONE,
                        "decimal", BigDecimal.ONE,
                        "enabled", Boolean.TRUE,
                        "letter", 'A',
                        "role", ModelMessageRole.USER,
                        "date", LocalDate.of(2026, 6, 23)
                )
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldValidateModelUsageTokens() {
        assertThatCode(() -> new ModelUsage(0, 0, 0)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new ModelUsage(-1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token");
        assertThatThrownBy(() -> new ModelUsage(1, -1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token");
        assertThatThrownBy(() -> new ModelUsage(1, 1, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token");
        assertThatThrownBy(() -> new ModelUsage(1, 1, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("总Token");
        assertThatThrownBy(() -> new ModelUsage(Long.MAX_VALUE, 1, Long.MAX_VALUE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("总Token");
    }

    @Test
    void shouldValidateModelRoute() {
        assertThatCode(() -> new ModelRoute("openai", "gpt-4.1", 1, false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new ModelRoute(" ", "gpt-4.1", 1, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("供应商");
        assertThatThrownBy(() -> new ModelRoute("openai", " ", 1, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型");
        assertThatThrownBy(() -> new ModelRoute("openai", "gpt-4.1", 0, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("尝试次数");
    }

    @Test
    void shouldRequireExpectedRequestMode() {
        ModelGatewayRequest blockRequest = new ModelGatewayRequest(
                "CHAT",
                ModelPrompt.direct(List.of(new ModelMessage(ModelMessageRole.USER, "你好"))),
                ModelCallMode.BLOCK,
                null,
                null
        );

        assertThatCode(() -> requireMode(blockRequest, ModelCallMode.BLOCK)).doesNotThrowAnyException();
        assertThatThrownBy(() -> requireMode(blockRequest, ModelCallMode.STREAM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("调用模式");
    }

    @Test
    void shouldValidateGatewayResult() {
        ModelUsage usage = new ModelUsage(1, 2, 3);
        ModelRoute route = new ModelRoute("openai", "gpt-4.1", 1, false);

        assertThatCode(() -> new GatewayResult("invocation-1", "", usage, route, "stop"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new GatewayResult(" ", "", usage, route, "stop"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("调用标识");
        assertThatThrownBy(() -> new GatewayResult("invocation-1", null, usage, route, "stop"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("响应内容");
        assertThatThrownBy(() -> new GatewayResult("invocation-1", "", null, route, "stop"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("使用量");
        assertThatThrownBy(() -> new GatewayResult("invocation-1", "", usage, null, "stop"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("路由");
        assertThatThrownBy(() -> new GatewayResult("invocation-1", "", usage, route, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("结束原因");
    }

    @Test
    void shouldValidateGatewayChunk() {
        ModelUsage usage = new ModelUsage(1, 2, 3);
        ModelRoute route = new ModelRoute("openai", "gpt-4.1", 1, false);

        assertThatCode(() -> new GatewayChunk("invocation-1", "片段", false, null, null, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> new GatewayChunk("invocation-1", "", true, usage, route, "stop"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new GatewayChunk(" ", "片段", false, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("调用标识");
        assertThatThrownBy(() -> new GatewayChunk("invocation-1", null, false, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("响应内容");
        assertThatThrownBy(() -> new GatewayChunk("invocation-1", "", true, null, route, "stop"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("使用量");
        assertThatThrownBy(() -> new GatewayChunk("invocation-1", "", true, usage, null, "stop"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("路由");
        assertThatThrownBy(() -> new GatewayChunk("invocation-1", "", true, usage, route, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("结束原因");
    }

    private static void requireMode(ModelGatewayRequest request, ModelCallMode expected) {
        try {
            request.getClass().getMethod("requireMode", ModelCallMode.class).invoke(request, expected);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError("调用模式校验异常", exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("缺少调用模式校验方法", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> variableList(ModelPrompt prompt, String key) {
        return (List<Object>) prompt.variables().get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> variableMap(ModelPrompt prompt, String key) {
        return (Map<String, Object>) prompt.variables().get(key);
    }

    private static class MutableTemporalAccessor implements TemporalAccessor {

        @Override
        public boolean isSupported(TemporalField field) {
            return false;
        }

        @Override
        public long getLong(TemporalField field) {
            throw new UnsupportedOperationException("不支持的时间字段");
        }

        @Override
        public <R> R query(TemporalQuery<R> query) {
            return null;
        }
    }
}
