package com.liang.data.agent.ai.code.docker;

import com.liang.data.agent.ai.code.model.TaskResponse;
import com.liang.data.agent.ai.code.strategy.DockerPythonExecutor;
import com.liang.data.agent.common.config.DataAgentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Docker Java Python 沙箱真实容器集成测试。
 */
class DockerJavaSandboxClientIntegrationTest {

    private static final String IMAGE = "data-agent-sandbox-py:3.10";

    @Test
    @EnabledIfSystemProperty(named = "docker.integration.enabled", matches = "true")
    void shouldExecutePandasAnalysisInRestrictedContainer() {
        DockerJavaSandboxClient client = new DockerJavaSandboxClient();
        DataAgentProperties properties = new DataAgentProperties();
        properties.getCodeExecutor().setDockerImage(IMAGE);
        DockerPythonExecutor executor = new DockerPythonExecutor(client, properties);
        String code = """
                import json
                import pandas as pd
                import sys

                data = json.load(sys.stdin)
                frame = pd.DataFrame(data)
                print(int(frame["amount"].sum()))
                """;

        // 1. 验证 Docker 服务和目标镜像可用
        assertThat(executor.isAvailable()).isTrue();

        // 2. 验证受限容器能够完成 pandas 数据分析
        TaskResponse result = executor.execute(code, "[{\"amount\": 12}, {\"amount\": 30}]", 30);

        assertThat(result.isSuccess()).as(result.getStderr()).isTrue();
        assertThat(result.getStdout()).isEqualTo("42");
    }
}
