package com.liang.data.agent.workflow.dto.node;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MemoryCandidateOutputDTO {

    private Long candidateId;

    private String title;

    private String candidateType;

    private String scope;

    private String normalizedContent;

    private BigDecimal confidenceScore;

    private Boolean saveRequired;
}
