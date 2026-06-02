package com.liang.data.agent.workflow.dto.humanfeedback;

import lombok.Data;

/**
 * 人工反馈意图识别结果。
 *
 * <p>封装识别出的意图、置信度和原因，供工作流恢复时决定是否继续执行当前计划。</p>
 */
@Data
public class HumanFeedbackIntentResult {

    /**
     * 识别出的人工反馈意图。
     */
    private HumanFeedbackIntent intent;

    /**
     * 识别置信度，范围为 0 到 1。
     */
    private double confidence;

    /**
     * 识别原因。
     */
    private String reason;

    /**
     * 是否包含修改计划的要求。
     */
    private boolean hasModificationRequest;

    private HumanFeedbackIntentResult(HumanFeedbackIntent intent,
                                      double confidence,
                                      String reason,
                                      boolean hasModificationRequest) {
        this.intent = intent;
        this.confidence = confidence;
        this.reason = reason;
        this.hasModificationRequest = hasModificationRequest;
    }

    /**
     * 创建人工反馈意图识别结果。
     *
     * @param intent                 识别出的人工反馈意图
     * @param confidence             识别置信度
     * @param reason                 识别原因
     * @param hasModificationRequest 是否包含修改计划的要求
     * @return 人工反馈意图识别结果
     */
    public static HumanFeedbackIntentResult of(HumanFeedbackIntent intent,
                                               double confidence,
                                               String reason,
                                               boolean hasModificationRequest) {
        return new HumanFeedbackIntentResult(intent, confidence, reason, hasModificationRequest);
    }
}
