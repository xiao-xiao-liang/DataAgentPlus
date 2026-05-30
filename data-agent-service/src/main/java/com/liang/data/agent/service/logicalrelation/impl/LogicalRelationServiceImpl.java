package com.liang.data.agent.service.logicalrelation.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.common.util.BeanUtil;
import com.liang.data.agent.dal.entity.LogicalRelationEntity;
import com.liang.data.agent.dal.mapper.LogicalRelationMapper;
import com.liang.data.agent.service.logicalrelation.LogicalRelationService;
import com.liang.data.agent.service.logicalrelation.dto.LogicalRelationDTO;
import com.liang.data.agent.service.logicalrelation.vo.LogicalRelationVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 逻辑外键管理服务实现类
 */
@Service
public class LogicalRelationServiceImpl extends ServiceImpl<LogicalRelationMapper, LogicalRelationEntity> implements LogicalRelationService {

    @Override
    public List<LogicalRelationVO> listByDatasource(Integer datasourceId) {
        return lambdaQuery()
                .eq(LogicalRelationEntity::getDatasourceId, datasourceId)
                .orderByDesc(LogicalRelationEntity::getUpdateTime)
                .list()
                .stream()
                .map(entity -> BeanUtil.copyProperties(entity, LogicalRelationVO.class))
                .toList();
    }

    @Override
    public Integer create(LogicalRelationDTO dto) {
        LogicalRelationEntity entity = new LogicalRelationEntity();
        fillEntity(entity, dto);
        save(entity);
        return entity.getId();
    }

    @Override
    public void update(Integer datasourceId, LogicalRelationDTO dto) {
        if (dto.getId() == null) {
            throw new ServiceException("逻辑关系 ID 不能为空");
        }
        LogicalRelationEntity entity = getRelation(datasourceId, dto.getId());

        dto.setDatasourceId(datasourceId);
        fillEntity(entity, dto);
        updateById(entity);
    }

    @Override
    public void delete(Integer datasourceId, Integer id) {
        getRelation(datasourceId, id);
        removeById(id);
    }

    private LogicalRelationEntity getRelation(Integer datasourceId, Integer id) {
        return lambdaQuery()
                .eq(LogicalRelationEntity::getDatasourceId, datasourceId)
                .eq(LogicalRelationEntity::getId, id)
                .oneOpt()
                .orElseThrow(() -> new ServiceException("未找到逻辑外键配置, id=" + id));
    }

    private void fillEntity(LogicalRelationEntity entity, LogicalRelationDTO dto) {
        BeanUtil.copyProperties(dto, entity);
        if (Objects.isNull(entity.getDelFlag())) {
            entity.setDelFlag(0);
        }
    }
}
