package com.liang.data.agent.service.knowledge.chunk.vo;

import lombok.Data;

/**
 * 知识分块保存请求。
 */
@Data
public class KnowledgeChunkUpdateRequest {
    private String name;
    private String content;
    private Integer contentVersion;
    private Boolean manualNameChanged;
}
