package com.liang.data.agent.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.liang.data.agent.common.result.Result;
import com.liang.data.agent.common.result.Results;
import com.liang.data.agent.dal.connector.bo.ColumnInfoBO;
import com.liang.data.agent.dal.connector.bo.ResultSetBO;
import com.liang.data.agent.dal.connector.bo.TableInfoBO;
import com.liang.data.agent.service.datasource.DatasourceService;
import com.liang.data.agent.service.datasource.dto.DatasourceDTO;
import com.liang.data.agent.service.datasource.vo.DatasourceVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据源管理控制器
 *
 * <p>提供数据源的增删改查、连接测试以及表/字段元数据查询等接口</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/datasource")
@RequiredArgsConstructor
public class DatasourceController {

    private final DatasourceService datasourceService;

    /**
     * 创建数据源
     *
     * @param dto 数据源信息
     * @return 新创建的数据源 ID
     */
    @PostMapping
    public Result<Integer> create(@Valid @RequestBody DatasourceDTO dto) {
        Integer id = datasourceService.create(dto);
        return Results.success(id);
    }

    /**
     * 更新数据源
     *
     * @param dto 数据源信息（需包含 ID）
     * @return 操作结果
     */
    @PutMapping
    public Result<Void> update(@Valid @RequestBody DatasourceDTO dto) {
        datasourceService.update(dto);
        return Results.success();
    }

    /**
     * 删除数据源
     *
     * @param id 数据源 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        datasourceService.delete(id);
        return Results.success();
    }

    /**
     * 查询数据源详情
     *
     * @param id 数据源 ID
     * @return 数据源详情
     */
    @GetMapping("/{id}")
    public Result<DatasourceVO> detail(@PathVariable Integer id) {
        DatasourceVO vo = datasourceService.getById(id);
        return Results.success(vo);
    }

    /**
     * 查询数据源列表
     *
     * @return 数据源列表
     */
    @GetMapping
    public Result<List<DatasourceVO>> list() {
        List<DatasourceVO> list = datasourceService.findAll();
        return Results.success(list);
    }

    /**
     * 测试已保存的数据源连接
     *
     * <p>返回 {@code null} 表示连接成功，非 {@code null} 为失败原因</p>
     *
     * @param id 数据源 ID
     * @return 连接测试结果
     */
    @PostMapping("/{id}/test")
    public Result<String> testConnection(@PathVariable Integer id) {
        String result = datasourceService.testConnection(id);
        return Results.success(result);
    }

    /**
     * 创建前预检测数据源连接
     *
     * <p>在数据源保存到数据库之前，先验证连接参数是否可用</p>
     *
     * @param dto 数据源连接信息
     * @return 连接测试结果，{@code null} 表示成功，非 {@code null} 为失败原因
     */
    @PostMapping("/test")
    public Result<String> testBeforeCreate(@Valid @RequestBody DatasourceDTO dto) {
        String result = datasourceService.testConnectionByDto(dto);
        return Results.success(result);
    }

    /**
     * 获取数据源下的表列表
     *
     * @param id 数据源 ID
     * @return 表信息列表
     */
    @GetMapping("/{id}/tables")
    public Result<List<TableInfoBO>> getTables(@PathVariable Integer id) {
        List<TableInfoBO> tables = datasourceService.getTables(id);
        return Results.success(tables);
    }

    /**
     * 获取指定表的字段列表
     *
     * @param id        数据源 ID
     * @param tableName 表名
     * @return 字段信息列表
     */
    @GetMapping("/{id}/tables/{tableName}/columns")
    public Result<List<ColumnInfoBO>> getColumns(@PathVariable Integer id,
                                                 @PathVariable String tableName) {
        List<ColumnInfoBO> columns = datasourceService.getColumns(id, tableName);
        return Results.success(columns);
    }

    /**
     * 获取数据源指定表的数据采样预览 (默认前 10 行)
     *
     * @param id        数据源 ID
     * @param tableName 表名
     * @return 数据表采样结果包
     */
    @GetMapping("/{id}/tables/{tableName}/preview")
    public Result<ResultSetBO> getTablePreview(@PathVariable Integer id,
                                               @PathVariable String tableName) {
        ResultSetBO previewData = datasourceService.getTablePreview(id, tableName);
        return Results.success(previewData);
    }
}
