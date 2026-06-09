package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.liang.data.agent.ai.code.PythonCodeExecutor;
import com.liang.data.agent.common.ratelimit.ResourceType;
import com.liang.data.agent.service.ratelimit.ResourceGate;
import com.liang.data.agent.service.ratelimit.ResourcePermit;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static com.liang.data.agent.common.constant.NodeOutputKey.PYTHON_EXECUTE_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.NodeOutputKey.PYTHON_GENERATE_NODE_OUTPUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Python 执行节点资源门控测试。
 */
class PythonExecuteNodeTest {

    @Test
    void applyShouldNotExecutePythonWhenPermitRejected() throws Exception {
        PythonCodeExecutor pythonCodeExecutor = mock(PythonCodeExecutor.class);
        ResourceGate resourceGate = mock(ResourceGate.class);
        when(resourceGate.tryAcquire(eq(ResourceType.PYTHON_EXECUTION), anyString(), any()))
                .thenReturn(ResourcePermit.rejected(ResourceType.PYTHON_EXECUTION, "python-execute"));
        PythonExecuteNode node = new PythonExecuteNode(pythonCodeExecutor, resourceGate);
        OverAllState state = mock(OverAllState.class);
        when(state.value(PYTHON_GENERATE_NODE_OUTPUT)).thenReturn(Optional.of("print('ok')"));

        Map<String, Object> result = node.apply(state);

        assertThat(result).containsKey(PYTHON_EXECUTE_NODE_OUTPUT);
        verify(pythonCodeExecutor, never()).execute(anyString(), anyString(), anyInt());
    }
}
