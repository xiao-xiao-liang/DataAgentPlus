package com.liang.data.agent.ai.code.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.LogConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 基于 Docker Java 的 Python 沙箱客户端
 *
 * <p>负责创建受限容器、等待执行完成、收集日志，并在所有退出路径中删除容器与临时文件。</p>
 */
@Slf4j
@Component
@NoArgsConstructor
public class DockerJavaSandboxClient implements DockerSandboxClient {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private volatile DockerClient dockerClient;

    DockerJavaSandboxClient(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    @Override
    public boolean isAvailable(String image) {
        try {
            // 1. 验证 Docker 服务连通性
            try (var pingCmd = getDockerClient().pingCmd()) {
                pingCmd.exec();
            }
            // 2. 验证沙箱镜像已在本地准备完成
            try (var inspectImageCmd = getDockerClient().inspectImageCmd(image)) {
                inspectImageCmd.exec();
            }
            return true;
        } catch (Exception | LinkageError e) {
            log.debug("Docker Python 沙箱不可用，镜像：{}，原因：{}", image, e.getMessage());
            return false;
        }
    }

    @Override
    public DockerSandboxResult execute(DockerSandboxRequest request) {
        long startTime = System.currentTimeMillis();
        Path tempDir = null;
        String containerId = null;
        try {
            // 1. 将脚本和输入写入单次执行临时目录
            tempDir = createReadonlyInputFiles(request);

            // 2. 创建并启动受限容器
            HostConfig hostConfig = buildHostConfig(request, tempDir);
            CreateContainerResponse container;
            try (var createContainerCmd = getDockerClient().createContainerCmd(request.image())) {
                container = createContainerCmd
                        .withHostConfig(hostConfig)
                        .withNetworkDisabled(!request.networkEnabled())
                        .withUser(request.user())
                        .withWorkingDir("/sandbox")
                        .withEnv("PYTHONDONTWRITEBYTECODE=1", "PYTHONUNBUFFERED=1", "TMPDIR=/tmp")
                        .withCmd("sh", "-c", "python /sandbox/script.py < /sandbox/input.json")
                        .exec();
            }
            containerId = container.getId();
            try (var startContainerCmd = getDockerClient().startContainerCmd(containerId)) {
                startContainerCmd.exec();
            }

            // 3. 等待执行完成，超时后强制终止容器
            Integer exitCode;
            try (var waitContainerCmd = getDockerClient().waitContainerCmd(containerId);
                 WaitContainerResultCallback waitCallback = new WaitContainerResultCallback()) {
                exitCode = waitContainerCmd.exec(waitCallback)
                        .awaitStatusCode(request.timeoutSeconds(), TimeUnit.SECONDS);
            }
            if (exitCode == null) {
                killContainer(containerId);
                return new DockerSandboxResult(false, "", "容器执行超过最大允许时间", 137, true);
            }

            // 4. 收集标准输出和标准错误
            ContainerLogs logs = collectLogs(containerId);
            boolean success = exitCode == 0;
            String stderr = success || !logs.stderr().isBlank() ? logs.stderr() : "容器以非零状态退出，退出码：" + exitCode;
            log.info("Docker Python 沙箱执行完成，退出码：{}，耗时：{} 毫秒", exitCode, System.currentTimeMillis() - startTime);
            return new DockerSandboxResult(success, logs.stdout().trim(), stderr.trim(), exitCode, false);
        } catch (Exception e) {
            log.warn("Docker Python 沙箱执行失败：{}", e.getMessage());
            return new DockerSandboxResult(false, "", "Docker 沙箱异常：" + e.getMessage(), -1, false);
        } finally {
            removeContainer(containerId);
            clearTempDir(tempDir);
        }
    }

    private DockerClient createDockerClient() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(3))
                .responseTimeout(Duration.ofSeconds(30))
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

