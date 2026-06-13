package com.liang.data.agent.workflow.util;

import static com.liang.data.agent.workflow.constants.JsonParseConstants.MAX_RETRY_COUNT;
import static com.liang.data.agent.workflow.constants.JsonParseConstants.THINK_END_TAG;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
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
 * JSON parser with deterministic cleanup before falling back to LLM repair.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonParseUtil {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LlmService llmService;

    public <T> T tryConvertToObject(String json, Class<T> clazz) {
        if (!StringUtils.hasText(json)) {
            throw new ServiceException("传入的JSON不能为空或者空字符串");
        }
        return tryConvertToObjectInternal(json, (mapper, curJson) -> mapper.readValue(curJson, clazz));
    }

    public <T> T tryConvertToObject(String json, TypeReference<T> typeReference) {
        if (!StringUtils.hasText(json)) {
            throw new ServiceException("传入的JSON不能为空或者空字符串");
        }
        return tryConvertToObjectInternal(json, (mapper, curJson) -> mapper.readValue(curJson, typeReference));
    }

    private <T> T tryConvertToObjectInternal(String json, JsonParserFunction<T> parser) {
        log.info("尝试解析 JSON: {}", json);
        String curJson = normalizeJsonCandidate(json);
        Exception lastException;

        try {
            return parser.parse(OBJECT_MAPPER, curJson);
        } catch (JsonProcessingException e) {
            lastException = e;
            log.warn("初次解析失败，准备调用 LLM 修复: {}", e.getMessage());
        }

        for (int i = 0; i < MAX_RETRY_COUNT; i++) {
            try {
                curJson = callLlmToFix(curJson, lastException.getMessage());
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
            String fixedJson = responseFlux
                    .map(ChatResponseUtil::getText)
                    .collect(StringBuilder::new, StringBuilder::append)
                    .map(StringBuilder::toString)
                    .block();

            if (!StringUtils.hasText(fixedJson)) {
                log.warn("LLM 修复返回空内容，使用原始 JSON");
                return json;
            }

            String cleanedJson = normalizeJsonCandidate(fixedJson);
            return StringUtils.hasText(cleanedJson) ? cleanedJson : json;
        } catch (Exception e) {
            log.error("调用 LLM 修复 JSON 时发生异常", e);
            return json;
        }
    }

    private String normalizeJsonCandidate(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String cleaned = removeThinkTags(text);
        cleaned = MarkdownParserUtil.extractRawText(cleaned).trim();
        return extractBalancedJson(cleaned);
    }

    private String extractBalancedJson(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        int start = findFirstJsonStart(text);
        if (start < 0) {
            return text.trim();
        }

        char opening = text.charAt(start);
        char closing = opening == '{' ? '}' : ']';
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char current = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
            } else if (current == opening) {
                depth++;
            } else if (current == closing) {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1).trim();
                }
            }
        }
        return text.substring(start).trim();
    }

    private int findFirstJsonStart(String text) {
        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');
        if (objectStart < 0) {
            return arrayStart;
        }
        if (arrayStart < 0) {
            return objectStart;
        }
        return Math.min(objectStart, arrayStart);
    }

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

    @FunctionalInterface
    private interface JsonParserFunction<T> {
        T parse(ObjectMapper mapper, String json) throws JsonProcessingException;
    }
}
