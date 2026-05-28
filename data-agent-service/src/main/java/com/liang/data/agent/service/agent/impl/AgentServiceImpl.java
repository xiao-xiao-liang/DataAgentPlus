package com.liang.data.agent.service.agent.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.common.util.BeanUtil;
import com.liang.data.agent.dal.entity.AgentEntity;
import com.liang.data.agent.dal.mapper.AgentMapper;
import com.liang.data.agent.service.agent.AgentService;
import com.liang.data.agent.service.agent.dto.AgentDTO;
import com.liang.data.agent.service.agent.vo.AgentVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * 智能体管理服务实现
 * 继承 MyBatis-Plus 的 ServiceImpl 以提供更强大的 lambda 链式操作能力
 * 优化：移除所有单表 DML 操作上多余的 @Transactional 注解，减小事务开销，提升连接池在高并发下的吞吐率
 *
 * @author 资深Java架构师
 */
@Slf4j
@Service
public class AgentServiceImpl extends ServiceImpl<AgentMapper, AgentEntity> implements AgentService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_OFFLINE = "offline";

    @Override
    public Integer create(AgentDTO dto) {
        AgentEntity entity = BeanUtil.copyProperties(dto, AgentEntity.class);
        entity.setStatus(STATUS_DRAFT);
        this.save(entity);
        log.info("创建智能体成功, id={}, name={}", entity.getId(), entity.getName());
        return entity.getId();
    }

    @Override
    public void update(AgentDTO dto) {
        AgentEntity entity = getEntityById(dto.getId());
        BeanUtil.copyProperties(dto, entity);
        this.updateById(entity);
        log.info("更新智能体成功, id={}", entity.getId());
    }

    @Override
    public void delete(Integer id) {
        getEntityById(id);
        this.removeById(id);
        log.info("删除智能体成功, id={}", id);
    }

    @Override
    public AgentVO getById(Integer id) {
        AgentEntity entity = getEntityById(id);
        return toVO(entity);
    }

    @Override
    public List<AgentVO> list(String keyword, String status) {
        List<AgentEntity> entities = this.lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(AgentEntity::getName, keyword)
                        .or()
                        .like(AgentEntity::getDescription, keyword)
                )
                .eq(StringUtils.hasText(status), AgentEntity::getStatus, status)
                .orderByDesc(AgentEntity::getCreateTime)
                .list();
        return entities.stream().map(this::toVO).toList();
    }

    @Override
    public void publish(Integer id) {
        AgentEntity entity = getEntityById(id);
        String currentStatus = entity.getStatus();
        if (!STATUS_DRAFT.equals(currentStatus) && !STATUS_OFFLINE.equals(currentStatus)) {
            throw new ServiceException("当前状态不允许发布，当前状态: " + currentStatus);
        }
        
        entity.setStatus(STATUS_PUBLISHED);
        this.updateById(entity);
        log.info("发布智能体成功, id={}", id);
    }

    @Override
    public void offline(Integer id) {
        AgentEntity entity = getEntityById(id);
        String currentStatus = entity.getStatus();
        if (!STATUS_PUBLISHED.equals(currentStatus)) {
            throw new ServiceException("当前状态不允许下线，当前状态: " + currentStatus);
        }
        
        entity.setStatus(STATUS_OFFLINE);
        this.updateById(entity);
        log.info("下线智能体成功, id={}", id);
    }

    @Override
    public String getApiKey(Integer id) {
        AgentEntity entity = getEntityById(id);
        return entity.getApiKey();
    }

    @Override
    public String generateApiKey(Integer id) {
        AgentEntity entity = getEntityById(id);
        if (StringUtils.hasText(entity.getApiKey())) {
            throw new ServiceException("该智能体已拥有 API Key，请使用重置功能");
        }
        
        String apiKey = UUID.randomUUID().toString().replace("-", "");
        entity.setApiKey(apiKey);
        this.updateById(entity);
        log.info("生成 API Key 成功, id={}", id);
        return apiKey;
    }

    @Override
    public String resetApiKey(Integer id) {
        AgentEntity entity = getEntityById(id);
        String apiKey = UUID.randomUUID().toString().replace("-", "");
        entity.setApiKey(apiKey);
        this.updateById(entity);
        log.info("重置 API Key 成功, id={}", id);
        return apiKey;
    }

    /**
     * 根据 ID 查询智能体实体，不存在则抛出异常
     *
     * @param id 智能体ID
     * @return 智能体实体
     */
    private AgentEntity getEntityById(Integer id) {
        return lambdaQuery().eq(AgentEntity::getId, id)
                .oneOpt()
                .orElseThrow(() -> new ServiceException("智能体不存在"));
    }

    /**
     * 实体转视图对象
     *
     * @param entity 智能体实体
     * @return 智能体视图对象
     */
    private AgentVO toVO(AgentEntity entity) {
        return BeanUtil.copyProperties(entity, AgentVO.class);
    }
}
