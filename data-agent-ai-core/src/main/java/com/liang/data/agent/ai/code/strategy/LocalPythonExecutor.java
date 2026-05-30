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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@Component
public class LocalPythonExecutor implements PythonExecutionStrategy {

    private static final String[] PYTHON_NAMES = {"python3", "python", "py"};

    private final String pythonExecutable;

    public LocalPythonExecutor() {
        this.pythonExecutable = checkProgramExists();
        if (this.pythonExecutable != null) {
            log.info("Detected local Python executable: {}", this.pythonExecutable);
        } else {
            log.warn("No local Python executable found in PATH. LocalPythonExecutor is unavailable.");
        }
    }

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public boolean isAvailable() {
        return this.pythonExecutable != null;
    }

    @Override
    public TaskResponse execute(String code, String inputJson, int timeoutSeconds) {
        if (!isAvailable()) {
            return TaskResponse.error("No local Python executable found.");
        }

        long startTime = System.currentTimeMillis();
        Path tempDir = null;
        Process process = null;

        try {
            tempDir = Files.createTempDirectory("data-agent-py-run-");
            Path scriptFile = tempDir.resolve("script.py");
            Path stdinFile = tempDir.resolve("stdin.json");

            Files.writeString(scriptFile, withJsonSerializationGuard(code), StandardCharsets.UTF_8);
            Files.writeString(stdinFile, inputJson, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(pythonExecutable, scriptFile.toAbsolutePath().toString());
            pb.directory(tempDir.toFile());
            pb.redirectInput(stdinFile.toFile());

            process = pb.start();

            StringWriter stdoutWriter = new StringWriter();
            StringWriter stderrWriter = new StringWriter();

            try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {

                CompletableFuture<Void> stdoutFuture = CompletableFuture.runAsync(() -> transferTo(stdoutReader, stdoutWriter, "stdout"));
                CompletableFuture<Void> stderrFuture = CompletableFuture.runAsync(() -> transferTo(stderrReader, stderrWriter, "stderr"));

                boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!completed) {
                    process.destroy();
                    if (process.isAlive()) {
                        process.destroyForcibly();
                    }
                    return TaskResponse.failure("", "Python execution timed out (" + timeoutSeconds + "s).",
                            System.currentTimeMillis() - startTime);
                }

                CompletableFuture.allOf(stdoutFuture, stderrFuture).get(1, TimeUnit.SECONDS);
            }

            int exitCode = process.exitValue();
            String stdout = stdoutWriter.toString().trim();
            String stderr = stderrWriter.toString().trim();
            long timeMs = System.currentTimeMillis() - startTime;

            log.info("Local Python execution completed, exitCode: {}, time: {} ms", exitCode, timeMs);

            if (exitCode != 0) {
                String fallbackError = "Process exited with non-zero code: " + exitCode;
                return TaskResponse.failure(stdout, StringUtils.hasText(stderr) ? stderr : fallbackError, timeMs);
            }
            return TaskResponse.success(stdout, timeMs);

        } catch (Exception e) {
            log.error("Failed to execute local Python process", e);
            return TaskResponse.failure("", "Python execution exception: " + e.getMessage(),
                    System.currentTimeMillis() - startTime);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            clearTempDir(tempDir);
        }
    }

    private void transferTo(BufferedReader reader, StringWriter writer, String streamName) {
        try {
            reader.transferTo(writer);
        } catch (Exception e) {
            log.error("Error reading {}: {}", streamName, e.getMessage());
        }
    }

    private String withJsonSerializationGuard(String code) {
        return """
                import json as __data_agent_json

                __data_agent_original_json_dumps = __data_agent_json.dumps

                def __data_agent_json_default(obj):
                    try:
                        import numpy as __data_agent_np
                        if isinstance(obj, __data_agent_np.integer):
                            return int(obj)
                        if isinstance(obj, __data_agent_np.floating):
                            return float(obj)
                        if isinstance(obj, __data_agent_np.bool_):
                            return bool(obj)
                        if isinstance(obj, __data_agent_np.ndarray):
                            return obj.tolist()
                    except Exception:
                        pass
                    try:
                        import pandas as __data_agent_pd
                        if obj is __data_agent_pd.NA:
                            return None
                        if isinstance(obj, (__data_agent_pd.Timestamp, __data_agent_pd.Timedelta)):
                            return str(obj)
                    except Exception:
                        pass
                    if hasattr(obj, "item"):
                        try:
                            return obj.item()
                        except Exception:
                            pass
                    if hasattr(obj, "tolist"):
                        try:
                            return obj.tolist()
                        except Exception:
                            pass
                    return str(obj)

                def __data_agent_safe_json_dumps(obj, *args, **kwargs):
                    kwargs.setdefault("default", __data_agent_json_default)
                    return __data_agent_original_json_dumps(obj, *args, **kwargs)

                __data_agent_json.dumps = __data_agent_safe_json_dumps

                """ + code;
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
            log.debug("Cleaned temp execution directory: {}", path);
        } catch (Exception e) {
            log.warn("Failed to clean temp execution directory: {}", path, e);
        }
    }
}
