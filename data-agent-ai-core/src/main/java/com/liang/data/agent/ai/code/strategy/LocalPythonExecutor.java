package com.liang.data.agent.ai.code.strategy;

import com.liang.data.agent.ai.code.PythonExecutionStrategy;
import com.liang.data.agent.ai.code.model.TaskResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 本地命令行 Python 进程执行策略
 */
@Slf4j
@Component
public class LocalPythonExecutor implements PythonExecutionStrategy {

    private static final String[] PYTHON_NAMES = {"python3", "python", "py"};
    private final String pythonExecutable;

    public LocalPythonExecutor() {
        this.pythonExecutable = checkProgramExists();
        if (this.pythonExecutable != null) {
            log.info("成功检测到 Python 可执行文件路径: {}", this.pythonExecutable);
        } else {
            log.warn("在系统 PATH 中未找到 Python 可执行文件。LocalPythonExecutor（本地 Python 执行器）将不可用。");
        }
    }

    @Override
    public int getOrder() {
        return 1; // 优先级最高，首选本地解释器执行
    }

    @Override
    public boolean isAvailable() {
        return this.pythonExecutable != null;
    }

    @Override
    public TaskResponse execute(String code, String inputJson, int timeoutSeconds) {
        if (!isAvailable()) {
            return TaskResponse.error("系统环境中未找到本地 Python 可执行文件。");
        }

        long startTime = System.currentTimeMillis();
        Path tempDir = null;
        Process process = null;

        try {
            // 1. 创建独立临时的隔离运行环境
            tempDir = Files.createTempDirectory("data-agent-py-run-");
            Path scriptFile = tempDir.resolve("script.py");
            Path stdinFile = tempDir.resolve("stdin.json");

            // 2. 写入大模型生成的 Python 代码和标准输入数据集
            Files.write(scriptFile, code.getBytes());
            Files.write(stdinFile, inputJson.getBytes());

            // 3. 构建进程并在临时隔离目录下启动
            ProcessBuilder pb = new ProcessBuilder(pythonExecutable, scriptFile.toAbsolutePath().toString());
            pb.directory(tempDir.toFile());
            pb.redirectInput(stdinFile.toFile());

            process = pb.start();

            // 4. 异步并发收集 stdout 与 stderr，防止管道阻塞
            StringWriter stdoutWriter = new StringWriter();
            StringWriter stderrWriter = new StringWriter();

            try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                CompletableFuture<Void> stdoutFuture = CompletableFuture.runAsync(() -> {
                    try {
                        stdoutReader.transferTo(stdoutWriter);
                    } catch (Exception e) {
                        log.error("Error reading stdout: {}", e.getMessage());
                    }
                });

                CompletableFuture<Void> stderrFuture = CompletableFuture.runAsync(() -> {
                    try {
                        stderrReader.transferTo(stderrWriter);
                    } catch (Exception e) {
                        log.error("Error reading stderr: {}", e.getMessage());
                    }
                });

                // 5. 挂起控制：带超时防护的进程挂起等待
                boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!completed) {
                    process.destroy();
                    if (process.isAlive()) {
                        process.destroyForcibly();
                    }
                    return TaskResponse.failure("", "Python 执行超时 (" + timeoutSeconds + "s)。进程已被终止。", System.currentTimeMillis() - startTime);
                }

                // 额外给予 1 秒收集剩余缓冲区的流
                CompletableFuture.allOf(stdoutFuture, stderrFuture).get(1, TimeUnit.SECONDS);
            }

            int exitCode = process.exitValue();
            String stdout = stdoutWriter.toString().trim();
            String stderr = stderrWriter.toString().trim();
            long timeMs = System.currentTimeMillis() - startTime;

            log.info("本地 Python 执行完成，退出码：{}，耗时：{} 毫秒", exitCode, timeMs);

            if (exitCode != 0) {
                // 如果运行报错，将 stderr 内容返回供模型自愈
                return TaskResponse.failure(stdout, StringUtils.hasText(stderr) ? stderr : "进程异常退出，错误码: " + exitCode, timeMs);
            } else {
                return TaskResponse.success(stdout, timeMs);
            }

        } catch (Exception e) {
            log.error("运行本地 Python 进程失败", e);
            return TaskResponse.failure("", "Python 执行异常: " + e.getMessage(), System.currentTimeMillis() - startTime);
        } finally {
            // 6. 安全清理：物理级联清除生成的临时文件
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            clearTempDir(tempDir);
        }
    }

    private String checkProgramExists() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }

        String[] pathDirs = pathEnv.split(File.pathSeparator);
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        for (String program : PYTHON_NAMES) {
            for (String dir : pathDirs) {
                if (dir == null || dir.trim().isEmpty()) {
                    continue;
                }
                Path path = Paths.get(dir, program);
                if (Files.exists(path) && Files.isExecutable(path)) {
                    return program;
                }
                if (isWindows) {
                    Path exePath = Paths.get(dir, program + ".exe");
                    if (Files.exists(exePath) && Files.isExecutable(exePath)) {
                        return program;
                    }
                }
            }
        }
        return null;
    }

    private void clearTempDir(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            log.debug("已清理临时执行目录: {}", path);
        } catch (Exception e) {
            log.warn("清理临时执行目录失败: {}", path, e);
        }
    }
}
