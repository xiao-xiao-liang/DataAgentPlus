package com.liang.data.agent.workflow.dto.node;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 意图识别节点的 LLM 输出
 */
@Data
@NoArgsConstructor
public class IntentRecognitionOutputDTO {

    /**
     * 意图分类结果: "《闲聊或无关指令》" 或 "《可能的数据分析请求》"
     */
    @JsonProperty("classification")
    @JsonPropertyDescription("意图分类结果，值为：《闲聊或无关指令》或《可能的数据分析请求》")
    private String classification;
}
