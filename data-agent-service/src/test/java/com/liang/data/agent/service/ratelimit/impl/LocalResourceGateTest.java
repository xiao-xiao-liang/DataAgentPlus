package com.liang.data.agent.service.ratelimit.impl;

import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.common.ratelimit.ResourceType;
import com.liang.data.agent.service.ratelimit.ResourcePermit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地资源门控测试。
 */
class LocalResourceGateTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void springContextShouldCreateLocalResourceGate() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LocalResourceGate.class);
        });
    }

    @Test
    void tryAcquireShouldRejectWhenLimitReached() {
        LocalResourceGate gate = new LocalResourceGate(Map.of(ResourceType.LLM_CALL, 1));

        ResourcePermit first = gate.tryAcquire(ResourceType.LLM_CALL, "owner-1", Duration.ZERO);
        ResourcePermit second = gate.tryAcquire(ResourceType.LLM_CALL, "owner-2", Duration.ZERO);

        assertThat(first.acquired()).isTrue();
        assertThat(second.acquired()).isFalse();

        first.close();
    }

    @Test
    void closeShouldReleasePermit() {
        LocalResourceGate gate = new LocalResourceGate(Map.of(ResourceType.SQL_EXECUTION, 1));

        ResourcePermit first = gate.tryAcquire(ResourceType.SQL_EXECUTION, "owner-1", Duration.ZERO);
        first.close();
        ResourcePermit second = gate.tryAcquire(ResourceType.SQL_EXECUTION, "owner-2", Duration.ZERO);

        assertThat(first.acquired()).isTrue();
        assertThat(second.acquired()).isTrue();

        second.close();
    }

    @Test
    void closeShouldReleaseOnlyOnce() {
        LocalResourceGate gate = new LocalResourceGate(Map.of(ResourceType.PYTHON_EXECUTION, 1));

        ResourcePermit first = gate.tryAcquire(ResourceType.PYTHON_EXECUTION, "owner-1", Duration.ZERO);
        first.close();
        first.close();
        ResourcePermit second = gate.tryAcquire(ResourceType.PYTHON_EXECUTION, "owner-2", Duration.ZERO);
        ResourcePermit third = gate.tryAcquire(ResourceType.PYTHON_EXECUTION, "owner-3", Duration.ZERO);

        assertThat(second.acquired()).isTrue();
        assertThat(third.acquired()).isFalse();

        second.close();
    }

    /**
     * 构造本地资源门控的 Spring 注入上下文，复现应用启动时的构造器选择逻辑。
     */
    @Configuration
    @Import(LocalResourceGate.class)
    static class TestConfiguration {

        @Bean
        DataAgentProperties dataAgentProperties() {
            return new DataAgentProperties();
        }
    }
}
