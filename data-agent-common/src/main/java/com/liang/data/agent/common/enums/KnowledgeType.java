package com.liang.data.agent.common.enums;

import lombok.Getter;

/**
 * 智能体知识类型枚举。
 *
 * @author 资深Java架构师
 */
@Getter
public enum KnowledgeType {

    /**
     * 文档类型
     */
    DOCUMENT("DOCUMENT"),

    /**
     * 问答类型
     */
    QA("QA"),

    /**
     * 常见问题类型
     */
    FAQ("FAQ");

    private final String code;

    KnowledgeType(String code) {
        this.code = code;
    }
}
