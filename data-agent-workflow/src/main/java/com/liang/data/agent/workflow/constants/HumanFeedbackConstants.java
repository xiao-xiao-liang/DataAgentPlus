package com.liang.data.agent.workflow.constants;

import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;

/**
 * 人工反馈识别常量。
 */
@NoArgsConstructor
public final class HumanFeedbackConstants {

    public static final double APPROVE_CONFIDENCE_THRESHOLD = 0.9D;
    public static final Duration MODEL_TIMEOUT = Duration.ofSeconds(15);

    public static final List<String> MODIFICATION_SIGNALS = List.of(
            "但是", "不过", "但", "只是", "另外", "顺便",
            "改", "修改", "调整", "换成", "改成", "重新",
            "不要", "别", "不用", "取消", "去掉",
            "增加", "补充", "加上", "减少", "删除",
            "不对", "有问题", "存在问题", "不准确", "不是", "优化"
    );

    public static final List<String> APPROVE_EXACT_PHRASES = List.of(
            "可以", "可以的", "确认", "确认执行", "没问题", "同意",
            "好的", "好", "ok", "okay", "yes", "行", "就这样", "对的"
    );

    public static final List<String> APPROVE_CONTAINS_PHRASES = List.of(
            "按你说的来", "按这个来", "就这样", "就这么办", "开始吧",
            "执行吧", "继续执行", "照此执行", "开始任务", "开始执行"
    );
}
