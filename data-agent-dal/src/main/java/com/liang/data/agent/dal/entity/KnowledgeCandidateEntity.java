package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_candidate")
public class KnowledgeCandidateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer agentId;

    private Integer datasourceId;

    private String sessionId;

    private String threadId;

    private String sourceQuestion;

    private String clarificationQuestion;

    private String userAnswer;

    private String normalizedContent;

    private String candidateType;

    private String title;

    private String scope;

    private String status;

    private BigDecimal confidenceScore;

    private Long reviewerId;

    private String reviewComment;

    private String publishedTargetType;

    private Long publishedTargetId;

    @TableLogic
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
