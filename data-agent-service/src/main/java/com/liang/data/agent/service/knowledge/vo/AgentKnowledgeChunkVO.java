package com.liang.data.agent.service.knowledge.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 智能体知识文件切分片段视图对象。
 */
@Data
@Accessors(chain = true)
public class AgentKnowledgeChunkVO {

    private String id;
    private Integer knowledgeId;
    private Integer seq;
    private String content;
    private Integer length;
    private String splitterType;
    private String status;
    private String embeddingId;
}
