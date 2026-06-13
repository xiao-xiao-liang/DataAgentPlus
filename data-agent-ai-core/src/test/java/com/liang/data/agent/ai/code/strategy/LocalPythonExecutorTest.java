package com.liang.data.agent.ai.code.strategy;

import com.liang.data.agent.ai.code.model.TaskResponse;
import com.liang.data.agent.common.config.DataAgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalPythonExecutorTest {

    @Test
    void shouldSerializeObjectsWithItemMethodAsJsonScalars() {
        LocalPythonExecutor executor = new LocalPythonExecutor(new DataAgentProperties(), new MockEnvironment());
        assumeTrue(executor.isAvailable(), "local Python executable is not available");

        TaskResponse response = executor.execute("""
                import json

                class IntLike:
                    def item(self):
                        return 7

                result = {"value": IntLike()}
                print(json.dumps(result, ensure_ascii=False))
                """, "[]", 5);

        assertTrue(response.isSuccess(), response.getStderr());
        assertEquals("{\"value\": 7}", response.getStdout());
    }

    @Test
    void shouldDisableLocalExecutorInProductionByDefault() {
        DataAgentProperties properties = new DataAgentProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        LocalPythonExecutor executor = new LocalPythonExecutor(properties, environment);

        assertTrue(!executor.isAvailable());
    }

    @Test
    void shouldAllowLocalExecutorInProductionWhenExplicitlyEnabled() {
        DataAgentProperties properties = new DataAgentProperties();
        properties.getCodeExecutor().setAllowLocalFallbackInProduction(true);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        LocalPythonExecutor executor = new LocalPythonExecutor(properties, environment);

        assumeTrue(executor.hasLocalPython(), "local Python executable is not available");
        assertTrue(executor.isAvailable());
    }

}
