package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.ModelGatewayAttemptEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型网关调用尝试明细 Mapper。
 *
 * <p>用于读写模型网关调用尝试记录，不涉及完整 Prompt、完整响应或密钥类敏感信息。</p>
 */
@Mapper
public interface ModelGatewayAttemptMapper extends BaseMapper<ModelGatewayAttemptEntity> {
}
