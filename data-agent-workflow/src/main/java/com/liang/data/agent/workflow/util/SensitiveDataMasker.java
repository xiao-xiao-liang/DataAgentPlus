package com.liang.data.agent.workflow.util;

import com.liang.data.agent.dal.connector.bo.ResultSetBO;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 根据结果列名识别并脱敏敏感数据。
 */
@NoArgsConstructor
public final class SensitiveDataMasker {

    private static final Set<String> AGGREGATE_TOKENS = Set.of(
            "count", "total", "avg", "average", "sum", "max", "min"
    );
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^(\\d{15}|\\d{17}[0-9Xx])$");

    public static ResultSetBO mask(ResultSetBO resultSet) {
        if (resultSet == null || resultSet.data() == null || resultSet.data().isEmpty()) {
            return resultSet;
        }

        Map<String, SensitiveType> sensitiveColumns = new LinkedHashMap<>();
        for (String column : resultSet.columns()) {
            SensitiveType type = detectType(column);
            if (type != SensitiveType.NONE) {
                sensitiveColumns.put(column, type);
            }
        }
        List<Map<String, String>> maskedData = resultSet.data().stream()
                .map(row -> maskRow(row, sensitiveColumns))
                .toList();
        if (maskedData.equals(resultSet.data())) {
            return resultSet;
        }
        return new ResultSetBO(resultSet.columns(), maskedData);
    }

    private static Map<String, String> maskRow(Map<String, String> row, Map<String, SensitiveType> sensitiveColumns) {
        Map<String, String> maskedRow = new LinkedHashMap<>(row);
        maskedRow.replaceAll((column, value) -> {
            SensitiveType type = sensitiveColumns.getOrDefault(column, SensitiveType.NONE);
            if (type == SensitiveType.NONE && !isAggregateColumn(column)) {
                type = detectValueType(value);
            }
            return maskValue(value, type);
        });
        return maskedRow;
    }

    private static SensitiveType detectType(String column) {
        String normalized = normalize(column);
        List<String> tokens = Arrays.asList(normalized.split("_"));
        if (isAggregateColumn(tokens)) {
            return SensitiveType.NONE;
        }

        String compact = normalized.replace("_", "");
        if (matchesEnglish(tokens, compact, "password", "passwd", "pwd", "secret", "token", "apikey",
                "accesskey", "privatekey", "clientsecret", "credential")
                || containsAny(compact, "密码", "密钥", "令牌")) {
            return SensitiveType.SECRET;
        }
        if (matchesEnglish(tokens, compact, "idcard", "identitycard", "citizenid", "nationalid")
                || containsAny(compact, "身份证", "证件号")) {
            return SensitiveType.ID_CARD;
        }
        if (matchesEnglish(tokens, compact, "email", "mail")
                || containsAny(compact, "邮箱", "电子邮箱")) {
            return SensitiveType.EMAIL;
        }
        if (matchesEnglish(tokens, compact, "phone", "mobile", "telephone", "cellphone")
                || tokens.contains("tel")
                || containsAny(compact, "手机", "电话", "手机号")) {
            return SensitiveType.PHONE;
        }
        if (matchesEnglish(tokens, compact, "address") || tokens.contains("addr")
                || containsAny(compact, "地址", "住址")) {
            return SensitiveType.ADDRESS;
        }
        return SensitiveType.NONE;
    }

    private static SensitiveType detectValueType(String value) {
        if (value == null || value.isBlank()) {
            return SensitiveType.NONE;
        }
        if (EMAIL_PATTERN.matcher(value).matches()) {
            return SensitiveType.EMAIL;
        }
        if (PHONE_PATTERN.matcher(value).matches()) {
            return SensitiveType.PHONE;
        }
        if (ID_CARD_PATTERN.matcher(value).matches()) {
            return SensitiveType.ID_CARD;
        }
        return SensitiveType.NONE;
    }

    private static boolean isAggregateColumn(String column) {
        return isAggregateColumn(Arrays.asList(normalize(column).split("_")));
    }

    private static boolean isAggregateColumn(List<String> tokens) {
        return tokens.stream().anyMatch(AGGREGATE_TOKENS::contains);
    }

    private static String normalize(String column) {
        if (column == null) {
            return "";
        }
        return column
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]+", "_")
                .toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... keywords) {
        return Arrays.stream(keywords).anyMatch(value::contains);
    }

    private static boolean matchesEnglish(List<String> tokens, String compact, String... keywords) {
        return Arrays.stream(keywords)
                .anyMatch(keyword -> tokens.contains(keyword)
                        || compact.equals(keyword)
                        || compact.endsWith(keyword));
    }

    private static String maskValue(String value, SensitiveType type) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return switch (type) {
            case SECRET -> "******";
            case EMAIL -> maskEmail(value);
            case PHONE, ID_CARD -> keepEnds(value);
            case ADDRESS -> keepStart(value);
            case NONE -> value;
        };
    }

    private static String maskEmail(String value) {
        int at = value.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return value.charAt(0) + "***" + value.substring(at);
    }

    private static String keepEnds(String value) {
        if (value.length() <= 3 + 4) {
            return "***";
        }
        return value.substring(0, 3)
                + "*".repeat(value.length() - 3 - 4)
                + value.substring(value.length() - 4);
    }

    private static String keepStart(String value) {
        if (value.length() <= 3) {
            return "***";
        }
        return value.substring(0, 3) + "***";
    }

    private enum SensitiveType {
        NONE,
        PHONE,
        EMAIL,
        ID_CARD,
        ADDRESS,
        SECRET
    }
}
