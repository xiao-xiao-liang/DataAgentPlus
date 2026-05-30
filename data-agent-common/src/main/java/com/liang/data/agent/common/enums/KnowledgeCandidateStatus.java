package com.liang.data.agent.common.enums;

import lombok.Getter;

@Getter
public enum KnowledgeCandidateStatus {

    DRAFT("DRAFT"),
    PENDING_REVIEW("PENDING_REVIEW"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    PUBLISHED("PUBLISHED");

    private final String code;

    KnowledgeCandidateStatus(String code) {
        this.code = code;
    }
}
