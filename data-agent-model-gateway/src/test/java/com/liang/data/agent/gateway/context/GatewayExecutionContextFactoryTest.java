package com.liang.data.agent.gateway.context;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模型网关执行上下文工厂测试。
 *
 * <p>验证工作流运行编号、链路追踪编号、业务身份字段以及 Reactor 上下文传播规则。</p>
 */
class GatewayExecutionContextFactoryTest {

    @Test
    void shouldCreateContextAndPropagateWithReactorContext() {
        GatewayExecutionContextFactory factory = new GatewayExecutionContextFactory(() -> "trace-001");

        GatewayExecutionContext context = factory.create("session-001", 11L, 22, "tenant-001");

        assertThat(context.runId()).isNotBlank();
        assertThat(context.runId()).hasSize(36);
        assertThat(context.traceId()).isEqualTo("trace-001");
        assertThat(context.sessionId()).isEqualTo("session-001");
        assertThat(context.userId()).isEqualTo(11L);
        assertThat(context.agentId()).isEqualTo(22);
        assertThat(context.tenantId()).isEqualTo("tenant-001");

        GatewayExecutionContext actual = Mono.deferContextual(GatewayReactorContext::current)
                .contextWrite(GatewayReactorContext.with(context))
                .block();

        assertThat(actual).isSameAs(context);
    }

    @Test
    void shouldUseNullTraceIdWhenProviderReturnsBlank() {
        GatewayExecutionContextFactory factory = new GatewayExecutionContextFactory(() -> " ");

        GatewayExecutionContext context = factory.create("session-001", null, null, null);

        assertThat(context.runId()).isNotBlank();
        assertThat(context.traceId()).isNull();
    }

    @Test
    void shouldUseNullTraceIdWhenProviderReturnsNull() {
        GatewayExecutionContextFactory factory = new GatewayExecutionContextFactory(() -> null);

        GatewayExecutionContext context = factory.create("session-001", null, null, null);

        assertThat(context.runId()).isNotBlank();
        assertThat(context.traceId()).isNull();
    }

    @Test
    void shouldValidateExecutionContextFields() {
        assertThatThrownBy(() -> new GatewayExecutionContext(" ", "trace-001", "session-001", 1L, 1, "tenant-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("运行编号");
        assertThatThrownBy(() -> new GatewayExecutionContext("run-001", "trace-001", " ", 1L, 1, "tenant-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("会话编号");
        assertThatThrownBy(() -> new GatewayExecutionContext("run-001", "trace-001", "session-001", 0L, 1, "tenant-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户编号");
        assertThatThrownBy(() -> new GatewayExecutionContext("run-001", "trace-001", "session-001", 1L, 0, "tenant-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("智能体编号");
        assertThatThrownBy(() -> new GatewayExecutionContext("run-001", " ", "session-001", 1L, 1, "tenant-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("链路追踪编号");
        assertThatThrownBy(() -> new GatewayExecutionContext("run-001", "trace-001", "session-001", 1L, 1, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("租户编号");
    }

    @Test
    void shouldReturnEmptyWhenContextMissing() {
        GatewayExecutionContext actual = Mono.deferContextual(GatewayReactorContext::current)
                .contextWrite(Context.empty())
                .block();

        assertThat(actual).isNull();
    }

    @Test
    void shouldThrowWhenRequiredContextMissing() {
        assertThatThrownBy(() -> GatewayReactorContext.currentOrThrow(Context.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("执行上下文不存在");
    }

    @Test
    void shouldRejectNullContextWhenWritingReactorContext() {
        assertThatThrownBy(() -> GatewayReactorContext.with(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("执行上下文");
    }
}
