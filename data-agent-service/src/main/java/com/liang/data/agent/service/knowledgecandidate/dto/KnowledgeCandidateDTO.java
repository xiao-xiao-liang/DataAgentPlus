package com.liang.data.agent.service.knowledgecandidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class KnowledgeCandidateDTO {

    @NotNull(message = "智能体ID不能为空")
    private Integer agentId;

    private Integer datasourceId;

    private String sessionId;

    private String threadId;

    @NotBlank(message = "原始问题不能为空")
    private String sourceQuestion;

    private String clarificationQuestion;

    private String userAnswer;

    @NotBlank(message = "候选知识内容不能为空")
    private String normalizedContent;

    @NotBlank(message = "候选知识类型不能为空")
    private String candidateType;

    @NotBlank(message = "候选知识标题不能为空")
    private String title;

    private String scope;

    private String status;

    private BigDecimal confidenceScore;
}
