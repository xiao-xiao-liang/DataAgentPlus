package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务知识表
 */
@Data
@TableName("business_knowledge")
public class BusinessKnowledgeEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 关联的智能体ID */
    private Integer agentId;

    /** 业务名词 */
    private String businessTerm;

    /** 描述 */
    private String description;

    /** 同义词, 逗号分隔 */
    private String synonyms;

    /** 是否召回：0-不召回, 1-召回 */
    private Integer isRecall;

    /** 向量化状态：PENDING, PROCESSING, COMPLETED, FAILED */
    private String embeddingStatus;

    /** 操作失败的错误信息 */
    private String errorMsg;

    @TableLogic
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
