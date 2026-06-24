package com.liang.data.agent.config;

import com.liang.data.agent.gateway.context.GatewayExecutionContextFactory;
import com.liang.data.agent.gateway.context.TraceIdProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型网关上下文配置测试，验证观测配置缺省场景下的 Bean 创建和配置项约束。
 */
class ModelGatewayContextConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ModelGatewayContextConfiguration.class);

    @Test
    void shouldCreateGatewayContextBeansWhenTracerMissing() {
        contextRunner.run(context -> {
            // 1. 验证未注册 Tracer 时仍能创建模型网关上下文相关默认 Bean。
            assertThat(context).hasSingleBean(TraceIdProvider.class);
            assertThat(context).hasSingleBean(GatewayExecutionContextFactory.class);

            // 2. 验证无当前链路上下文时 traceId 按约定返回 null。
            TraceIdProvider traceIdProvider = context.getBean(TraceIdProvider.class);
            assertThat(traceIdProvider.currentTraceId()).isNull();
        });
    }

    @Test
    void shouldUseProjectLevelFullTracesEndpointProperty() {
        // 1. 加载应用配置，避免启动完整 SpringBootTest。
        Properties applicationProperties = loadApplicationProperties();

        // 2. 验证 OTLP traces endpoint 使用项目级完整端点变量，并暴露 Prometheus 端点。
        assertThat(applicationProperties.getProperty("management.otlp.tracing.endpoint"))
                .isEqualTo("${OBSERVABILITY_OTLP_TRACES_ENDPOINT:http://localhost:4318/v1/traces}");
        assertThat(applicationProperties.getProperty("management.endpoints.web.exposure.include"))
                .contains("prometheus");
    }

    private static Properties loadApplicationProperties() {
        // 1. 使用 Spring YAML 工具读取 classpath 下的 application.yml。
        YamlPropertiesFactoryBean yamlPropertiesFactoryBean = new YamlPropertiesFactoryBean();
        yamlPropertiesFactoryBean.setResources(new ClassPathResource("application.yml"));

        // 2. 返回扁平化配置，供测试断言关键配置项。
        Properties properties = yamlPropertiesFactoryBean.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
