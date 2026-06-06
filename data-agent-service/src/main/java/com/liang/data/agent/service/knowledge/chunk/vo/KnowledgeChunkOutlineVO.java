package com.liang.data.agent.service.knowledge.chunk.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 知识分块大纲视图对象。
 */
@Data
@Accessors(chain = true)
public class KnowledgeChunkOutlineVO {
    private String id;
    private Integer seq;
    private String name;
    private Integer length;
    private Integer contentVersion;
    private Integer vectorVersion;
    private Integer vectorTaskVersion;
    private String vectorStatus;
    private LocalDateTime updateTime;
}
