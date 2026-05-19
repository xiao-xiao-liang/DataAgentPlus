package com.liang.data.agent.workflow.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.workflow.prompt.PromptConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Objects;

/**
 * JSON 解析工具类，支持 LLM 自动修复格式错误的 JSON
 *
 * <p>工作流程:
 * 1. 先尝试直接解析
 * 2. 如果失败，移除 &lt;/think&gt; 标签后重试
 * 3. 如果仍失败，调用 LLM 修复 JSON 格式 (最多 3 次)
 * </p>
 *
 * <p>为什么需要 LLM 修复？因为大模型输出的 JSON 经常有:
 * - 多余的逗号 (trailing comma)
 * - 缺少引号
 * - 被 Markdown 代码块包裹
 * - 被 &lt;think&gt; 标签包裹 (DeepSeek 等模型)
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonParseUtil {

    private final LlmService llmService;

    private static final int MAX_RETRY_COUNT = 3;
    private static final String THINK_END_TAG = "</think>";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 尝试将 JSON 字符串转换为指定类型
     */
    public <T> T tryConvertToObject(String json, Class<T> clazz) {
        if (!StringUtils.hasText(json)) {
            throw new ServiceException("传入的JSON不能为空或者空字符串");
        }
        return tryConvertToObjectInternal(json, (mapper, curJson) -> mapper.readValue(curJson, clazz));
    }

    /**
     * 尝试将 JSON 字符串转换为指定类型 (支持 TypeReference，如 List&lt;String&gt;)
     */
    public <T> T tryConvertToObject(String json, TypeReference<T> typeReference) {
        if (!StringUtils.hasText(json)) {
            throw new ServiceException("传入的JSON不能为空或者空字符串");
        }
        return tryConvertToObjectInternal(json, (mapper, curJson) -> mapper.readValue(curJson, typeReference));
    }

    private <T> T tryConvertToObjectInternal(String json, JsonParserFunction<T> parser) {
        log.info("尝试解析 JSON: {}", json);
        String curJson = removeThinkTags(json);
        Exception lastException = null;

        // 先尝试直接解析
        try {
            return parser.parse(OBJECT_MAPPER, curJson);
        } catch (JsonProcessingException e) {
            log.warn("初次解析失败，准备调用 LLM 修复: {}", e.getMessage());
        }

        // LLM 修复重试
        for (int i = 0; i < MAX_RETRY_COUNT; i++) {
            try {
                curJson = callLlmToFix(curJson, Objects.nonNull(lastException) ? lastException.getMessage() : "Unknown error");
                return parser.parse(OBJECT_MAPPER, curJson);
            } catch (JsonProcessingException e) {
                lastException = e;
                log.warn("第 {} 次 LLM 修复后仍失败: {}", i + 1, e.getMessage());
            }
        }
        throw new ServiceException(String.format("经过 %d 次 LLM 修复后仍无法解析 JSON", MAX_RETRY_COUNT));
    }

    private String callLlmToFix(String json, String errorMessage) {
        try {
            String prompt = PromptConstant.getJsonFixPromptTemplate()
                    .render(Map.of("json_string", json, "error_message", errorMessage));
            Flux<ChatResponse> responseFlux = llmService.callUser(prompt);
            String fixedJson = responseFlux.map(ChatResponse::getResults)
                    .collect(StringBuilder::new, StringBuilder::append)
                    .map(StringBuilder::toString)
                    .block();

            if (null == fixedJson) {
                log.warn("LLM 修复返回 null，使用原始 JSON");
                return json;
            }

            // 清理: 移除 think 标签 + 提取 Markdown 代码块
            String cleanedJson = removeThinkTags(fixedJson);
            cleanedJson = MarkdownParserUtil.extractRawText(cleanedJson);
            return cleanedJson != null ? cleanedJson : json;
        } catch (Exception e) {
            log.error("调用 LLM 修复 JSON 时发生异常", e);
            return json;
        }
    }

    @FunctionalInterface
    private interface JsonParserFunction<T> {
        T parse(ObjectMapper mapper, String json) throws JsonProcessingException;
    }

    /**
     * 移除 &lt;/think&gt; 标签及其之前的所有内容
     */
    private String removeThinkTags(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        int lastEndTagIndex = text.indexOf(THINK_END_TAG);
        if (lastEndTagIndex != -1) {
            return text.substring(lastEndTagIndex + THINK_END_TAG.length()).trim();
        }
        return text.trim();
    }
}
