package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.ChatWorkflowRunEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天工作流运行快照 Mapper。
 */
@Mapper
public interface ChatWorkflowRunMapper extends BaseMapper<ChatWorkflowRunEntity> {
}
