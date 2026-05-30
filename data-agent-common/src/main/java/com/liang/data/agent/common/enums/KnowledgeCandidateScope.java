package com.liang.data.agent.common.enums;

import lombok.Getter;

@Getter
public enum KnowledgeCandidateScope {

    SESSION("SESSION"),
    USER("USER"),
    AGENT("AGENT"),
    DATASOURCE("DATASOURCE"),
    ORG("ORG");

    private final String code;

    KnowledgeCandidateScope(String code) {
        this.code = code;
    }
}
