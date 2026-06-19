package com.liang.data.agent.controller;

import com.liang.data.agent.common.result.Result;
import com.liang.data.agent.common.result.Results;
import com.liang.data.agent.service.agentdatasource.AgentDatasourceService;
import com.liang.data.agent.service.agentdatasource.AgentDatasourceColumnService;
import com.liang.data.agent.service.agentdatasource.dto.ColumnAnalyticUpdateDTO;
import com.liang.data.agent.service.agentdatasource.vo.AgentDatasourceColumnVO;
import com.liang.data.agent.service.agentdatasource.vo.AgentDatasourceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智能体数据源绑定管理控制器
 *
 * <p>提供智能体与数据源的绑定、解绑、查询以及 Schema 同步等操作。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/{agentId}")
@RequiredArgsConstructor
public class AgentDatasourceController {

    private final AgentDatasourceService agentDatasourceService;
    private final AgentDatasourceColumnService agentDatasourceColumnService;

    /**
     * 绑定数据源到指定智能体
     *
     * @param agentId      智能体 ID
     * @param datasourceId 数据源 ID
     * @param tables       选中的表名列表，可选
     * @return 操作结果
     */
    @PostMapping("/datasource/{datasourceId}")
    public Result<Void> bind(@PathVariable Integer agentId,
                             @PathVariable Integer datasourceId,
                             @RequestBody(required = false) List<String> tables) {
        log.info("绑定数据源, agentId={}, datasourceId={}, tables={}", agentId, datasourceId, tables);
        agentDatasourceService.bindDatasource(agentId, datasourceId, tables);
        return Results.success();
    }

    /**
     * 解绑指定智能体的数据源
     *
     * @param agentId 智能体 ID
     * @return 操作结果
     */
    @DeleteMapping("/datasource")
    public Result<Void> unbind(@PathVariable Integer agentId) {
        log.info("解绑数据源, agentId={}", agentId);
        agentDatasourceService.unbindDatasource(agentId);
        return Results.success();
    }

    /**
     * 获取指定智能体当前绑定的数据源信息
     *
     * @param agentId 智能体 ID
     * @return 当前绑定的数据源信息，未绑定时 data 为 null
     */
    @GetMapping("/datasource")
    public Result<AgentDatasourceVO> getCurrent(@PathVariable Integer agentId) {
        log.info("查询当前绑定的数据源, agentId={}", agentId);
        AgentDatasourceVO vo = agentDatasourceService.getCurrentBinding(agentId);
        return Results.success(vo);
    }

    /**
     * 触发指定智能体的数据源 Schema 同步
     *
     * @param agentId 智能体 ID
     * @param tables  指定的表名列表，可选
     * @return 操作结果
     */
    @PostMapping("/schema/sync")
    public Result<Void> syncSchema(@PathVariable Integer agentId,
                                   @RequestBody(required = false) List<String> tables) {
        log.info("触发 Schema 同步, agentId={}, tables={}", agentId, tables);
        agentDatasourceService.syncSchema(agentId, tables);
        return Results.success();
    }

    /**
     * 查询当前智能体指定数据表的字段配置。
     */
    @GetMapping("/datasource/{datasourceId}/tables/{tableName}/columns")
    public Result<List<AgentDatasourceColumnVO>> getColumns(@PathVariable Integer agentId,
                                                            @PathVariable Integer datasourceId,
                                                            @PathVariable String tableName) {
        return Results.success(agentDatasourceColumnService.listColumns(agentId, datasourceId, tableName));
    }

    /**
     * 批量更新当前智能体字段参与分析状态。
     */
    @PutMapping("/datasource/{datasourceId}/tables/{tableName}/columns/analytic")
    public Result<Void> updateColumnAnalytic(@PathVariable Integer agentId,
                                             @PathVariable Integer datasourceId,
                                             @PathVariable String tableName,
                                             @Valid @RequestBody ColumnAnalyticUpdateDTO dto) {
        agentDatasourceColumnService.updateAnalytic(agentId, datasourceId, tableName,
                dto.getColumnNames(), dto.getIsAnalytic());
        return Results.success();
    }
}
