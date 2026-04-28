package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.UserPromptConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Prompt 配置表 Mapper
 */
@Mapper
public interface UserPromptConfigMapper extends BaseMapper<UserPromptConfigEntity> {
}
