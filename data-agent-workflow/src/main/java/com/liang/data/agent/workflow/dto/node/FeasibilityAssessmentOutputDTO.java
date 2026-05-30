package com.liang.data.agent.workflow.dto.node;

import lombok.Data;

@Data
public class FeasibilityAssessmentOutputDTO {

    private String requestType;

    private String language;

    private String reason;

    private String analysisGoal;

    private String missingTerm;

    private String clarificationQuestion;

    private String suggestedMemoryType;

    private Boolean memoryWorthSaving;

    private Boolean affectsSchemaRecall;
}
