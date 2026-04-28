package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体预设问题表
 */
@Data
@TableName("agent_preset_question")
public class AgentPresetQuestionEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 智能体ID */
    private Integer agentId;

    /** 预设问题内容 */
    private String question;

    /** 排序顺序 */
    private Integer sortOrder;

    /** 是否启用：0-禁用, 1-启用 */
    private Integer isActive;

    @TableLogic
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
