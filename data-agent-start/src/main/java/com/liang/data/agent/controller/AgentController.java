package com.liang.data.agent.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.liang.data.agent.common.result.Result;
import com.liang.data.agent.common.result.Results;
import com.liang.data.agent.service.agent.AgentService;
import com.liang.data.agent.service.agent.dto.AgentDTO;
import com.liang.data.agent.service.agent.vo.AgentVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 智能体管理控制器
 *
 * <p>提供智能体的增删改查、发布/下线以及 API Key 管理等接口</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /**
     * 创建智能体
     *
     * @param dto 智能体信息
     * @return 新创建的智能体 ID
     */
    @PostMapping
    public Result<Integer> create(@Valid @RequestBody AgentDTO dto) {
        Integer id = agentService.create(dto);
        return Results.success(id);
    }

    /**
     * 更新智能体
     *
     * @param dto 智能体信息（需包含 ID）
     * @return 操作结果
     */
    @PutMapping
    public Result<Void> update(@Valid @RequestBody AgentDTO dto) {
        agentService.update(dto);
        return Results.success();
    }

    /**
     * 删除智能体
     *
     * @param id 智能体 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        agentService.delete(id);
        return Results.success();
    }

    /**
     * 查询智能体详情
     *
     * @param id 智能体 ID
     * @return 智能体详情
     */
    @GetMapping("/{id}")
    public Result<AgentVO> detail(@PathVariable Integer id) {
        AgentVO vo = agentService.getById(id);
        return Results.success(vo);
    }

    /**
     * 查询智能体列表
     *
     * <p>支持按关键词和状态进行筛选</p>
     *
     * @param keyword 关键词（可选），模糊匹配智能体名称
     * @param status  状态（可选），如 draft / published / offline
     * @return 智能体列表
     */
    @GetMapping("/list")
    public Result<List<AgentVO>> list(@RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String status) {
        List<AgentVO> list = agentService.list(keyword, status);
        return Results.success(list);
    }

    /**
     * 发布智能体
     *
     * <p>将智能体状态变更为已发布，使其可供外部调用</p>
     *
     * @param id 智能体 ID
     * @return 操作结果
     */
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Integer id) {
        agentService.publish(id);
        return Results.success();
    }

    /**
     * 下线智能体
     *
     * <p>将智能体状态变更为已下线，停止对外提供服务</p>
     *
     * @param id 智能体 ID
     * @return 操作结果
     */
    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Integer id) {
        agentService.offline(id);
        return Results.success();
    }

    /**
     * 获取智能体的 API Key
     *
     * @param id 智能体 ID
     * @return API Key 字符串
     */
    @GetMapping("/{id}/api-key")
    public Result<String> getApiKey(@PathVariable Integer id) {
        String apiKey = agentService.getApiKey(id);
        return Results.success(apiKey);
    }

    /**
     * 生成智能体的 API Key
     *
     * <p>为智能体首次生成 API Key，若已存在则抛出异常</p>
     *
     * @param id 智能体 ID
     * @return 新生成的 API Key
     */
    @PostMapping("/{id}/api-key/generate")
    public Result<String> generateApiKey(@PathVariable Integer id) {
        String apiKey = agentService.generateApiKey(id);
        return Results.success(apiKey);
    }

    /**
     * 重置智能体的 API Key
     *
     * <p>作废旧 Key 并生成新的 API Key</p>
     *
     * @param id 智能体 ID
     * @return 重置后的 API Key
     */
    @PostMapping("/{id}/api-key/reset")
    public Result<String> resetApiKey(@PathVariable Integer id) {
        String apiKey = agentService.resetApiKey(id);
        return Results.success(apiKey);
    }
}
