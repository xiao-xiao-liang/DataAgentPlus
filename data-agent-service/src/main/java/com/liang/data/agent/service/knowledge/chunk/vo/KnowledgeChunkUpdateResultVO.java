package com.liang.data.agent.service.knowledge.chunk.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 知识分块变更结果。
 */
@Data
@AllArgsConstructor
public class KnowledgeChunkUpdateResultVO {
    private KnowledgeChunkDetailVO detail;
    private boolean messageSubmitted;
}
