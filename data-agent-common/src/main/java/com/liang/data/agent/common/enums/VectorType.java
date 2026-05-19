package com.liang.data.agent.common.enums;

import com.liang.data.agent.common.exception.ServiceException;
import lombok.Getter;

/**
 * 向量文档类型枚举
 */
@Getter
public enum VectorType {

    TABLE("TABLE"),
    COLUMN("COLUMN"),
    KNOWLEDGE("KNOWLEDGE"),
    BUSINESS_TERM("BUSINESS_TERM");

    private final String code;

    VectorType(String code) {
        this.code = code;
    }

    public static VectorType fromCode(String code) {
        for (VectorType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new ServiceException("未知的向量文档类型: " + code);
    }
}