package com.liang.data.agent.workflow.util;

import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * 在 SQL 到达 JDBC 驱动之前对其进行防护处理
 */
@NoArgsConstructor
public final class SqlStatementGuard {

    public static boolean isSingleStatementQuery(String sql) {
        if (StringUtils.isBlank(sql)) {
            return false;
        }

        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }

            if (!singleQuote && !doubleQuote && !backtick) {
                if (current == '-' && next == '-') {
                    lineComment = true;
                    i++;
                    continue;
                }
                if (current == '/' && next == '*') {
                    blockComment = true;
                    i++;
                    continue;
                }
            }

            if (!doubleQuote && !backtick && current == '\'') {
                if (singleQuote && next == '\'') {
                    i++;
                } else {
                    singleQuote = !singleQuote;
                }
                continue;
            }
            if (!singleQuote && !backtick && current == '"') {
                doubleQuote = !doubleQuote;
                continue;
            }
            if (!singleQuote && !doubleQuote && current == '`') {
                backtick = !backtick;
                continue;
            }

            if (!singleQuote && !doubleQuote && !backtick && current == ';') {
                return !hasNonWhitespaceAfter(sql, i + 1);
            }
        }

        return true;
    }

    private static boolean hasNonWhitespaceAfter(String sql, int start) {
        for (int i = start; i < sql.length(); i++) {
            if (!Character.isWhitespace(sql.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
