package com.liang.data.agent.workflow.dto.node;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 证据召回查询重写的 LLM 输出
 */
@Getter
@Setter
@NoArgsConstructor
public class EvidenceQueryRewriteDTO {

    /**
     * 重写后的完整句子（消除了多轮对话中的指代词）
     */
    @JsonProperty("standalone_query")
    @JsonPropertyDescription("重写后的完整句子")
    private String standaloneQuery;
}
