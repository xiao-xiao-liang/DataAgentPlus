package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型网关调用主表实体。
 *
 * <p>用于记录一次模型网关调用的链路、模型、耗时、Token 和错误摘要，不保存完整 Prompt、完整响应或密钥类敏感信息。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_gateway_invocation")
public class ModelGatewayInvocationEntity {

    /** 调用记录主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模型网关调用ID */
    private String invocationId;

    /** 工作流运行ID */
    private String runId;

    /** 链路追踪ID */
    private String traceId;

    /** 会话ID */
    private String sessionId;

    /** 用户ID */
    private Long userId;

    /** 智能体ID */
    private Integer agentId;

    /** 租户ID */
    private String tenantId;

    /** 调用场景编码 */
    private String sceneCode;

    /** 调用模式 */
    private String callMode;

    /** 调用状态 */
    private String status;

    /** 模型厂商 */
    private String provider;

    /** 模型名称 */
    private String model;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 耗时毫秒数 */
    private Long durationMs;

    /** 输入Token数 */
    private Long inputTokens;

    /** 输出Token数 */
    private Long outputTokens;

    /** 总Token数 */
    private Long totalTokens;

    /** 错误码 */
    private String errorCode;

    /** 错误摘要 */
    private String errorMessage;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
