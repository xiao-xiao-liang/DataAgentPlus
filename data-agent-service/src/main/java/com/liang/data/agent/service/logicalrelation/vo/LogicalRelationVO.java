package com.liang.data.agent.service.logicalrelation.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Logical relation response view.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogicalRelationVO {

    private Integer id;

    private Integer datasourceId;

    private String sourceTableName;

    private String sourceColumnName;

    private String targetTableName;

    private String targetColumnName;

    private String relationType;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
