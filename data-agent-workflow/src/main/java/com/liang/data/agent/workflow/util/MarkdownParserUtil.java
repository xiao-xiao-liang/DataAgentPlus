package com.liang.data.agent.workflow.util;

import lombok.NoArgsConstructor;

/**
 * Markdown 代码块提取工具
 *
 * <p>LLM 经常把 JSON/SQL 包裹在 ```json ... ``` 代码块中，
 * 此工具负责提取代码块内的纯文本内容</p>
 */
@NoArgsConstructor
public final class MarkdownParserUtil {

    /**
     * 提取 Markdown 代码块中的文本，并将换行符替换为空格
     *
     * <p>适用于需要单行 JSON 的场景</p>
     */
    public static String extractText(String markdownCode) {
        String code = extractRawText(markdownCode);
        return code.replaceAll("\r\n", " ").replaceAll("\n", " ").replaceAll("\r", " ");
    }

    /**
     * 提取 Markdown 代码块中的原始文本 (保留换行)
     *
     * <p>支持 3 个及以上反引号的代码块，自动跳过语言标识行 (如 ```json)</p>
     *
     * @param markdownCode 可能包含代码块的文本
     * @return 代码块内容，或原文 (如果没有代码块)
     */
    public static String extractRawText(String markdownCode) {
        // 查找代码块开始标记 (3 个及以上反引号)
        int startIndex = -1;
        int delimiterLength = 0;

        for (int i = 0; i <= markdownCode.length() - 3; i++) {
            if (markdownCode.startsWith("```", i)) {
                startIndex = i;
                delimiterLength = 3;
                // 计算额外的反引号数量
                while (i + delimiterLength < markdownCode.length()
                        && markdownCode.charAt(i + delimiterLength) == '`') {
                    delimiterLength++;
                }
                break;
            }
        }

        if (startIndex == -1) {
            return markdownCode;
        }

        // 跳过开始标记和可选的语言标识行
        int contentStart = startIndex + delimiterLength;
        while (contentStart < markdownCode.length() && markdownCode.charAt(contentStart) != '\n') {
            contentStart++;
        }
        if (contentStart < markdownCode.length() && markdownCode.charAt(contentStart) == '\n') {
            contentStart++;
        }

        // 查找结束标记
        String closingDelimiter = "`".repeat(delimiterLength);
        int endIndex = markdownCode.indexOf(closingDelimiter, contentStart);

        if (endIndex == -1) {
            return markdownCode.substring(contentStart);
        }

        return markdownCode.substring(contentStart, endIndex);
    }
}
