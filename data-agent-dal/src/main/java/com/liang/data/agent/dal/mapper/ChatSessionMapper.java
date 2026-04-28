package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.ChatSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天会话表 Mapper
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {
}
