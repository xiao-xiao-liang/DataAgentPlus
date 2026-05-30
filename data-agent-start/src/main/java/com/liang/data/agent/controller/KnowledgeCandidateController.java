package com.liang.data.agent.controller;

import com.liang.data.agent.common.result.Result;
import com.liang.data.agent.common.result.Results;
import com.liang.data.agent.service.knowledgecandidate.KnowledgeCandidateService;
import com.liang.data.agent.service.knowledgecandidate.dto.KnowledgeCandidateDTO;
import com.liang.data.agent.service.knowledgecandidate.dto.PublishKnowledgeCandidateDTO;
import com.liang.data.agent.service.knowledgecandidate.vo.KnowledgeCandidateVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge-candidates")
public class KnowledgeCandidateController {

    private final KnowledgeCandidateService knowledgeCandidateService;

    public KnowledgeCandidateController(KnowledgeCandidateService knowledgeCandidateService) {
        this.knowledgeCandidateService = knowledgeCandidateService;
    }

    @GetMapping
    public Result<List<KnowledgeCandidateVO>> list(@RequestParam Integer agentId,
                                                   @RequestParam(required = false) String status) {
        return Results.success(knowledgeCandidateService.listByAgent(agentId, status));
    }

    @GetMapping("/{id}")
    public Result<KnowledgeCandidateVO> detail(@PathVariable Long id) {
        return Results.success(knowledgeCandidateService.getDetail(id));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody KnowledgeCandidateDTO dto) {
        return Results.success(knowledgeCandidateService.create(dto));
    }

    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        knowledgeCandidateService.submitForReview(id);
        return Results.success();
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                               @RequestBody(required = false) PublishKnowledgeCandidateDTO dto) {
        knowledgeCandidateService.reject(id, dto);
        return Results.success();
    }

    @PostMapping("/{id}/publish")
    public Result<Long> publish(@PathVariable Long id,
                                @RequestBody(required = false) PublishKnowledgeCandidateDTO dto) {
        return Results.success(knowledgeCandidateService.publish(id, dto));
    }
}
