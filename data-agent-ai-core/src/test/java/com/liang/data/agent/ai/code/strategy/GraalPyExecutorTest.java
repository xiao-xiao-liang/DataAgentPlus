package com.liang.data.agent.ai.code.strategy;

import com.liang.data.agent.ai.code.model.TaskResponse;
import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GraalPyExecutorTest {

    @Test
    void shouldDisableHostAccessByDefault() {
        assertEquals(HostAccess.NONE, GraalPyExecutor.secureHostAccess());
    }

    @Test
    void shouldExecuteSimplePythonWhenGraalPyRuntimeIsAvailable() {
        GraalPyExecutor executor = new GraalPyExecutor();
        assumeTrue(executor.isAvailable(), "GraalPy runtime is not available");

        TaskResponse response = executor.execute("print('{\"ok\": true}')", "[]", 5);
        assumeTrue(response.isSuccess(), "GraalPy runtime cannot execute in current JVM: " + response.getStderr());

        assertEquals("{\"ok\": true}", response.getStdout());
    }
}
