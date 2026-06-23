package com.liang.data.agent.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流运行状态视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRunVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 运行记录主键 */
    private Long id;

    /** 单次工作流运行ID */
    private String runId;

    /** OpenTelemetry追踪ID */
    private String traceId;

    /** 会话 ID */
    private String sessionId;

    /** 智能体 ID */
    private Integer agentId;

    /** 用户 ID */
    private Long userId;

    /** 用户原始问题 */
    private String query;

    /** 运行状态 */
    private String status;

    /** 最近完成的节点名称 */
    private String lastNodeName;

    /** 下一节点名称 */
    private String nextNodeName;

    /** 是否可以继续执行 */
    private boolean resumable;

    /** 中断或失败原因 */
    private String interruptReason;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 运行耗时毫秒 */
    private Long durationMs;

    /** 失败节点名称 */
    private String failedNodeName;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
