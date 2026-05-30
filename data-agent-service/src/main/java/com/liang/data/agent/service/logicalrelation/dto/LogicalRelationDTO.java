package com.liang.data.agent.service.logicalrelation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Logical relation create/update payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogicalRelationDTO {

    private Integer id;

    private Integer datasourceId;

    @NotBlank(message = "源表名不能为空")
    private String sourceTableName;

    @NotBlank(message = "源字段不能为空")
    private String sourceColumnName;

    @NotBlank(message = "目标表名不能为空")
    private String targetTableName;

    @NotBlank(message = "目标字段名不能为空")
    private String targetColumnName;

    private String relationType;

    private String description;
}
