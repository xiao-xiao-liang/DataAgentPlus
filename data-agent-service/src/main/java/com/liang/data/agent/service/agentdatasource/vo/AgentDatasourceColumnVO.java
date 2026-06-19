package com.liang.data.agent.service.agentdatasource.vo;

import lombok.Data;

/**
 * 智能体数据源字段配置视图对象。
 */
@Data
public class AgentDatasourceColumnVO {

    private String columnName;
    private String dataType;
    private String comment;
    private Boolean nullable;
    private Boolean primaryKey;
    private Boolean isAnalytic;
}
