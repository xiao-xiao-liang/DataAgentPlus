package com.liang.data.agent.workflow.dto.node;

import lombok.Data;

@Data
public class ClarificationRequestDTO {

    private String reason;

    private String missingTerm;

    private String question;

    private String suggestedMemoryType;

    private Boolean memoryWorthSaving;

    private Boolean affectsSchemaRecall;
}