    private DockerClient getDockerClient() {
        DockerClient current = dockerClient;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (dockerClient == null) {
                dockerClient = createDockerClient();
            }
            return dockerClient;
        }
    }

    private Path createReadonlyInputFiles(DockerSandboxRequest request) throws IOException {
        Path tempDir = Files.createTempDirectory("data-agent-docker-py-");
        Path scriptFile = Files.writeString(tempDir.resolve("script.py"), request.code(), StandardCharsets.UTF_8);
        Path inputFile = Files.writeString(tempDir.resolve("input.json"), request.inputJson(), StandardCharsets.UTF_8);
        makeReadableBySandboxUser(tempDir, scriptFile, inputFile);
        return tempDir;
    }

    private void makeReadableBySandboxUser(Path tempDir, Path... files) {
        try {
            Files.setPosixFilePermissions(tempDir, PosixFilePermissions.fromString("r-xr-xr-x"));
            for (Path file : files) {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));
            }
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("当前文件系统不支持 POSIX 权限设置，将由 Docker 挂载层处理只读权限：{}", e.getMessage());
        }
    }

    HostConfig buildHostConfig(DockerSandboxRequest request, Path tempDir) {
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(new Bind(tempDir.toAbsolutePath().toString(), new Volume("/sandbox"), AccessMode.ro))
                .withMemory(request.memoryMb() * BYTES_PER_MB)
                .withMemorySwap(request.memoryMb() * BYTES_PER_MB)
                .withNanoCPUs(request.nanoCpus())
                .withPidsLimit(request.pidsLimit())
                .withNetworkMode(request.networkEnabled() ? "default" : "none")
                .withReadonlyRootfs(request.readonlyRootFilesystem())
                .withTmpFs(Map.of("/tmp", "rw,noexec,nosuid,size=64m"))
                .withLogConfig(new LogConfig(LogConfig.LoggingType.JSON_FILE,
                        Map.of("max-size", "1m", "max-file", "1")))
                .withAutoRemove(false);
        if (request.dropAllCapabilities()) {
            hostConfig.withCapDrop(Capability.ALL);
        }
        if (request.noNewPrivileges()) {
            hostConfig.withSecurityOpts(List.of("no-new-privileges:true"));
        }
        return hostConfig;
    }

    private ContainerLogs collectLogs(String containerId) throws InterruptedException, IOException {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        try (var logContainerCmd = getDockerClient().logContainerCmd(containerId);
             ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
                 @Override
                 public void onNext(Frame frame) {
                     String content = new String(frame.getPayload(), StandardCharsets.UTF_8);
                     if (frame.getStreamType() == StreamType.STDERR) {
                         stderr.append(content);
                     } else {
                         stdout.append(content);
                     }
                 }
             }) {
            logContainerCmd.withStdOut(true)
                    .withStdErr(true)
                    .withTailAll()
                    .exec(callback)
                    .awaitCompletion(5, TimeUnit.SECONDS);
        }
        return new ContainerLogs(stdout.toString(), stderr.toString());
    }

    private void killContainer(String containerId) {
        try (var killContainerCmd = getDockerClient().killContainerCmd(containerId)) {
            killContainerCmd.exec();
        } catch (Exception e) {
            log.debug("强制终止 Docker 沙箱容器失败，容器：{}，原因：{}", containerId, e.getMessage());
        }
    }

    private void removeContainer(String containerId) {
        if (containerId == null) {
            return;
        }
        try (var removeContainerCmd = getDockerClient().removeContainerCmd(containerId)) {
            removeContainerCmd.withForce(true).exec();
        } catch (Exception e) {
            log.warn("删除 Docker 沙箱容器失败，容器：{}，原因：{}", containerId, e.getMessage());
        }
    }

    private void clearTempDir(Path tempDir) {
        if (tempDir == null || !Files.exists(tempDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deletePath);
        } catch (Exception e) {
            log.warn("清理 Docker 沙箱临时目录失败，目录：{}，原因：{}", tempDir, e.getMessage());
        }
    }

    private void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除 Docker 沙箱临时文件失败，文件：{}，原因：{}", path, e.getMessage());
        }
    }

    private record ContainerLogs(String stdout, String stderr) {
    }
}
