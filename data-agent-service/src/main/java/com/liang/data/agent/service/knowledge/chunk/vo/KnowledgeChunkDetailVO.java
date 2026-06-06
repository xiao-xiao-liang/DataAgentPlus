package com.liang.data.agent.service.knowledge.chunk.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 知识分块编辑详情视图对象。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class KnowledgeChunkDetailVO extends KnowledgeChunkOutlineVO {
    private Integer knowledgeId;
    private String content;
    private Boolean nameLocked;
    private Integer retryCount;
    private String errorMsg;
    private LocalDateTime vectorProcessingStartedAt;
    private Long vectorTaskTimeoutSeconds;
}
