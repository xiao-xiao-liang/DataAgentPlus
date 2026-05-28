package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能体表
 */
@Data
@Builder
@TableName("agent")
@NoArgsConstructor
@AllArgsConstructor
public class AgentEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 智能体名称 */
    private String name;

    /** 智能体描述 */
    private String description;

    /** 头像URL */
    private String avatar;

    /** 状态：draft-待发布, published-已发布, offline-已下线 */
    private String status;

    /** 访问 API Key */
    private String apiKey;

    /** API Key 是否启用：0-禁用, 1-启用 */
    private Integer apiKeyEnabled;

    /** 自定义 Prompt 配置 */
    private String prompt;

    /** 分类 */
    private String category;

    /** 管理员ID */
    private Long adminId;

    /** 标签, 逗号分隔 */
    private String tags;

    /** 逻辑删除：0-未删除, 1-已删除 */
    @TableLogic
    private Integer delFlag;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
