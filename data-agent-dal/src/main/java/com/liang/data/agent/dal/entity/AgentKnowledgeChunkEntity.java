package com.liang.data.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体知识分块表。
 *
 * <p>记录文档切分后的文本片段、向量化状态和向量存储标识。</p>
 */
@Data
@TableName("agent_knowledge_chunk")
public class AgentKnowledgeChunkEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属知识源 ID */
    private Integer knowledgeId;

    /** 分块业务 ID */
    private String chunkId;

    /** 分块顺序，从 0 开始 */
    private Integer chunkOrder;

    /** 分块文本内容 */
    private String content;

    /** 分块文本长度 */
    private Integer contentLength;

    /** 分块元数据 JSON */
    private String metadata;

    /** 向量存储中的文档 ID */
    private String embeddingId;

    /** 分块策略 */
    private String splitterType;

    /** 状态：SKIP_EMBEDDING, VECTOR_STORED, FAILED */
    private String status;

    /** 是否跳过向量化：0=不跳过, 1=跳过 */
    private Integer skipEmbedding;

    /** 操作失败的错误信息 */
    private String errorMsg;

    @TableLogic
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
