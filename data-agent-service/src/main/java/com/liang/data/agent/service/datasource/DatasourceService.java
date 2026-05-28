package com.liang.data.agent.service.datasource;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liang.data.agent.dal.connector.bo.ColumnInfoBO;
import com.liang.data.agent.dal.connector.bo.ResultSetBO;
import com.liang.data.agent.dal.connector.bo.TableInfoBO;
import com.liang.data.agent.dal.entity.DatasourceEntity;
import com.liang.data.agent.service.datasource.dto.DatasourceDTO;
import com.liang.data.agent.service.datasource.vo.DatasourceVO;

import java.util.List;

/**
 * 数据源管理服务接口
 */
public interface DatasourceService extends IService<DatasourceEntity> {

    /**
     * 创建数据源
     *
     * @param dto 数据源信息
     * @return 新创建的数据源ID
     */
    Integer create(DatasourceDTO dto);

    /**
     * 更新数据源
     *
     * @param dto 数据源信息（id 必传，password 为 null 时不修改密码）
     */
    void update(DatasourceDTO dto);

    /**
     * 删除数据源（逻辑删除）
     *
     * @param id 数据源ID
     */
    void delete(Integer id);

    /**
     * 获取数据源详情
     *
     * @param id 数据源ID
     * @return 数据源视图对象
     */
    DatasourceVO getById(Integer id);

    /**
     * 获取所有数据源列表
     *
     * @return 数据源视图对象列表
     */
    List<DatasourceVO> findAll();

    /**
     * 根据数据源ID测试连接
     *
     * @param id 已保存的数据源ID
     * @return 连接测试结果，null 表示成功，否则返回错误信息
     */
    String testConnection(Integer id);

    /**
     * 通过 DTO 测试连接（创建前预检）
     *
     * @param dto 数据源配置信息
     * @return 连接测试结果，null 表示成功，否则返回错误信息
     */
    String testConnectionByDto(DatasourceDTO dto);

    /**
     * 获取数据源下的表列表
     *
     * @param id 数据源ID
     * @return 表信息列表
     */
    List<TableInfoBO> getTables(Integer id);

    /**
     * 获取指定表的列信息
     *
     * @param id        数据源ID
     * @param tableName 表名
     * @return 列信息列表
     */
    List<ColumnInfoBO> getColumns(Integer id, String tableName);

    /**
     * 获取指定表的数据采样预览 (默认前 10 行)
     *
     * @param id        数据源ID
     * @param tableName 表名
     * @return 结果集对象
     */
    ResultSetBO getTablePreview(Integer id, String tableName);
}
