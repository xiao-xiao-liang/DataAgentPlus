package com.liang.data.agent.service.datasource.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 数据源管理 DTO
 * <p>
 * 用于创建和更新数据源，包含 JSR303 校验注解。
 * 创建时 password 必传；更新时 password 为 null 表示不修改密码。
 * </p>
 */
@Data
@Accessors(chain = true)
public class DatasourceDTO {

    /**
     * 数据源ID，更新时必传
     */
    private Integer id;

    /**
     * 数据源名称
     */
    @NotBlank(message = "数据源名称不能为空")
    private String name;

    /**
     * 数据源类型，如 "mysql"、"postgresql"
     */
    @NotBlank(message = "数据源类型不能为空")
    private String type;

    /**
     * 主机地址
     */
    @NotBlank(message = "主机地址不能为空")
    private String host;

    /**
     * 端口号，取值范围 1~65535
     */
    @NotNull(message = "端口号不能为空")
    @Min(value = 1, message = "端口号最小为1")
    @Max(value = 65535, message = "端口号最大为65535")
    private Integer port;

    /**
     * 数据库名称，可选
     */
    private String databaseName;

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码
     * <p>创建时必传；更新时为 null 表示不修改密码</p>
     */
    private String password;

    /**
     * 完整连接URL，可选，填写后将覆盖 host/port/databaseName 拼接的 URL
     */
    private String connectionUrl;

    /**
     * 描述信息
     */
    private String description;
}
