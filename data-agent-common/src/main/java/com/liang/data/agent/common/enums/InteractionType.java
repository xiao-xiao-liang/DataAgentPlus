package com.liang.data.agent.common.enums;

import lombok.Getter;

@Getter
public enum InteractionType {

    NEW_QUERY("NEW_QUERY"),
    CLARIFICATION_ANSWER("CLARIFICATION_ANSWER"),
    CLARIFICATION_CONFIRM("CLARIFICATION_CONFIRM"),
    HUMAN_PLAN_FEEDBACK("HUMAN_PLAN_FEEDBACK");

    private final String code;

    InteractionType(String code) {
        this.code = code;
    }

    public static InteractionType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return NEW_QUERY;
        }
        for (InteractionType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return NEW_QUERY;
    }
}
