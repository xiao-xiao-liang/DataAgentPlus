package com.liang.data.agent.ai.code.strategy;

import com.liang.data.agent.ai.code.PythonExecutionStrategy;
import com.liang.data.agent.ai.code.model.TaskResponse;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.springframework.stereotype.Component;

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
        return true; // 嵌入式沙箱环境默认可用
    }

    @Override
    public TaskResponse execute(String code, String inputJson, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(inputJson.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

        // 提示：此处设置 allowIO(IOAccess.NONE) 来确保沙箱的安全，防止生成的脚本对宿主机进行文件读取或越权
        try (Context context = Context.newBuilder("python")
                .allowHostAccess(HostAccess.ALL)
                .allowIO(IOAccess.NONE)
                .allowNativeAccess(false)
                .in(inputStream)
                .out(new PrintStream(outputStream, true, StandardCharsets.UTF_8))
                .err(new PrintStream(errorStream, true, StandardCharsets.UTF_8))
                .build()) {

            // 执行代码。GraalVM 的 Python 引擎将在隔离沙箱里运行脚本并输出 stdout/stderr
            Value evalResult = context.eval("python", code);
            
            long timeMs = System.currentTimeMillis() - startTime;
            String stdout = outputStream.toString(StandardCharsets.UTF_8).trim();
            String stderr = errorStream.toString(StandardCharsets.UTF_8).trim();

            log.info("GraalVM Python 执行完成，耗时：{} 毫秒", timeMs);

            if (!stderr.isEmpty()) {
                // 如果 GraalVM 运行时向 stderr 吐了错误，代表报错
                return TaskResponse.failure(stdout, stderr, timeMs);
            }
            
            return TaskResponse.success(stdout, timeMs);

        } catch (Exception e) {
            String stderr = errorStream.toString(StandardCharsets.UTF_8).trim();
            long timeMs = System.currentTimeMillis() - startTime;
            log.warn("GraalVM Python 执行遇到异常: {}", e.getMessage());
            
            String errorMsg = stderr.isEmpty() ? e.getMessage() : stderr + "\n" + e.getMessage();
            return TaskResponse.failure("", errorMsg, timeMs);
        }
    }
}
