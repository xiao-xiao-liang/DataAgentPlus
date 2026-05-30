package com.liang.data.agent.workflow.dto.node;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ClarificationNormalizedDTO {

    private String term;

    private String normalizedDefinition;

    private String calculationRule;

    private String suggestedMemoryType;

    private List<String> synonyms = new ArrayList<>();

    private BigDecimal confidence;

    private String confirmationText;
}
