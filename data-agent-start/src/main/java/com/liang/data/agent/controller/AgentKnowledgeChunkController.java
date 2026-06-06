package com.liang.data.agent.controller;

import com.liang.data.agent.common.result.Result;
import com.liang.data.agent.common.result.Results;
import com.liang.data.agent.service.knowledge.chunk.AgentKnowledgeChunkService;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkDetailVO;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkOutlineVO;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkUpdateRequest;
import com.liang.data.agent.service.knowledge.chunk.vo.KnowledgeChunkUpdateResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识分块工作台接口。
 *
 * <p>提供分块大纲查询、详情编辑、重试同步和 AI 名称生成能力。</p>
 */
@RestController
@RequestMapping("/api/v1/agent-knowledge/{knowledgeId}/chunks")
@RequiredArgsConstructor
public class AgentKnowledgeChunkController {

    private final AgentKnowledgeChunkService chunkService;

    @GetMapping
    public Result<List<KnowledgeChunkOutlineVO>> list(@RequestParam Integer agentId,
                                                      @PathVariable Integer knowledgeId,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String vectorStatus) {
        return Results.success(chunkService.listOutlines(agentId, knowledgeId, keyword, vectorStatus));
    }

    @GetMapping("/{chunkId}")
    public Result<KnowledgeChunkDetailVO> detail(@RequestParam Integer agentId,
                                                 @PathVariable Integer knowledgeId,
                                                 @PathVariable String chunkId) {
        return Results.success(chunkService.getDetail(agentId, knowledgeId, chunkId));
    }

    @PutMapping("/{chunkId}")
    public Result<KnowledgeChunkUpdateResultVO> update(@RequestParam Integer agentId,
                                                       @PathVariable Integer knowledgeId,
                                                       @PathVariable String chunkId,
                                                       @Valid @RequestBody KnowledgeChunkUpdateRequest request) {
        return Results.success(chunkService.update(agentId, knowledgeId, chunkId, request));
    }

    @PostMapping("/{chunkId}/retry")
    public Result<KnowledgeChunkUpdateResultVO> retry(@RequestParam Integer agentId,
                                                      @PathVariable Integer knowledgeId,
                                                      @PathVariable String chunkId) {
        return Results.success(chunkService.retry(agentId, knowledgeId, chunkId));
    }

    @PostMapping("/{chunkId}/generate-name")
    public Result<KnowledgeChunkUpdateResultVO> generateName(@RequestParam Integer agentId,
                                                             @PathVariable Integer knowledgeId,
                                                             @PathVariable String chunkId) {
        return Results.success(chunkService.generateName(agentId, knowledgeId, chunkId));
    }
}
