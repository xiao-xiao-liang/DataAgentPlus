package com.liang.data.agent.service.knowledge.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 知识任务队列视图对象。
 */
@Data
@Accessors(chain = true)
public class KnowledgeJobQueueVO {

    /** 知识任务 ID */
    private Long jobId;

    /** 任务状态 */
    private String status;

    /** 当前任务前方等待任务数 */
    private long aheadTaskCount;

    /** 当前任务前方等待用户数 */
    private long aheadUserCount;
}
