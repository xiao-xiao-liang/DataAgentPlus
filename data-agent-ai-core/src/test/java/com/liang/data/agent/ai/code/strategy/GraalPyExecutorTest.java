package com.liang.data.agent.ai.code.strategy;

import com.liang.data.agent.ai.code.model.TaskResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GraalPyExecutorTest {

    @Test
    void shouldExecuteSimplePythonWhenGraalPyRuntimeIsAvailable() {
        GraalPyExecutor executor = new GraalPyExecutor();
        assumeTrue(executor.isAvailable(), "GraalPy runtime is not available");

        TaskResponse response = executor.execute("print('{\"ok\": true}')", "[]", 5);

        assertTrue(response.isSuccess(), response.getStderr());
        assertEquals("{\"ok\": true}", response.getStdout());
    }
}
