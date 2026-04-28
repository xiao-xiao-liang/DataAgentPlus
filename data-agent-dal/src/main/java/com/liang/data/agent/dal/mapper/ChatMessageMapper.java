package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息表 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
}
