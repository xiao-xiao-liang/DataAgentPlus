package com.liang.data.agent.service.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 智能体创建/更新请求 DTO
 */
@Data
@Accessors(chain = true)
public class AgentDTO {

    /** 智能体ID，更新时必传 */
    private Integer id;

    /** 智能体名称 */
    @NotBlank(message = "智能体名称不能为空")
    @Size(max = 100, message = "智能体名称长度不能超过100个字符")
    private String name;

    /** 智能体描述 */
    @Size(max = 500, message = "智能体描述长度不能超过500个字符")
    private String description;

    /** 头像 URL */
    private String avatar;

    /** 自定义系统提示词 */
    private String prompt;

    /** 分类 */
    private String category;

    /** 标签，JSON 数组格式 */
    private String tags;
}
