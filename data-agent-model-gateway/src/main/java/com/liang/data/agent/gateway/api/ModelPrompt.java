package com.liang.data.agent.gateway.api;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 模型提示词，支持模板引用与直接消息两种互斥模式。
 *
 * @param templateId 模板标识
 * @param variables 模板变量
 * @param messages 直接消息列表
 */
public record ModelPrompt(String templateId, Map<String, Object> variables, List<ModelMessage> messages) {

    public ModelPrompt {
        // 1. 递归复制模板变量，避免嵌套集合或数组在构造后被调用方修改。
        variables = copyVariables(variables);
        messages = messages == null ? List.of() : List.copyOf(messages);
        // 2. 校验提示词来源，模板模式与直接消息模式必须二选一。
        boolean templateMode = templateId != null && !templateId.isBlank();
        if (templateMode == !messages.isEmpty()) {
            throw new IllegalArgumentException("模板标识与直接消息必须且只能提供一种");
        }
    }

    /**
     * 创建直接消息模式的提示词。
     *
     * @param messages 直接消息列表
     * @return 直接消息模式提示词
     */
    public static ModelPrompt direct(List<ModelMessage> messages) {
        return new ModelPrompt(null, Map.of(), messages);
    }

    /**
     * 创建模板模式的提示词。
     *
     * @param templateId 模板标识
     * @param variables 模板变量
     * @return 模板模式提示词
     */
    public static ModelPrompt template(String templateId, Map<String, Object> variables) {
        return new ModelPrompt(templateId, variables, List.of());
    }

    private static Map<String, Object> copyVariables(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copiedVariables = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            // 1. 校验模板变量名，变量名必须是明确的字符串。
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("模板变量名不能为空");
            }
            // 2. 递归复制变量值，仅允许协议明确支持的不可变快照类型。
            copiedVariables.put(key, copyVariableValue(value));
        });
        return Map.copyOf(copiedVariables);
    }

    private static Object copyVariableValue(Object value) {
        Objects.requireNonNull(value, "模板变量值不能为空");
        if (isSupportedScalar(value)) {
            return value;
        }
        if (value instanceof List<?> listValue) {
            return copyListValue(listValue);
        }
        if (value instanceof Map<?, ?> mapValue) {
            return copyMapValue(mapValue);
        }
        if (value.getClass().isArray()) {
            return copyArrayValue(value);
        }
        throw new IllegalArgumentException("模板变量值类型不支持：" + value.getClass().getName());
    }

    private static boolean isSupportedScalar(Object value) {
        return value instanceof String
                || isSupportedNumber(value)
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof TemporalAccessor;
    }

    private static boolean isSupportedNumber(Object value) {
        return value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigDecimal
                || value instanceof BigInteger;
    }

    private static List<Object> copyListValue(List<?> source) {
        // 1. 逐项递归复制列表元素，避免保留外部可变对象引用。
        return source.stream()
                .map(ModelPrompt::copyVariableValue)
                .toList();
    }

    private static Map<String, Object> copyMapValue(Map<?, ?> source) {
        Map<String, Object> copiedMap = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            // 1. 嵌套 Map 仅支持字符串键，保持模板变量表达一致。
            if (!(key instanceof String stringKey) || stringKey.isBlank()) {
                throw new IllegalArgumentException("模板变量嵌套Map键必须为非空字符串");
            }
            // 2. 递归复制嵌套 Map 的值。
            copiedMap.put(stringKey, copyVariableValue(value));
        });
        return Map.copyOf(copiedMap);
    }

    private static List<Object> copyArrayValue(Object source) {
        int length = Array.getLength(source);
        java.util.ArrayList<Object> copiedArray = new java.util.ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            // 1. 将数组转为不可修改列表快照，避免数组元素被外部替换后影响提示词。
            copiedArray.add(copyVariableValue(Array.get(source, index)));
        }
        return List.copyOf(copiedArray);
    }
}
