package com.liang.data.agent.service.knowledge.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 智能体知识文件视图对象。
 */
@Data
@Accessors(chain = true)
public class AgentKnowledgeVO {

    private Integer id;
    private Integer agentId;
    private String title;
    private String type;
    private String content;
    private Integer isRecall;
    private String embeddingStatus;
    private String errorMsg;
    private String sourceFilename;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private String splitterType;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
