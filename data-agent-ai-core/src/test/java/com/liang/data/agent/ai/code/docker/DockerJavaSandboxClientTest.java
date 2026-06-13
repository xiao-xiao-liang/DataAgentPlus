package com.liang.data.agent.ai.code.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.PingCmd;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.LogConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Docker Java 沙箱客户端安全配置测试
 */
class DockerJavaSandboxClientTest {

    @Test
    void shouldCloseAvailabilityCheckCommands() throws Exception {
        DockerClient dockerClient = mock(DockerClient.class);
        PingCmd pingCmd = mock(PingCmd.class);
        InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
        when(dockerClient.pingCmd()).thenReturn(pingCmd);
        when(dockerClient.inspectImageCmd("sandbox:test")).thenReturn(inspectImageCmd);
        DockerJavaSandboxClient client = new DockerJavaSandboxClient(dockerClient);

        assertThat(client.isAvailable("sandbox:test")).isTrue();

        verify(pingCmd).close();
        verify(inspectImageCmd).close();
    }

    @Test
    void shouldBuildRestrictedHostConfig() {
        DockerClient dockerClient = mock(DockerClient.class);
        DockerJavaSandboxClient client = new DockerJavaSandboxClient(dockerClient);
        DockerSandboxRequest request = new DockerSandboxRequest(
                "sandbox:test", "print('ok')", "[]", 30,
                256, 1_000_000_000L, 64, "65534:65534",
                false, true, true, true
        );

        HostConfig hostConfig = client.buildHostConfig(request, Path.of("sandbox-input").toAbsolutePath());

        assertThat(hostConfig.getNetworkMode()).isEqualTo("none");
        assertThat(hostConfig.getReadonlyRootfs()).isTrue();
        assertThat(hostConfig.getMemory()).isEqualTo(256L * 1024L * 1024L);
        assertThat(hostConfig.getMemorySwap()).isEqualTo(256L * 1024L * 1024L);
        assertThat(hostConfig.getNanoCPUs()).isEqualTo(1_000_000_000L);
        assertThat(hostConfig.getPidsLimit()).isEqualTo(64L);
        assertThat(hostConfig.getCapDrop()).containsExactly(Capability.ALL);
        assertThat(hostConfig.getSecurityOpts()).containsExactly("no-new-privileges:true");
        assertThat(hostConfig.getBinds()).singleElement()
                .satisfies(bind -> assertThat(bind.getAccessMode()).isEqualTo(AccessMode.ro));
        assertThat(hostConfig.getTmpFs()).containsEntry("/tmp", "rw,noexec,nosuid,size=64m");
        assertThat(hostConfig.getLogConfig().getType()).isEqualTo(LogConfig.LoggingType.JSON_FILE);
        assertThat(hostConfig.getLogConfig().getConfig())
                .containsEntry("max-size", "1m")
                .containsEntry("max-file", "1");
    }
}
