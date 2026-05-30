package com.liang.data.agent.service.knowledgecandidate.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class KnowledgeCandidateVO {

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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
