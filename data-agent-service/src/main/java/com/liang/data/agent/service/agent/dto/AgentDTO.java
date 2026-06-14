package com.liang.data.agent.service.agent.dto;

import com.liang.data.agent.common.constant.SqlQueryLimitConstant;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    /** SQL 查询最大返回行数 */
    @Min(value = 1, message = "最大返回行数不能小于 1")
    @Max(value = SqlQueryLimitConstant.MAX_RESULT_ROWS, message = "最大返回行数不能超过 1000")
    private Integer maxResultRows = SqlQueryLimitConstant.DEFAULT_MAX_RESULT_ROWS;
}
