package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体知识源管理表 (支持文档、QA、FAQ)
 */
@Data
@TableName("agent_knowledge")
public class AgentKnowledgeEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 关联的智能体ID */
    private Integer agentId;

    /** 知识标题 */
    private String title;

    /** 知识类型: DOCUMENT-文档, QA-问答, FAQ-常见问题 */
    private String type;

    /** 问题 (仅当 type 为 QA/FAQ 时使用) */
    private String question;

    /** 知识内容 */
    private String content;

    /** 是否召回: 1=召回, 0=不召回 */
    private Integer isRecall;

    /** 向量化状态：PENDING, PROCESSING, COMPLETED, FAILED */
    private String embeddingStatus;

    /** 操作失败的错误信息 */
    private String errorMsg;

    /** 上传时的原始文件名 */
    private String sourceFilename;

    /** 文件在服务器上的物理存储路径 */
    private String filePath;

    /** 文件大小 (字节) */
    private Long fileSize;

    /** 文件类型 (pdf, md, doc 等) */
    private String fileType;

    /** 分块策略: token, recursive, sentence, semantic */
    private String splitterType;

    /** 0=物理资源未清理, 1=已清理 */
    private Integer isResourceCleaned;

    @TableLogic
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
