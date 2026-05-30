package com.liang.data.agent.ai.code;

import com.liang.data.agent.ai.code.model.TaskResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PythonCodeExecutor {

    private final List<PythonExecutionStrategy> executionStrategies;

    private final List<PythonExecutionStrategy> sortedStrategies = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (executionStrategies != null) {
            sortedStrategies.addAll(executionStrategies);
            sortedStrategies.sort(Comparator.comparingInt(PythonExecutionStrategy::getOrder));
            log.info("Initialized Python execution strategies, count: {}", sortedStrategies.size());
            for (PythonExecutionStrategy strategy : sortedStrategies) {
                log.info(" - strategy: {}, order: {}, available: {}",
                        strategy.getClass().getSimpleName(), strategy.getOrder(), isStrategyAvailable(strategy));
            }
        }
    }

    public TaskResponse execute(String code, String inputJson, int timeoutSeconds) {
        List<String> failureMessages = new ArrayList<>();

        for (PythonExecutionStrategy strategy : sortedStrategies) {
            String strategyName = strategy.getClass().getSimpleName();
            if (!isStrategyAvailable(strategy)) {
                continue;
            }

            log.info("Executing Python script with strategy [{}]...", strategyName);

            TaskResponse response;
            try {
                response = strategy.execute(code, inputJson, timeoutSeconds);
            } catch (Exception | LinkageError e) {
                String message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
                failureMessages.add(strategyName + ": " + message);
                log.warn("Python strategy [{}] crashed, trying next strategy: {}", strategyName, message);
                log.debug("Python strategy [{}] crash details", strategyName, e);
                continue;
            }

            if (response.isSuccess()) {
                return response;
            }

            failureMessages.add(strategyName + ": " + response.getStderr());
            log.warn("Python strategy [{}] failed: {}. Trying next strategy...",
                    strategyName, response.getStderr());
        }

        String detail = failureMessages.isEmpty() ? "" : " Details: " + String.join("; ", failureMessages);
        return TaskResponse.error("All registered Python execution strategies failed." + detail);
    }

    private boolean isStrategyAvailable(PythonExecutionStrategy strategy) {
        try {
            return strategy.isAvailable();
        } catch (Exception | LinkageError e) {
            log.warn("Python strategy [{}] availability check failed, skipping it: {}",
                    strategy.getClass().getSimpleName(), e.getMessage());
            log.debug("Python strategy [{}] availability check details", strategy.getClass().getSimpleName(), e);
            return false;
        }
    }
}
