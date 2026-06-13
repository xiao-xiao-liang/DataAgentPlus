package com.liang.data.agent.ai.code.strategy;

import com.liang.data.agent.ai.code.PythonExecutionStrategy;
import com.liang.data.agent.ai.code.docker.DockerSandboxClient;
import com.liang.data.agent.ai.code.docker.DockerSandboxRequest;
import com.liang.data.agent.ai.code.docker.DockerSandboxResult;
import com.liang.data.agent.ai.code.model.TaskResponse;
import com.liang.data.agent.common.config.DataAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Docker Python 隔离执行策略
 *
 * <p>以断网、只读根文件系统、非 root 用户和资源限制运行模型生成的 Python 脚本。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerPythonExecutor implements PythonExecutionStrategy {

    private static final long DOCKER_NANO_CPUS = 1_000_000_000L;

    private static final long DOCKER_PIDS_LIMIT = 64L;

    private static final String DOCKER_USER = "65534:65534";

    private final DockerSandboxClient dockerSandboxClient;

    private final DataAgentProperties properties;

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public boolean isAvailable() {
        DataAgentProperties.CodeExecutorProperties config = properties.getCodeExecutor();
        return dockerSandboxClient.isAvailable(config.getDockerImage());
    }

    @Override
    public TaskResponse execute(String code, String inputJson, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        DataAgentProperties.CodeExecutorProperties config = properties.getCodeExecutor();
        DockerSandboxRequest request = new DockerSandboxRequest(
                config.getDockerImage(),
                code,
                inputJson,
                timeoutSeconds,
                parseMemoryLimitMb(),
                DOCKER_NANO_CPUS,
                DOCKER_PIDS_LIMIT,
                DOCKER_USER,
                false,
                true,
                true,
                true
        );

        try {
            DockerSandboxResult result = dockerSandboxClient.execute(request);
            long timeMs = System.currentTimeMillis() - startTime;
            if (result.success()) {
                return TaskResponse.success(result.stdout(), timeMs);
            }
            String message = result.timedOut()
                    ? "Docker Python 沙箱执行超时：" + result.stderr()
                    : result.stderr();
            return TaskResponse.failure(result.stdout(), message, timeMs);
        } catch (Exception e) {
            log.warn("Docker Python 沙箱执行异常：{}", e.getMessage());
            return TaskResponse.failure("", "Docker Python 沙箱执行异常：" + e.getMessage(),
                    System.currentTimeMillis() - startTime);
        }
    }

    private long parseMemoryLimitMb() {
        try {
            return Long.parseLong(properties.getPythonMemoryLimit());
        } catch (NumberFormatException e) {
            log.warn("Python 内存限制配置无效，使用默认值 256 MB：{}", properties.getPythonMemoryLimit());
            return 256L;
        }
    }
}
