package com.liang.data.agent.service.agentdatasource.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 字段参与分析状态批量更新参数。
 */
@Data
public class ColumnAnalyticUpdateDTO {

    @NotEmpty(message = "字段列表不能为空")
    private List<String> columnNames;

    @NotNull(message = "参与分析状态不能为空")
    private Boolean isAnalytic;
}
