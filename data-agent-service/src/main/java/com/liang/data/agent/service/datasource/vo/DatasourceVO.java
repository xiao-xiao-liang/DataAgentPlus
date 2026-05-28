package com.liang.data.agent.service.datasource.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 数据源视图对象
 * <p>
 * 用于接口返回，隐藏 password 和 delFlag 等敏感/内部字段。
 * </p>
 */
@Data
@Accessors(chain = true)
public class DatasourceVO {

    /**
     * 数据源ID
     */
    private Integer id;

    /**
     * 数据源名称
     */
    private String name;

    /**
     * 数据源类型
     */
    private String type;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 端口号
     */
    private Integer port;

    /**
     * 数据库名称
     */
    private String databaseName;

    /**
     * 用户名
     */
    private String username;

    /**
     * 完整连接URL
     */
    private String connectionUrl;

    /**
     * 状态：active-启用, inactive-禁用
     */
    private String status;

    /**
     * 连接测试状态：success, failed, unknown
     */
    private String testStatus;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建者ID
     */
    private Long creatorId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
