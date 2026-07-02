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
 * 模型网关调用尝试明细实体。
 *
 * <p>用于记录一次模型网关调用中的单次尝试结果和错误摘要，不保存完整 Prompt、完整响应或密钥类敏感信息。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_gateway_attempt")
public class ModelGatewayAttemptEntity {

    /** 尝试记录主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模型网关尝试ID */
    private String attemptId;

    /** 模型网关调用ID */
    private String invocationId;

    /** 尝试序号 */
    private Integer attemptNo;

    /** 模型厂商 */
    private String provider;

    /** 模型名称 */
    private String model;

    /** 尝试状态 */
    private String status;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 耗时毫秒数 */
    private Long durationMs;

    /** HTTP状态码 */
    private Integer httpStatus;

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
