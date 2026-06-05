package com.liang.data.agent.common.enums;

import lombok.Getter;

/**
 * 知识切分策略类型枚举。
 *
 * @author 资深Java架构师
 */
@Getter
public enum SplitterType {

    /**
     * 标题分块
     */
    TITLE("title"),

    /**
     * 智能分块
     */
    SMART("smart"),

    /**
     * Token分块
     */
    TOKEN("token"),

    /**
     * 长度分块
     */
    LENGTH("length"),

    /**
     * 分隔符分块
     */
    SEPARATOR("separator"),

    /**
     * 递归分块
     */
    RECURSIVE("recursive"),

    /**
     * 句子分块
     */
    SENTENCE("sentence"),

    /**
     * 正则分块
     */
    REGEX("regex");

    private final String code;

    SplitterType(String code) {
        this.code = code;
    }

    /**
     * 校验传入的代码是否为合法的分块策略。
     *
     * @param code 分块策略代码
     * @return 是否合法
     */
    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String trimCode = code.trim();
        for (SplitterType type : values()) {
            if (type.code.equalsIgnoreCase(trimCode)) {
                return true;
            }
        }
        return false;
    }
}
