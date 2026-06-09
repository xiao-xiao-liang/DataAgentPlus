package com.liang.data.agent.controller;

import com.liang.data.agent.common.result.Result;
import com.liang.data.agent.common.result.Results;
import com.liang.data.agent.service.knowledge.AgentKnowledgeService;
import com.liang.data.agent.service.knowledge.vo.AgentKnowledgeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 智能体知识源接口。
 *
 * <p>提供知识文件上传、列表查询、分块查看和删除能力。</p>
 */
@RestController
@RequestMapping("/api/v1/agent-knowledge")
@RequiredArgsConstructor
public class AgentKnowledgeController {

    private final AgentKnowledgeService agentKnowledgeService;

    @GetMapping
    public Result<List<AgentKnowledgeVO>> list(@RequestParam Integer agentId) {
        return Results.success(agentKnowledgeService.listByAgent(agentId));
    }

    @PostMapping("/upload")
    public Result<AgentKnowledgeVO> upload(@RequestParam Integer agentId,
                                           @RequestParam Long userId,
                                           @RequestParam(required = false) String title,
                                           @RequestParam(defaultValue = "title") String splitterType,
                                           @RequestParam("file") MultipartFile file) throws IOException {
        return Results.success(agentKnowledgeService.upload(
                agentId,
                userId,
                title,
                file.getOriginalFilename(),
                file.getInputStream(),
                file.getSize(),
                splitterType
        ));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@RequestParam Integer agentId, @PathVariable Integer id) {
        agentKnowledgeService.delete(agentId, id);
        return Results.success();
    }
}
