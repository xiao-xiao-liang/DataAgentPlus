package com.liang.data.agent.workflow.util;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.workflow.dto.node.QueryEnhanceOutputDTO;
import lombok.NoArgsConstructor;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.liang.data.agent.common.constant.NodeOutputKey.QUERY_ENHANCE_NODE_OUTPUT;

/**
 * State 管理工具类，提供类型安全的 state 取值方法
 *
 * <p>避免在每个节点中重复写 state.value(key).map(...).orElse(...) 样板代码</p>
 */
@NoArgsConstructor
public final class StateUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 获取 String 类型的 state 值 (不存在则抛异常)
     */
    public static String getStringValue(OverAllState state, String key) {
        return state.value(key)
                .map(String.class::cast)
                .orElseThrow(() -> new ServiceException("State 中缺失关键配置或状态数据: " + key));
    }

    /**
     * 获取 String 类型的 state 值 (不存在则返回默认值)
     */
    public static String getStringValue(OverAllState state, String key, String defaultValue) {
        return state.value(key)
                .map(String.class::cast)
                .orElse(defaultValue);
    }

    /**
     * 获取 List 类型的 state 值
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> getListValue(OverAllState state, String key) {
        return state.value(key)
                .map(v -> (List<T>) v)
                .orElseThrow(() -> new ServiceException("State 中缺失列表数据。Key" + key));
    }

    /**
     * 获取对象类型的 state 值 (不存在则抛异常)
     *
     * <p>自动处理 HashMap → 目标类型的反序列化 (StateGraph 内部可能将对象存为 Map)</p>
     */
    public static <T> T getObjectValue(OverAllState state, String key, Class<T> type) {
        return state.value(key)
                .map(value -> deserializeIfNeeded(value, type))
                .orElseThrow(() -> new ServiceException("State 中缺失对象数据。Key：" + key));
    }

    /**
     * 获取对象类型的 state 值 (不存在则返回默认值)
     */
    public static <T> T getObjectValue(OverAllState state, String key, Class<T> type, T defaultValue) {
        return state.value(key)
                .map(value -> deserializeIfNeeded(value, type))
                .orElse(defaultValue);
    }

    /**
     * 获取对象类型的 state 值 (不存在则用 Supplier 提供默认值)
     */
    public static <T> T getObjectValue(OverAllState state, String key, Class<T> type, Supplier<T> defaultSupplier) {
        return state.value(key)
                .map(type::cast)
                .orElseGet(defaultSupplier);
    }

    /**
     * 检查 state 中是否存在指定 key 的有效值
     */
    public static boolean hasValue(OverAllState state, String key) {
        Optional<Object> value = state.value(key);
        if (value.isPresent()) {
            if (value.get() instanceof String content) {
                return !content.isEmpty();
            }
            return true;
        }
        return false;
    }

    /**
     * 获取 Document 列表
     */
    public static List<Document> getDocumentList(OverAllState state, String key) {
        return getListValue(state, key);
    }

    /**
     * 获取规范化查询 (从 QueryEnhanceNode 的输出中提取)
     */
    public static String getCanonicalQuery(OverAllState state) {
        var dto = getObjectValue(state, QUERY_ENHANCE_NODE_OUTPUT, QueryEnhanceOutputDTO.class);
        return dto.getCanonicalQuery();
    }

    /**
     * 处理 HashMap → 目标类型的反序列化
     */
    private static <T> T deserializeIfNeeded(Object value, Class<T> type) {
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        if (value instanceof HashMap && !type.equals(HashMap.class)) {
            return OBJECT_MAPPER.convertValue(value, type);
        }
        return type.cast(value);
    }
}
