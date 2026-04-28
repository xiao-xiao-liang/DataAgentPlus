package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源表
 */
@Data
@TableName("datasource")
public class DatasourceEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 数据源名称 */
    private String name;

    /** 数据源类型：mysql, postgresql */
    private String type;

    /** 主机地址 */
    private String host;

    /** 端口号 */
    private Integer port;

    /** 数据库名称 */
    private String databaseName;

    /** 用户名 */
    private String username;

    /** 密码（加密存储） */
    private String password;

    /** 完整连接URL */
    private String connectionUrl;

    /** 状态：active-启用, inactive-禁用 */
    private String status;

    /** 连接测试状态：success, failed, unknown */
    private String testStatus;

    /** 描述 */
    private String description;

    /** 创建者ID */
    private Long creatorId;

    @TableLogic
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
