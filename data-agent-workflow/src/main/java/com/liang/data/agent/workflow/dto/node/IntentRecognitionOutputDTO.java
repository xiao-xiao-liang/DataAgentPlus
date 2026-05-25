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

    /**
     * 闲聊友好引导回复（当分类为《闲聊或无关指令》时有值，否则为 null）
     */
    @JsonProperty("reply")
    @JsonPropertyDescription("当分类为《闲聊或无关指令》时，对此闲聊进行礼貌回应并引导用户上传数据集的中文回复；当分类为《可能的数据分析请求》时，此字段为 null")
    private String reply;
}
