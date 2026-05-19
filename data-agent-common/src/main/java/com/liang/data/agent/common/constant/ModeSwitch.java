package com.liang.data.agent.common.constant;

import lombok.NoArgsConstructor;

/**
 * 模式开关 Key
 */
@NoArgsConstructor
public final class ModeSwitch {

    /**
     * 是否仅 NL2SQL 模式 (跳过 Python/Report)
     */
    
    public static final String IS_ONLY_NL2SQL = "IS_ONLY_NL2SQL";
    /**
     * 是否启用人工复核
     */
    
    public static final String HUMAN_REVIEW_ENABLED = "HUMAN_REVIEW_ENABLED";
    
    /**
     * 人工反馈数据
     */
    public static final String HUMAN_FEEDBACK_DATA = "HUMAN_FEEDBACK_DATA";
}
