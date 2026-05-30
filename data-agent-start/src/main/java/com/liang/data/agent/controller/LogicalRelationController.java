package com.liang.data.agent.controller;

import com.liang.data.agent.common.result.Result;
import com.liang.data.agent.common.result.Results;
import com.liang.data.agent.service.logicalrelation.LogicalRelationService;
import com.liang.data.agent.service.logicalrelation.dto.LogicalRelationDTO;
import com.liang.data.agent.service.logicalrelation.vo.LogicalRelationVO;
import jakarta.validation.Valid;
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
 * Logical relation management controller.
 */
@RestController
@RequestMapping("/api/datasource/{datasourceId}/logical-relations")
public class LogicalRelationController {

    private final LogicalRelationService logicalRelationService;

    public LogicalRelationController(LogicalRelationService logicalRelationService) {
        this.logicalRelationService = logicalRelationService;
    }

    @GetMapping
    public Result<List<LogicalRelationVO>> list(@PathVariable Integer datasourceId) {
        return Results.success(logicalRelationService.listByDatasource(datasourceId));
    }

    @PostMapping
    public Result<Integer> create(@PathVariable Integer datasourceId,
                                  @Valid @RequestBody LogicalRelationDTO dto) {
        dto.setDatasourceId(datasourceId);
        return Results.success(logicalRelationService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Integer datasourceId,
                               @PathVariable Integer id,
                               @Valid @RequestBody LogicalRelationDTO dto) {
        dto.setId(id);
        dto.setDatasourceId(datasourceId);
        logicalRelationService.update(datasourceId, dto);
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer datasourceId,
                               @PathVariable Integer id) {
        logicalRelationService.delete(datasourceId, id);
        return Results.success();
    }
}
