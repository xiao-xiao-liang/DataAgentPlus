package com.liang.data.agent.service.knowledgecandidate.dto;

import lombok.Data;

@Data
public class PublishKnowledgeCandidateDTO {

    private String targetType;

    private Long reviewerId;

    private String reviewComment;
}
