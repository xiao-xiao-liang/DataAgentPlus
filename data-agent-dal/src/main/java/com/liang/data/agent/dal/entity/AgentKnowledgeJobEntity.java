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
 * 智能体知识处理任务表。
 *
 * <p>记录知识文档上传向量化、删除清理等异步任务的状态、重试次数和锁信息。</p>
 */
@Data
@Builder
@TableName("agent_knowledge_job")
@NoArgsConstructor
@AllArgsConstructor
public class AgentKnowledgeJobEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属知识源 ID */
    private Integer knowledgeId;

    /** 关联的智能体 ID */
    private Integer agentId;

    /** 用户 ID */
    private String userId;

    /** 任务类型：UPLOAD_VECTORIZE、DELETE_CLEANUP */
    private String jobType;

    /** 任务状态：PENDING、RUNNING、RETRYING、SUCCESS、FAILED */
    private String status;

    /** 当前重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetryCount;

    /** 下次可重试时间 */
    private LocalDateTime nextRetryTime;

    /** 锁持有者 */
    private String lockedBy;

    /** 锁过期时间 */
    private LocalDateTime lockedUntil;

    /** 失败错误信息 */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
