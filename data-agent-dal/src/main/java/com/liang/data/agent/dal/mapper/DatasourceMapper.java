package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.DatasourceEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源表 Mapper
 */
@Mapper
public interface DatasourceMapper extends BaseMapper<DatasourceEntity> {
}
