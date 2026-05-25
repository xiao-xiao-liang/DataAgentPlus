package com.liang.data.agent.workflow.node;

import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.HUMAN_NEXT_NODE;
import static com.liang.data.agent.common.constant.StateKey.PLAN_EXECUTOR_NODE;

/**
 * 人工反馈节点 (Phase 4 桩实现，直接放行)
 */
@Slf4j
@Component
public class HumanFeedbackNode implements NodeAction {
    
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("HumanFeedback 节点 (Stub) - 直接放行");
        
        Flux<ChatResponse> flux = Flux.just(ChatResponseUtil.createResponse("人工审核 (已跳过)"));
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> gen = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(), state, v -> Map.of(HUMAN_NEXT_NODE, PLAN_EXECUTOR_NODE), flux);
        
        return Map.of(HUMAN_NEXT_NODE, gen);
    }
}
