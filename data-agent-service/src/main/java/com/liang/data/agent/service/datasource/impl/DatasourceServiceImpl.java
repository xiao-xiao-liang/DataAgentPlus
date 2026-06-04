package com.liang.data.agent.service.datasource.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liang.data.agent.common.exception.ServiceException;
import com.liang.data.agent.common.util.BeanUtil;
import com.liang.data.agent.dal.connector.DatabaseAccessor;
import com.liang.data.agent.dal.connector.bo.ColumnInfoBO;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import com.liang.data.agent.dal.connector.bo.ResultSetBO;
import com.liang.data.agent.dal.connector.bo.TableInfoBO;
import com.liang.data.agent.dal.entity.AgentEntity;
import com.liang.data.agent.dal.entity.DatasourceEntity;
import com.liang.data.agent.dal.mapper.DatasourceMapper;
import com.liang.data.agent.service.agent.vo.AgentVO;
import com.liang.data.agent.service.datasource.DatasourceService;
import com.liang.data.agent.service.datasource.dto.DatasourceDTO;
import com.liang.data.agent.service.datasource.vo.DatasourceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 数据源管理服务实现
 * 继承 MyBatis-Plus 的 ServiceImpl 以提供统一的 CRUD 与链式查询能力
 * 优化：移除所有单表 DML 操作上多余的 @Transactional 注解，减小声明式事务带来的数据库连接持有时间
 *
 * @author 资深Java架构师
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceServiceImpl extends ServiceImpl<DatasourceMapper, DatasourceEntity> implements DatasourceService {

    private final DatabaseAccessor databaseAccessor;

    @Override
    public Integer create(DatasourceDTO dto) {
        DatasourceEntity entity = BeanUtil.copyProperties(dto, DatasourceEntity.class);
        fillConnectionUrl(entity);
        entity.setStatus("active");
        refreshTestStatus(entity);
        this.save(entity);
        log.info("创建数据源成功, id={}, name={}", entity.getId(), entity.getName());
        return entity.getId();
    }

    @Override
    public void update(DatasourceDTO dto) {
        Integer id = Optional.ofNullable(dto.getId())
                .orElseThrow(() -> new ServiceException("更新数据源时ID不能为空"));

        DatasourceEntity existing = getEntityById(id);
        BeanUtil.copyProperties(dto, existing);
        fillConnectionUrl(existing);

        this.updateById(existing);
        log.info("更新数据源成功, id={}", id);
    }

    @Override
    public void delete(Integer id) {
        getEntityById(id);
        this.removeById(id);
        log.info("删除数据源成功, id={}", id);
    }

    @Override
    public DatasourceVO getById(Integer id) {
        DatasourceEntity entity = getEntityById(id);
        return toVO(entity);
    }

    public DatasourceEntity getEntityById(Integer id) {
        DatasourceEntity entity = super.getById(id);
        if (entity == null) {
            throw new ServiceException("数据源不存在, id=" + id);
        }
        return entity;
    }

    @Override
    public List<DatasourceVO> findAll() {
        List<DatasourceEntity> entities = this.list();
        return entities.stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public String testConnection(Integer id) {
        DatasourceEntity entity = getEntityById(id);
        log.info("测试数据源连接, id={}, type={}", id, entity.getType());
        String result = refreshTestStatus(entity);
        this.updateById(entity);
        return result;
    }

    @Override
    public String testConnectionByDto(DatasourceDTO dto) {
        DbConfigBO config = buildConfigFromDto(dto);
        log.info("通过DTO测试数据源连接, type={}, host={}", dto.getType(), dto.getHost());
        return databaseAccessor.ping(config);
    }

    @Override
    public List<TableInfoBO> getTables(Integer id) {
        DatasourceEntity entity = getEntityById(id);
        DbConfigBO config = DbConfigBO.from(entity);
        return databaseAccessor.showTables(config, null);
    }

    @Override
    public List<ColumnInfoBO> getColumns(Integer id, String tableName) {
        DatasourceEntity entity = getEntityById(id);
        DbConfigBO config = DbConfigBO.from(entity);
        return databaseAccessor.showColumns(config, tableName);
    }

    // ==================== 私有转换方法 ====================

    /**
     * 将 Entity 转换为 VO（隐藏 password 和 delFlag）
     *
     * @param entity 数据源实体
     * @return 数据源视图对象
     */
    private DatasourceVO toVO(DatasourceEntity entity) {
        return BeanUtil.copyProperties(entity, DatasourceVO.class);
    }

    /**
     * 将 DTO 转换为 Entity
     *
     * @param dto 数据源 DTO
     * @return 数据源实体
     */
    private DatasourceEntity toEntity(DatasourceDTO dto) {
        return BeanUtil.copyProperties(dto, DatasourceEntity.class);
    }

    /**
     * 补齐数据源连接 URL。
     *
     * @param entity 数据源实体
     */
    private void fillConnectionUrl(DatasourceEntity entity) {
        if (entity.getConnectionUrl() == null || entity.getConnectionUrl().isBlank()) {
            entity.setConnectionUrl(DbConfigBO.buildJdbcUrl(
                    entity.getType(),
                    entity.getHost(),
                    entity.getPort(),
                    Optional.ofNullable(entity.getDatabaseName()).orElse("")
            ));
        }
    }

    /**
     * 刷新数据源连接测试状态。
     *
     * @param entity 数据源实体
     * @return 连接测试结果，null 表示成功
     */
    private String refreshTestStatus(DatasourceEntity entity) {
        String result = databaseAccessor.ping(DbConfigBO.from(entity));
        entity.setTestStatus(result == null ? "success" : "failed");
        return result;
    }

    /**
     * 从 DTO 构建数据库连接配置（用于创建前预检测试连接）
     *
     * @param dto 数据源 DTO
     * @return 数据库连接配置
     */
    private DbConfigBO buildConfigFromDto(DatasourceDTO dto) {
        String url = dto.getConnectionUrl();
        if (url == null || url.isBlank()) {
            url = DbConfigBO.buildJdbcUrl(dto.getType(), dto.getHost(), dto.getPort(),
                    Optional.ofNullable(dto.getDatabaseName()).orElse(""));
        }
        return new DbConfigBO(
                null,
                url,
                dto.getUsername(),
                dto.getPassword(),
                dto.getType(),
                DbConfigBO.extractSchemaName(dto.getType(), dto.getDatabaseName())
        );
    }

    @Override
    public ResultSetBO getTablePreview(Integer id, String tableName) {
        DatasourceEntity entity = getEntityById(id);
        DbConfigBO config = DbConfigBO.from(entity);
        String cleanTableName = tableName.replace("`", "");
        String sql = String.format("SELECT * FROM `%s` LIMIT 10", cleanTableName);
        log.info("执行物理数据表采样, id={}, tableName={}, sql={}", id, cleanTableName, sql);
        return databaseAccessor.executeSql(config, sql);
    }
}
