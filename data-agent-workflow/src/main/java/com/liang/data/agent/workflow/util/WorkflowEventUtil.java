package com.liang.data.agent.workflow.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.workflow.dto.GraphNodeResponse;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class WorkflowEventUtil {

    public static final String EVENT_PREFIX = "@@DATA_AGENT_EVENT@@";
    public static final String EVENT_SUFFIX = "@@END_DATA_AGENT_EVENT@@";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static String encode(String agentId, String threadId, String eventType, Object payload) {
        GraphNodeResponse response = GraphNodeResponse.builder()
                .agentId(agentId)
                .threadId(threadId)
                .eventType(eventType)
                .payload(payload)
                .text("")
                .build();
        try {
            return EVENT_PREFIX + OBJECT_MAPPER.writeValueAsString(response) + EVENT_SUFFIX + "\n";
        } catch (JsonProcessingException e) {
            return "";
        }
    }
}
