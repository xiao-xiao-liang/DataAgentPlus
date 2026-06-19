package com.liang.data.agent.workflow.node;

import static com.liang.data.agent.workflow.constants.PythonExecutionConstants.MAX_TRIES;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.ai.code.PythonCodeExecutor;
import com.liang.data.agent.ai.code.model.TaskResponse;
import com.liang.data.agent.common.config.DataAgentProperties;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.enums.TextType;
import com.liang.data.agent.common.ratelimit.ResourceType;
import com.liang.data.agent.service.ratelimit.ResourceGate;
import com.liang.data.agent.service.ratelimit.ResourcePermit;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.PYTHON_IS_SUCCESS;
import static com.liang.data.agent.common.constant.ControlFlowKey.PYTHON_TRIES_COUNT;
import static com.liang.data.agent.common.constant.ControlFlowKey.PYTHON_FALLBACK_MODE;
import static com.liang.data.agent.common.constant.NodeOutputKey.PYTHON_EXECUTE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.PYTHON_GENERATE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.SQL_RESULT_LIST_MEMORY;

/**
 * Python 执行节点
 *
 * <p>通过统一的 PythonCodeExecutor 门面，按 Docker、GraalPy、本地 Python 的责任链执行大模型生成的数据分析脚本。
 * 自动捕获执行的 stdout/stderr 写入流，并在运行失败时递增重试次数以触发自动大模型重试纠错，
 * 达上限后启动优雅降级分析，并内置了双重 Unicode/十六进制转义解码以防御 Windows 下的进程输出乱码。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonExecuteNode implements NodeAction {

    private final PythonCodeExecutor pythonCodeExecutor;
    private final ResourceGate resourceGate;
    private final DataAgentProperties properties;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 获取 AI 生成的 Python 脚本和当前的尝试次数
        String pythonCode = StateUtil.getStringValue(state, PYTHON_GENERATE_NODE_OUTPUT);
        int triesCount = StateUtil.getObjectValue(state, PYTHON_TRIES_COUNT, Integer.class, 0);

        // 2. 从状态提取上游 SQL 执行成功后缓存的所有数据集结果，序列化为 JSON 作为 stdin 输入
        boolean hasSqlResult = StateUtil.hasValue(state, SQL_RESULT_LIST_MEMORY);
        List<Map<String, String>> sqlResults = hasSqlResult 
                ? StateUtil.getListValue(state, SQL_RESULT_LIST_MEMORY) 
                : new ArrayList<>();
        
        String sqlResultJson = OBJECT_MAPPER.writeValueAsString(sqlResults);

        log.info("启动 Python 代码执行沙箱 - 脚本长度: {} 字符, 输入记录: {} 条, 当前尝试次数: {}", 
                pythonCode.length(), sqlResults.size(), triesCount);

        // 3. 申请 Python 执行资源，资源不足时不进入沙箱
        ResourcePermit permit = resourceGate.tryAcquire(ResourceType.PYTHON_EXECUTION, "python-execute", Duration.ZERO);
        if (!permit.acquired()) {
            return buildResourceBusyResponse(state);
        }

        Flux<ChatResponse> displayFlux;
        Map<String, Object> stateUpdate = new HashMap<>();

        try (permit) {
            // 4. 使用统一配置的超时时间调用多策略执行引擎。
            int timeoutSeconds = properties.getExecutionTimeout().getPythonSeconds();
            TaskResponse response = pythonCodeExecutor.execute(pythonCode, sqlResultJson, timeoutSeconds);

            if (response.isSuccess()) {
                // 执行成功：还原 stdout 并处理乱码，推进流程
                String cleanStdout = decodeUnicode(response.getStdout());
                log.info("Python 脚本执行成功，执行耗时: {} 毫秒", response.getExecutionTimeMs());

                displayFlux = Flux.just(
                        ChatResponseUtil.createResponse("开始执行 Python 数据分析代码..."),
                        ChatResponseUtil.createResponse("分析引擎标准输出："),
                        ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()),
                        ChatResponseUtil.createResponse(cleanStdout),
                        ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
                        ChatResponseUtil.createResponse("Python 代码数据处理执行成功！")
                );

                stateUpdate.put(PYTHON_EXECUTE_NODE_OUTPUT, cleanStdout);
                stateUpdate.put(PYTHON_IS_SUCCESS, true);
            } else {
                // 执行失败：防乱码转换 stderr，标记为失败以触发重试纠错路由
                String cleanStderr = decodeUnicode(response.getStderr());
                int nextTries = triesCount + 1;
                log.warn("Python 脚本执行失败。尝试次数: {}/{}。报错详情: {}", nextTries, MAX_TRIES, cleanStderr);

                stateUpdate.put(PYTHON_EXECUTE_NODE_OUTPUT, cleanStderr);
                stateUpdate.put(PYTHON_IS_SUCCESS, false);
                stateUpdate.put(PYTHON_TRIES_COUNT, nextTries);

                if (nextTries >= MAX_TRIES) {
                    // 已经达到了最大重试限制，为了保证工作流不戛然而止而导致用户无法获取最终报告，我们直接标记为进入降级模式
                    log.error("已达到 Python 脚本执行重试限制 ({} 次)，触发优雅降级流程，流转向分析汇总", MAX_TRIES);
                    stateUpdate.put(PYTHON_FALLBACK_MODE, true);

                    displayFlux = Flux.just(
                            ChatResponseUtil.createResponse("开始执行 Python 数据分析代码..."),
                            ChatResponseUtil.createResponse("⚠️ 分析引擎执行报错或异常输出："),
                            ChatResponseUtil.createResponse(cleanStderr),
                            ChatResponseUtil.createResponse("[系统] 已达到大模型重试纠错上限，正转向启动工作流优雅降级机制...")
                    );
                } else {
                    displayFlux = Flux.just(
                            ChatResponseUtil.createResponse("开始执行 Python 数据分析代码..."),
                            ChatResponseUtil.createResponse("⚠️ 分析引擎执行报错或异常输出："),
                            ChatResponseUtil.createResponse(cleanStderr),
                            ChatResponseUtil.createResponse(String.format("Python 执行未成功。当前尝试第 %d 次，正将错误回传给大模型触发自愈纠错机制...", nextTries))
                    );
                }
            }
        }

        // 5. 将输出封装为流式响应，推送给前端展示
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(), state,
                v -> stateUpdate,
                displayFlux
        );

        return Map.of(PYTHON_EXECUTE_NODE_OUTPUT, generator);
    }

    private Map<String, Object> buildResourceBusyResponse(OverAllState state) {
        Map<String, Object> stateUpdate = new HashMap<>();
        stateUpdate.put(PYTHON_EXECUTE_NODE_OUTPUT, "Python 执行资源繁忙，请稍后重试");
        stateUpdate.put(PYTHON_IS_SUCCESS, false);

        Flux<ChatResponse> displayFlux = Flux.just(
                ChatResponseUtil.createResponse("Python 执行资源繁忙，请稍后重试")
        );
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(), state,
                v -> stateUpdate,
                displayFlux
        );
        return Map.of(PYTHON_EXECUTE_NODE_OUTPUT, generator);
    }

    /**
     * 双重转义字符中文还原器
     *
     * <p>还原形如 \\uXXXX 的 Unicode 转义和形如 \\xXX 的 Python 字节集转义字符，
     * 用于解决 Windows 等不同编码控制台下命令行子进程标准流读入产生的中文乱码缺陷。</p>
     */
    private String decodeUnicode(String str) {
        if (str == null) {
            return "";
        }
        
        // Step 1: 还原 \\uXXXX 转义
        StringBuilder sb = new StringBuilder();
        int len = str.length();
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c == '\\' && i + 1 < len && str.charAt(i + 1) == 'u') {
                if (i + 5 < len) {
                    try {
                        int code = Integer.parseInt(str.substring(i + 2, i + 6), 16);
                        sb.append((char) code);
                        i += 5;
                        continue;
                    } catch (NumberFormatException ignored) {}
                }
            }
            sb.append(c);
        }

        // Step 2: 还原 Python print bytes 的 \xXX 转义（如 \xe4\xb8\xad）
        String s = sb.toString();
        if (s.contains("\\x")) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (c == '\\' && i + 1 < s.length() && s.charAt(i + 1) == 'x') {
                        if (i + 3 < s.length()) {
                            try {
                                int b = Integer.parseInt(s.substring(i + 2, i + 4), 16);
                                out.write((byte) b);
                                i += 3;
                                continue;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    out.write(c);
                }
                return out.toString(StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }

        return s;
    }
}
