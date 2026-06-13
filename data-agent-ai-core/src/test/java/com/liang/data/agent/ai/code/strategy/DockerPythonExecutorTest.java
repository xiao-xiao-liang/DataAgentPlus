package com.liang.data.agent.ai.code.strategy;

import com.liang.data.agent.ai.code.docker.DockerSandboxClient;
import com.liang.data.agent.ai.code.docker.DockerSandboxRequest;
import com.liang.data.agent.ai.code.docker.DockerSandboxResult;
import com.liang.data.agent.ai.code.model.TaskResponse;
import com.liang.data.agent.common.config.DataAgentProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerPythonExecutorTest {

    @Test
    void shouldBuildRestrictedSandboxRequest() {
        DataAgentProperties properties = new DataAgentProperties();
        properties.getCodeExecutor().setDockerImage("sandbox:test");
        properties.setPythonMemoryLimit("384");
        AtomicReference<DockerSandboxRequest> captured = new AtomicReference<>();
        DockerSandboxClient client = new StubDockerSandboxClient(true,
                request -> {
                    captured.set(request);
                    return new DockerSandboxResult(true, "{\"ok\":true}", "", 0, false);
                });

        DockerPythonExecutor executor = new DockerPythonExecutor(client, properties);
        TaskResponse response = executor.execute("print('ok')", "[]", 7);

        assertTrue(response.isSuccess());
        DockerSandboxRequest request = captured.get();
        assertEquals("sandbox:test", request.image());
        assertEquals(384L, request.memoryMb());
        assertEquals(1_000_000_000L, request.nanoCpus());
        assertEquals(64L, request.pidsLimit());
        assertEquals("65534:65534", request.user());
        assertEquals(7, request.timeoutSeconds());
        assertFalse(request.networkEnabled());
        assertTrue(request.readonlyRootFilesystem());
        assertTrue(request.dropAllCapabilities());
        assertTrue(request.noNewPrivileges());
    }

    @Test
    void shouldConvertTimeoutToFailure() {
        DataAgentProperties properties = new DataAgentProperties();
        DockerSandboxClient client = new StubDockerSandboxClient(true,
                request -> new DockerSandboxResult(false, "", "执行超时", 137, true));

        TaskResponse response = new DockerPythonExecutor(client, properties)
                .execute("print('ok')", "[]", 3);

        assertFalse(response.isSuccess());
        assertTrue(response.getStderr().contains("执行超时"));
    }

    private record StubDockerSandboxClient(
            boolean available,
            Runner runner
    ) implements DockerSandboxClient {

        @Override
        public boolean isAvailable(String image) {
            return available;
        }

        @Override
        public DockerSandboxResult execute(DockerSandboxRequest request) {
            return runner.execute(request);
        }
    }

    @FunctionalInterface
    private interface Runner {
        DockerSandboxResult execute(DockerSandboxRequest request);
    }
}
