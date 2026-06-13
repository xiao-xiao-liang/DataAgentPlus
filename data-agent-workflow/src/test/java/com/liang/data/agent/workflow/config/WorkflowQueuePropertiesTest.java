package com.liang.data.agent.workflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工作流队列配置属性单元测试。
 */
class WorkflowQueuePropertiesTest {

    @Test
    void shouldBindRedisQueueProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("chat.workflow.queue.max-user-running", "3")
                .withProperty("chat.workflow.queue.max-global-running", "12")
                .withProperty("chat.workflow.queue.redis.enabled", "true")
                .withProperty("chat.workflow.queue.redis.key-prefix", "test:workflow:queue")
                .withProperty("chat.workflow.queue.redis.scan-window", "200")
                .withProperty("chat.workflow.queue.redis.recovery-batch-size", "60")
                .withProperty("chat.workflow.queue.redis.recovery-interval", "30s");

        Binder binder = new Binder(ConfigurationPropertySources.get(environment));
        WorkflowQueueProperties properties = binder.bind(
                "chat.workflow.queue",
                Bindable.of(WorkflowQueueProperties.class)
        ).orElseThrow(IllegalStateException::new);

        assertThat(properties.getMaxUserRunning()).isEqualTo(3);
        assertThat(properties.getMaxGlobalRunning()).isEqualTo(12);
        assertThat(properties.getRedis().isEnabled()).isTrue();
        assertThat(properties.getRedis().getKeyPrefix()).isEqualTo("test:workflow:queue");
        assertThat(properties.getRedis().getScanWindow()).isEqualTo(200);
        assertThat(properties.getRedis().getRecoveryBatchSize()).isEqualTo(60);
        assertThat(properties.getRedis().getRecoveryInterval().toMillis()).isEqualTo(30000L);
    }
}
