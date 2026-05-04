package com.liang.data.agent.common.enums;

import lombok.Getter;

/**
 * 模型类型枚举
 *
 * <p>对应 model_config 表的 model_type 字段</p>
 */
@Getter
public enum ModelType {

    CHAT("CHAT"),
    EMBEDDING("EMBEDDING");

    private final String code;

    ModelType(String code) {
        this.code = code;
    }

    /**
     * 根据字符串代码查找枚举
     *
     * @param code 代码, 如 "CHAT"
     * @return 枚举值
     * @throws IllegalArgumentException 未知代码
     */
    public static ModelType fromCode(String code) {
        for (ModelType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的模型类型代码: " + code);
    }
}
