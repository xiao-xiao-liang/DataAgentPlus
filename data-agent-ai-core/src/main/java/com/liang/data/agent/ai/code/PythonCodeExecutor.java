package com.liang.data.agent.ai.code;

import com.liang.data.agent.ai.code.model.TaskResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 统一的 Python 代码安全沙箱与降级执行引擎门面类（采用策略模式重构）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PythonCodeExecutor {

    // 动态注入所有实现的 Python 执行策略 Bean
    private final List<PythonExecutionStrategy> executionStrategies;

    // 为了防止 Spring 注入列表顺序不可控，我们在初始化时显式进行优先级排序
    private final List<PythonExecutionStrategy> sortedStrategies = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (executionStrategies != null) {
            sortedStrategies.addAll(executionStrategies);
            // 按照 getOrder() 进行升序排序（数值越小优先级越高）
            sortedStrategies.sort(Comparator.comparingInt(PythonExecutionStrategy::getOrder));
            log.info("成功初始化 Python 执行策略链，已注册策略数: {}", sortedStrategies.size());
            for (PythonExecutionStrategy strategy : sortedStrategies) {
                log.info(" - 策略名称: {}, 优先级: {}, 是否可用: {}",
                        strategy.getClass().getSimpleName(), strategy.getOrder(), strategy.isAvailable());
            }
        }
    }

    /**
     * 多策略责任链模式自动降级执行 Python 脚本
     *
     * @param code           要执行的 Python 代码
     * @param inputJson      要从 sys.stdin 传入的 SQL 数据集 JSON 字符串
     * @param timeoutSeconds 最大超时限制（秒）
     * @return 最终的脚本执行结果
     */
    public TaskResponse execute(String code, String inputJson, int timeoutSeconds) {
        for (PythonExecutionStrategy strategy : sortedStrategies) {
            if (strategy.isAvailable()) {
                log.info("正在通过策略 [{}] 执行 Python 脚本...", strategy.getClass().getSimpleName());
                TaskResponse response = strategy.execute(code, inputJson, timeoutSeconds);
                if (response.isSuccess()) {
                    return response;
                }
                log.warn("策略 [{}] 执行失败，错误信息: {}。正在尝试下一个执行策略...",
                        strategy.getClass().getSimpleName(), response.getStderr());
            }
        }
        return TaskResponse.error("所有已注册的 Python 执行策略均运行失败，无法获取分析结果。");
    }
}
