package com.liang.data.agent.common.enums;

import lombok.Getter;

@Getter
public enum KnowledgeCandidateType {

    BUSINESS_KNOWLEDGE("BUSINESS_KNOWLEDGE"),
    AGENT_QA("AGENT_QA"),
    SEMANTIC_MODEL("SEMANTIC_MODEL"),
    LOGICAL_RELATION("LOGICAL_RELATION");

    private final String code;

    KnowledgeCandidateType(String code) {
        this.code = code;
    }
}
