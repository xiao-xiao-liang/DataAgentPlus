package com.liang.data.agent.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liang.data.agent.dal.entity.ModelConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 模型配置表 Mapper
 */
@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfigEntity> {

    /**
     * 查询指定类型的激活配置
     *
     * @param modelType 模型类型, 如 "CHAT" / "EMBEDDING"
     * @return 激活的配置, 无则返回 null
     */
    @Select("SELECT * FROM model_config WHERE model_type = #{modelType} AND is_active = 1 AND del_flag = 0 LIMIT 1")
    ModelConfigEntity selectActiveByType(@Param("modelType") String modelType);
}
