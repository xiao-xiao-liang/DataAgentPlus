package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户 Prompt 配置表
 *
 * <p>主键为 UUID 字符串, 非自增</p>
 */
@Data
@TableName("user_prompt_config")
public class UserPromptConfigEntity {

    /** 配置ID (UUID) */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 配置名称 */
    private String name;

    /** Prompt 类型 (如 report-generator, planner 等) */
    private String promptType;

    /** 关联的智能体ID, 为空表示全局配置 */
    private Integer agentId;

    /** 用户自定义系统 Prompt 内容 */
    private String systemPrompt;

    /** 是否启用：0-禁用, 1-启用 */
    private Integer enabled;

    /** 配置描述 */
    private String description;

    /** 优先级, 数字越大优先级越高 */
    private Integer priority;

    /** 显示顺序, 数字越小越靠前 */
    private Integer displayOrder;

    /** 创建者 */
    private String creator;

    @TableLogic
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
