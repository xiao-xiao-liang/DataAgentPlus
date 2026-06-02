package com.liang.data.agent.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.workflow.dto.humanfeedback.HumanFeedbackIntent;
import com.liang.data.agent.workflow.dto.humanfeedback.HumanFeedbackIntentResult;
import com.liang.data.agent.workflow.util.MarkdownParserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

/**
 * 人工反馈意图识别服务。
 *
 * <p>用于识别用户在人工审核节点中输入的反馈内容，优先通过高确定性的规则判断，
 * 规则无法判断时再调用大模型进行语义分类。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HumanFeedbackIntentService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final double APPROVE_CONFIDENCE_THRESHOLD = 0.9D;
    private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(15);

    private static final List<String> MODIFICATION_SIGNALS = List.of(
            "但是", "不过", "但", "只是", "另外", "顺便",
            "改", "修改", "调整", "换成", "改成", "重新",
            "不要", "别", "不用", "取消", "去掉",
            "增加", "补充", "加上", "减少", "删除",
            "不对", "有问题", "存在问题", "不准确", "不是", "优化"
    );

    private static final List<String> APPROVE_EXACT_PHRASES = List.of(
            "可以", "可以的", "确认", "确认执行", "没问题", "同意",
            "好的", "好", "ok", "okay", "yes", "行", "就这样", "对的"
    );

    private static final List<String> APPROVE_CONTAINS_PHRASES = List.of(
            "按你说的来", "按这个来", "就这样", "就这么办", "开始吧",
            "执行吧", "继续执行", "照此执行", "开始任务", "开始执行"
    );

    private final LlmService llmService;

    /**
     * 在没有模型服务的测试场景中创建仅使用规则的识别服务。
     *
     * @return 人工反馈意图识别服务
     */
    public static HumanFeedbackIntentService withoutModel() {
        return new HumanFeedbackIntentService(null);
    }

    /**
     * 识别人工反馈文本的真实意图。
     *
     * @param feedbackContent 用户输入的反馈内容
     * @return 人工反馈意图识别结果
     */
    public HumanFeedbackIntentResult classify(String feedbackContent) {
        if (!StringUtils.hasText(feedbackContent)) {
            return uncertain("反馈内容为空");
        }

        String normalized = normalize(feedbackContent);
        if (containsModificationSignal(normalized)) {
            return HumanFeedbackIntentResult.of(
                    HumanFeedbackIntent.REVISE,
                    1.0D,
                    "命中明确的修改或否定信号",
                    true
            );
        }
        if (isExplicitApprove(normalized)) {
            return HumanFeedbackIntentResult.of(
                    HumanFeedbackIntent.APPROVE,
                    1.0D,
                    "命中明确的确认表达",
                    false
            );
        }

        return classifyByModel(feedbackContent);
    }

    private HumanFeedbackIntentResult classifyByModel(String feedbackContent) {
        if (llmService == null) {
            return uncertain("未配置模型服务");
        }
        try {
            String modelOutput = llmService.toStringFlux(callModel(feedbackContent))
                    .collectList()
                    .map(parts -> String.join("", parts))
                    .block(MODEL_TIMEOUT);
            if (!StringUtils.hasText(modelOutput)) {
                return uncertain("模型未返回识别结果");
            }

            String json = MarkdownParserUtil.extractRawText(modelOutput).trim();
            HumanFeedbackIntentModelResponse response = OBJECT_MAPPER.readValue(json, HumanFeedbackIntentModelResponse.class);
            return normalizeModelResult(response);
        } catch (Exception ex) {
            log.warn("人工反馈意图模型识别失败，按不确定意图处理，原因: {}", ex.getMessage());
            return uncertain("模型识别异常");
        }
    }

    private Flux<ChatResponse> callModel(String feedbackContent) {
        return llmService.call(buildSystemPrompt(), "用户反馈内容：\n" + feedbackContent);
    }

    private HumanFeedbackIntentResult normalizeModelResult(HumanFeedbackIntentModelResponse response) {
        HumanFeedbackIntent intent = parseIntent(response.getIntent());
        double confidence = response.getConfidence();
        boolean hasModificationRequest = response.isHasModificationRequest();

        if (hasModificationRequest) {
            intent = HumanFeedbackIntent.REVISE;
        } else if (intent == HumanFeedbackIntent.APPROVE && confidence < APPROVE_CONFIDENCE_THRESHOLD) {
            intent = HumanFeedbackIntent.UNCERTAIN;
        }

        return HumanFeedbackIntentResult.of(
                intent,
                confidence,
                StringUtils.hasText(response.getReason()) ? response.getReason() : "模型未返回原因",
                hasModificationRequest
        );
    }

    private HumanFeedbackIntent parseIntent(String intent) {
        if (!StringUtils.hasText(intent)) {
            return HumanFeedbackIntent.UNCERTAIN;
        }
        try {
            return HumanFeedbackIntent.valueOf(intent.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return HumanFeedbackIntent.UNCERTAIN;
        }
    }

    private boolean containsModificationSignal(String normalized) {
        return MODIFICATION_SIGNALS.stream().anyMatch(normalized::contains);
    }

    private boolean isExplicitApprove(String normalized) {
        return APPROVE_EXACT_PHRASES.contains(normalized)
                || APPROVE_CONTAINS_PHRASES.stream().anyMatch(normalized::contains);
    }

    private String normalize(String content) {
        return content.trim()
                .toLowerCase()
                .replaceAll("[\\p{Punct}\\s，。！？；：、“”‘’（）【】《》…·]+", "");
    }

    private HumanFeedbackIntentResult uncertain(String reason) {
        return HumanFeedbackIntentResult.of(
                HumanFeedbackIntent.UNCERTAIN,
                0D,
                reason,
                false
        );
    }

    private String buildSystemPrompt() {
        return """
                你是人工审核反馈意图分类器，只负责判断用户反馈是确认当前执行计划，还是要求修改当前执行计划。

                判定规则：
                1. 只有用户明确表示同意、确认、继续、开始执行，且没有新增要求时，才输出 APPROVE。
                2. 只要用户提出任何修改、补充、删除、调整、限制、纠错，都输出 REVISE。
                3. 如果既像确认又像修改，输出 REVISE。
                4. 如果无法判断，输出 UNCERTAIN。
                5. 严格输出 JSON，不要输出 Markdown 代码块，不要输出额外解释。

                JSON 格式：
                {
                  "intent": "APPROVE 或 REVISE 或 UNCERTAIN",
                  "confidence": 0.0 到 1.0,
                  "reason": "简短中文原因",
                  "hasModificationRequest": true 或 false
                }
                """;
    }

    /**
     * 人工反馈意图模型响应。
     *
     * <p>用于承接大模型返回的结构化 JSON 分类结果，仅在当前服务内部使用。</p>
     */
    @lombok.Data
    private static class HumanFeedbackIntentModelResponse {

        /**
         * 模型识别的意图：APPROVE、REVISE 或 UNCERTAIN。
         */
        private String intent;

        /**
         * 模型识别置信度。
         */
        private double confidence;

        /**
         * 模型给出的识别原因。
         */
        private String reason;

        /**
         * 是否包含修改计划的要求。
         */
        private boolean hasModificationRequest;
    }
}
