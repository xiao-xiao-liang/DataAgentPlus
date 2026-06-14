package com.liang.data.agent.service.agent.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 智能体视图对象
 *
 * <p>不包含 apiKey（敏感字段）和 delFlag（内部字段）</p>
 */
@Data
@Accessors(chain = true)
public class AgentVO {

    /** 智能体ID */
    private Integer id;

    /** 智能体名称 */
    private String name;

    /** 智能体描述 */
    private String description;

    /** 头像 URL */
    private String avatar;

    /** 状态：draft-待发布, published-已发布, offline-已下线 */
    private String status;

    /** API Key 是否启用：0-禁用, 1-启用 */
    private Integer apiKeyEnabled;

    /** 自定义系统提示词 */
    private String prompt;

    /** 分类 */
    private String category;

    /** 标签 */
    private String tags;

    /** SQL 查询最大返回行数 */
    private Integer maxResultRows;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
