package com.liang.data.agent.common.enums;

import lombok.Getter;

@Getter
public enum FeasibilityRequestType {

    DATA_ANALYSIS("DATA_ANALYSIS"),
    NEED_CLARIFICATION("NEED_CLARIFICATION"),
    CHAT("CHAT");

    private final String code;

    FeasibilityRequestType(String code) {
        this.code = code;
    }
}
