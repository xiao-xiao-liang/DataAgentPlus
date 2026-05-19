package com.liang.data.agent.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SSE 流式输出的文本类型标记枚举
 *
 * <p>节点通过 startSign/endSign 包裹内容，前端据此切换渲染模式（JSON高亮、SQL高亮等）</p>
 *
 * <p>状态机: TEXT → (收到 startSign) → JSON/SQL/... → (收到 endSign) → TEXT</p>
 */
@Getter
@AllArgsConstructor
public enum TextType {

    /**
     * JSON 格式 (意图识别、查询增强等结构化输出)
     */
    JSON("$$$json", "$$$"),

    /**
     * Python 代码
     */
    PYTHON("$$$python", "$$$"),

    /**
     * SQL 代码 (用 $$$ 而非 ``` 避免与 LLM 输出的 ```sql 冲突)
     */
    SQL("$$$sql", "$$$"),

    /**
     * Markdown 报告 (使用不同的结束标记避免歧义)
     */
    MARK_DOWN("$$$markdown-report", "$$$/markdown-report"),

    /**
     * 结果集 (SQL 执行结果)
     */
    RESULT_SET("$$$result_set", "$$$"),

    /**
     * 普通文本 (默认类型)
     */
    TEXT(null, null);

    /**
     * 开始标记
     */
    private final String startSign;

    /**
     * 结束标记
     */
    private final String endSign;

    /**
     * 状态机: 根据当前类型和新收到的 chunk 判断是否需要切换类型
     *
     * @param origin 当前文本类型
     * @param chunk  新收到的文本块
     * @return 切换后的文本类型
     */
    public static TextType getType(TextType origin, String chunk) {
        if (origin == TEXT) {
            // 当前是普通文本，检查是否收到了某个类型的开始标记
            for (TextType type : TextType.values()) {
                if (chunk.equals(type.startSign)) {
                    return type;
                }
            }
        } else {
            // 当前在某个特殊类型中，检查是否收到了结束标记
            if (chunk.equals(origin.endSign)) {
                return TEXT;
            }
        }
        return origin;
    }

    /**
     * 根据开始标记查找对应的文本类型
     *
     * @param startSign 开始标记字符串
     * @return 匹配的类型，未匹配返回 TEXT
     */
    public static TextType getTypeByStartSign(String startSign) {
        for (TextType type : TextType.values()) {
            if (startSign.equals(type.startSign)) {
                return type;
            }
        }
        return TEXT;
    }
}