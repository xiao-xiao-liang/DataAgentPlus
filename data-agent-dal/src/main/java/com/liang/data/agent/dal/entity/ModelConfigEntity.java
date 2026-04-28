package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型配置表
 */
@Data
@TableName("model_config")
public class ModelConfigEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 厂商标识 (方便前端展示) */
    private String provider;

    /** 模型 Base URL */
    private String baseUrl;

    /** API 密钥 */
    private String apiKey;

    /** 模型名称 */
    private String modelName;

    /** 模型类型: CHAT / EMBEDDING */
    private String modelType;

    /** 温度参数 */
    private BigDecimal temperature;

    /** 最大输出 Token 数 */
    private Integer maxTokens;

    /** Chat 模型附加路径 */
    private String completionsPath;

    /** Embedding 模型附加路径 */
    private String embeddingsPath;

    /** 是否激活：0-禁用, 1-启用 */
    private Integer isActive;

    /** 是否启用代理：0-禁用, 1-启用 */
    private Integer proxyEnabled;

    /** 代理主机地址 */
    private String proxyHost;

    /** 代理端口 */
    private Integer proxyPort;

    /** 代理用户名 */
    private String proxyUsername;

    /** 代理密码 */
    private String proxyPassword;

    @TableLogic
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
