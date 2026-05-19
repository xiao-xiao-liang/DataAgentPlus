package com.liang.data.agent.workflow.dto.node;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 查询增强节点的 LLM 输出
 */
@Data
@NoArgsConstructor
public class QueryEnhanceOutputDTO {

    /**
     * 经 LLM 重写后的规范化查询 (含绝对时间和解析后的业务术语)
     */
    @JsonProperty("canonical_query")
    @JsonPropertyDescription("对用户最终意图的单一、清晰的重写，包含绝对时间和解析后的业务术语")
    private String canonicalQuery;

    /**
     * 基于 canonicalQuery 的扩展查询列表
     */
    @JsonProperty("expanded_queries")
    @JsonPropertyDescription("基于完整信息的扩展问题表述")
    private List<String> expandedQueries;
}
