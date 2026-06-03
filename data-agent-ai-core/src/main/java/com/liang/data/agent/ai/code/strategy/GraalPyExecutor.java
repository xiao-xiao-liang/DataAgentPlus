package com.liang.data.agent.ai.code.strategy;

import com.liang.data.agent.ai.code.PythonExecutionStrategy;
import com.liang.data.agent.ai.code.model.TaskResponse;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.io.IOAccess;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * 基于 GraalVM Polyglot API 的 JVM 内部嵌入式 Python 安全沙箱
 */
@Slf4j
@Component
public class GraalPyExecutor implements PythonExecutionStrategy {

    @Override
    public int getOrder() {
        return 2; // 优先级居中，当本地命令行 Python 不可用时作为次选
    }

    @Override
    public boolean isAvailable() {
        return ClassUtils.isPresent("org.graalvm.polyglot.Context", getClass().getClassLoader())
                && ClassUtils.isPresent("com.oracle.graal.python.PythonLanguage", getClass().getClassLoader());
    }

    @Override
    public TaskResponse execute(String code, String inputJson, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(inputJson.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

        // 提示：此处关闭宿主访问与 IO 能力，防止生成脚本读取宿主文件或通过 JVM 越权
        try (Context context = Context.newBuilder("python")
                .allowHostAccess(secureHostAccess())
                .allowIO(IOAccess.NONE)
                .allowNativeAccess(false)
                .option("engine.WarnInterpreterOnly", "false")
                .in(inputStream)
                .out(new PrintStream(outputStream, true, StandardCharsets.UTF_8))
                .err(new PrintStream(errorStream, true, StandardCharsets.UTF_8))
                .build()) {

            context.eval("python", code);

            long timeMs = System.currentTimeMillis() - startTime;
            String stdout = outputStream.toString(StandardCharsets.UTF_8).trim();
            String stderr = errorStream.toString(StandardCharsets.UTF_8).trim();

            log.info("GraalVM Python 执行完成，耗时：{} 毫秒", timeMs);

            if (!stderr.isEmpty()) {
                return TaskResponse.failure(stdout, stderr, timeMs);
            }

            return TaskResponse.success(stdout, timeMs);

        } catch (Exception | LinkageError e) {
            String stderr = errorStream.toString(StandardCharsets.UTF_8).trim();
            long timeMs = System.currentTimeMillis() - startTime;
            log.warn("GraalVM Python 执行遇到异常: {}", e.getMessage());

            String errorMsg = stderr.isEmpty() ? e.getMessage() : stderr + "\n" + e.getMessage();
            return TaskResponse.failure("", errorMsg, timeMs);
        }
    }

    static HostAccess secureHostAccess() {
        return HostAccess.NONE;
    }
}
