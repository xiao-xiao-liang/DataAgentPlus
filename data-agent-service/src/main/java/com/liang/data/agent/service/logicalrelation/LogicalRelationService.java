package com.liang.data.agent.service.logicalrelation;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liang.data.agent.dal.entity.LogicalRelationEntity;
import com.liang.data.agent.service.logicalrelation.dto.LogicalRelationDTO;
import com.liang.data.agent.service.logicalrelation.vo.LogicalRelationVO;

import java.util.List;

/**
 * 逻辑外键关系管理服务接口
 * <p>
 * 提供逻辑外键实体的增删改查及关联数据源的查询操作
 * </p>
 */
public interface LogicalRelationService extends IService<LogicalRelationEntity> {

    /**
     * 根据数据源 ID 查询逻辑外键关系列表
     *
     * @param datasourceId 数据源 ID
     * @return 逻辑外键关系视图对象（VO）列表
     */
    List<LogicalRelationVO> listByDatasource(Integer datasourceId);

    /**
     * 创建新的逻辑外键关系
     *
     * @param dto 逻辑外键关系创建信息传输对象
     * @return 新创建的逻辑外键关系 ID
     */
    Integer create(LogicalRelationDTO dto);

    /**
     * 更新指定数据源下的逻辑外键关系信息
     *
     * @param datasourceId 数据源 ID
     * @param dto          逻辑外键关系更新信息传输对象，其中通常包含需要修改的逻辑外键关系 ID
     */
    void update(Integer datasourceId, LogicalRelationDTO dto);

    /**
     * 删除指定数据源下的特定逻辑外键关系
     *
     * @param datasourceId 数据源 ID
     * @param id           逻辑外键关系 ID
     */
    void delete(Integer datasourceId, Integer id);
}
