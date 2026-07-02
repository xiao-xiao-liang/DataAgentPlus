package com.liang.data.agent.ai.code.strategy;

import com.liang.data.agent.ai.code.PythonExecutionStrategy;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.code.model.TaskResponse;
import com.liang.data.agent.gateway.constants.ModelGatewayConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 大模型 AI 模拟数据计算与分析的降级兜底策略
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiSimulatedExecutor implements PythonExecutionStrategy {

    private final LlmService llmService;

    @Override
    public int getOrder() {
        return 3; // 优先级最低，作为最后的兜底策略
    }

    @Override
    public boolean isAvailable() {
        return true; // 只要大模型连接通畅，默认可用
    }

    @Override
    public TaskResponse execute(String code, String inputJson, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        log.info("正在进入 Python 执行的 AI 模拟降级模式...");

        try {
            String systemPrompt = """
                    你扮演一个高效的 Python 解释器。你需要分析给定的 Python 代码和标准输入数据 (sys.stdin)，并模拟该 Python 脚本的运算过程。
                    
                    **请严格遵循以下要求**：
                    1. 仅仅输出该脚本通过 `print(json.dumps(result))` 应该输出的标准输出 JSON 内容本身。
                    2. 严禁输出任何 Markdown 格式包裹（不要带 ```json 或 ```），严禁输出任何解释性自然语言。
                    3. 输出的必须是有效的 JSON 格式。
                    """;

            String userPrompt = String.format("""
                    【Python 脚本代码】
                    %s
                    
                    【sys.stdin 灌入数据】
                    %s
                    """, code, inputJson);

            // 用大模型模拟计算
            String stdout = llmService.toStringFlux(llmService.call(ModelGatewayConstant.AI_SIMULATED_EXECUTION,
                            systemPrompt, userPrompt))
                    .collect(StringBuilder::new, StringBuilder::append)
                    .map(StringBuilder::toString)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            long timeMs = System.currentTimeMillis() - startTime;
            if (stdout == null) {
                return TaskResponse.error("AI 模拟返回空响应。");
            }
            
            String trimmed = stdout.trim();
            log.info("AI 模拟成功完成，耗时：{} 毫秒，输出长度：{}", timeMs, trimmed.length());
            return TaskResponse.success(trimmed, timeMs);

        } catch (Exception e) {
            log.error("AI 模拟 Python 执行失败", e);
            return TaskResponse.failure("", "AI 模拟失败: " + e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }
}
