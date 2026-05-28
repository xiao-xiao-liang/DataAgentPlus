package com.liang.data.agent.workflow.node;

import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Optional;

import static com.liang.data.agent.common.constant.ControlFlowKey.*;
import static com.liang.data.agent.common.constant.ModeSwitch.HUMAN_REVIEW_ENABLED;
import static com.liang.data.agent.common.constant.ModeSwitch.HUMAN_FEEDBACK_DATA;
import static com.liang.data.agent.common.constant.NodeOutputKey.PLANNER_NODE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.PLAN_EXECUTOR_NODE;
import static com.liang.data.agent.common.constant.StateKey.PLANNER_NODE;

/**
 * 人工反馈审核节点
 *
 * <p>用于对 Planner 规划出来的执行计划进行人工审核。
 * 支持批准、驳回、修改并重新执行，内置了最大重试次数以防发生死循环。</p>
 */
@Slf4j
@Component
public class HumanFeedbackNode implements NodeAction {

    private static final int MAX_REPAIR_LIMIT = 3;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("开始执行人工反馈审核节点...");

        // 1. 检查最大修复次数限制，防止死循环
        int repairCount = StateUtil.getObjectValue(state, PLAN_REPAIR_COUNT, Integer.class, 0);
        if (repairCount >= MAX_REPAIR_LIMIT) {
            log.warn("人工计划修复次数已达到上限 ({} 次)，强制终止流程", MAX_REPAIR_LIMIT);
            Flux<ChatResponse> responseFlux = Flux.just(ChatResponseUtil.createResponse("[警告] 计划修复次数已超限，工作流终止。"));
            Flux<GraphResponse<StreamingOutput<ChatResponse>>> gen = FluxUtil.createStreamingGeneratorWithMessages(
                    this.getClass(), state, v -> Map.of(HUMAN_NEXT_NODE, "END"), responseFlux);
            return Map.of(HUMAN_NEXT_NODE, gen);
        }

        // 2. 获取人工反馈数据
        Map<?, ?> feedbackData = StateUtil.getObjectValue(state, HUMAN_FEEDBACK_DATA, Map.class, Map.of());
        if (feedbackData.isEmpty()) {
            log.info("尚未获取到人工反馈数据，工作流转入 WAIT_FOR_FEEDBACK 挂起状态并等待输入");
            Flux<ChatResponse> responseFlux = Flux.just(ChatResponseUtil.createResponse("[系统] 等待人工复核反馈中..."));
            Flux<GraphResponse<StreamingOutput<ChatResponse>>> gen = FluxUtil.createStreamingGeneratorWithMessages(
                    this.getClass(), state, v -> Map.of(HUMAN_NEXT_NODE, WAIT_FOR_FEEDBACK), responseFlux);
            return Map.of(HUMAN_NEXT_NODE, gen);
        }

        // 3. 安全提取并校验 approved 状态，严禁信任默认值
        boolean approved = Optional.ofNullable(feedbackData.get("feedback"))
                .map(v -> {
                    if (v instanceof Boolean b) {
                        return b;
                    }
                    return Boolean.parseBoolean(v.toString());
                })
                .orElse(false); // 默认不批准，规避无反馈时自动批准的缺陷

        if (approved) {
            log.info("用户已批准该计划，转入计划执行器。");
            Flux<ChatResponse> responseFlux = Flux.just(ChatResponseUtil.createResponse("[系统] 人工审核已通过，继续执行步骤！"));
            Flux<GraphResponse<StreamingOutput<ChatResponse>>> gen = FluxUtil.createStreamingGeneratorWithMessages(
                    this.getClass(), state, v -> Map.of(
                            HUMAN_NEXT_NODE, PLAN_EXECUTOR_NODE,
                            HUMAN_REVIEW_ENABLED, false
                    ), responseFlux);
            return Map.of(HUMAN_NEXT_NODE, gen);
        } else {
            // 解析拒绝理由
            String feedbackContent = Optional.ofNullable(feedbackData.get("feedback_content"))
                    .map(Object::toString)
                    .filter(StringUtils::hasText)
                    .orElse("用户驳回了该执行计划");

            int nextRepairCount = repairCount + 1;
            log.info("用户已驳回该计划，正路由回 Planner 进行重构设计。当前修复次数: {}", nextRepairCount);
            
            Flux<ChatResponse> responseFlux = Flux.just(ChatResponseUtil.createResponse(
                    String.format("[系统] 人工审核已驳回，意见为: \"%s\"。正在尝试重新设计计划...", feedbackContent)));

            Flux<GraphResponse<StreamingOutput<ChatResponse>>> gen = FluxUtil.createStreamingGeneratorWithMessages(
                    this.getClass(), state, v -> Map.of(
                            HUMAN_NEXT_NODE, PLANNER_NODE,
                            PLAN_REPAIR_COUNT, nextRepairCount,
                            PLAN_CURRENT_STEP, 1,
                            HUMAN_REVIEW_ENABLED, true,
                            PLAN_VALIDATION_ERROR, feedbackContent,
                            PLANNER_NODE_OUTPUT, ""
                    ), responseFlux);
            return Map.of(HUMAN_NEXT_NODE, gen);
        }
    }
}
