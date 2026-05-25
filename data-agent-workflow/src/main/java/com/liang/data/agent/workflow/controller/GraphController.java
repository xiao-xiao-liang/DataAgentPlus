package com.liang.data.agent.workflow.controller;

import com.liang.data.agent.workflow.dto.GraphRequest;
import com.liang.data.agent.workflow.service.GraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 工作流 SSE 接口
 *
 * <p>提供 text/event-stream 流式响应</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    /**
     * SSE 流式对话
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody GraphRequest request) {
        log.info("收到对话请求 - agentId: {}", request.getAgentId());
        return graphService.chat(
                request.getQuery(),
                request.getAgentId(),
                request.getThreadId(),
                request.isNl2sqlOnly()
        );
    }

    /**
     * 获取工作流图结构 (PlantUML)
     */
    @GetMapping("/visualization")
    public String visualization() {
        return graphService.getGraphVisualization();
    }
}
