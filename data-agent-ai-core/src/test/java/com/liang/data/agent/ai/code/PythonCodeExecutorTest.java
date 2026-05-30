package com.liang.data.agent.ai.code;

import com.liang.data.agent.ai.code.model.TaskResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonCodeExecutorTest {

    @Test
    void shouldContinueToNextStrategyWhenAvailableStrategyThrowsLinkageError() {
        PythonCodeExecutor executor = new PythonCodeExecutor(List.of(
                new BrokenStrategy(),
                new SuccessfulStrategy()
        ));
        executor.init();

        TaskResponse response = executor.execute("print('ok')", "[]", 5);

        assertTrue(response.isSuccess());
        assertEquals("{\"ok\":true}", response.getStdout());
    }

    private static class BrokenStrategy implements PythonExecutionStrategy {
        @Override
        public int getOrder() {
            return 1;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TaskResponse execute(String code, String inputJson, int timeoutSeconds) {
            throw new NoClassDefFoundError("org/graalvm/polyglot/Context");
        }
    }

    private static class SuccessfulStrategy implements PythonExecutionStrategy {
        @Override
        public int getOrder() {
            return 2;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TaskResponse execute(String code, String inputJson, int timeoutSeconds) {
            return TaskResponse.success("{\"ok\":true}", 1);
        }
    }
}
