package com.liang.data.agent.workflow.dto;

import com.liang.data.agent.workflow.constants.WorkflowEventConstants;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraphStreamChunkTest {

    @Test
    void shouldMarkContentAsNodeOutputEvent() {
        GraphStreamChunk chunk = GraphStreamChunk.content("输出", "node-1");

        assertThat(chunk.eventType()).isEqualTo(WorkflowEventConstants.EVENT_NODE_OUTPUT);
        assertThat(chunk.hasContent()).isTrue();
        assertThat(chunk.nodeCompleted()).isFalse();
    }

    @Test
    void shouldMarkNodeCompletedAsStructuredEventWithoutVisibleContent() {
        GraphStreamChunk chunk = GraphStreamChunk.nodeCompleted("node-1");

        assertThat(chunk.eventType()).isEqualTo(WorkflowEventConstants.EVENT_NODE_COMPLETED);
        assertThat(chunk.hasContent()).isFalse();
        assertThat(chunk.nodeCompleted()).isTrue();
    }

    @Test
    void shouldCreateAllStructuredWorkflowEvents() {
        assertThat(GraphStreamChunk.nodeStarted("node-1").eventType())
                .isEqualTo(WorkflowEventConstants.EVENT_NODE_STARTED);
        assertThat(GraphStreamChunk.waitingUserInput("node-1", "等待确认").eventType())
                .isEqualTo(WorkflowEventConstants.EVENT_WAITING_USER_INPUT);
        assertThat(GraphStreamChunk.error("失败", "node-1").eventType())
                .isEqualTo(WorkflowEventConstants.EVENT_WORKFLOW_ERROR);
        assertThat(GraphStreamChunk.done().eventType())
                .isEqualTo(WorkflowEventConstants.EVENT_WORKFLOW_DONE);
    }
}
