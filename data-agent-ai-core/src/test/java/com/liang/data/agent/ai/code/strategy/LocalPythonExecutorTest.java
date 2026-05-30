package com.liang.data.agent.ai.code.strategy;

import com.liang.data.agent.ai.code.model.TaskResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalPythonExecutorTest {

    @Test
    void shouldSerializeObjectsWithItemMethodAsJsonScalars() {
        LocalPythonExecutor executor = new LocalPythonExecutor();
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
}
