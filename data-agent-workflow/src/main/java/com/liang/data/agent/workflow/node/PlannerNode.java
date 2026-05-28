package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.enums.TextType;
import com.liang.data.agent.common.schema.SchemaDTO;
import com.liang.data.agent.workflow.dto.planner.Plan;
import com.liang.data.agent.workflow.prompt.PromptConstant;
import com.liang.data.agent.workflow.prompt.PromptHelper;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.MarkdownParserUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.PLAN_CURRENT_STEP;
import static com.liang.data.agent.common.constant.ControlFlowKey.PLAN_VALIDATION_ERROR;
import static com.liang.data.agent.common.constant.ModeSwitch.IS_ONLY_NL2SQL;
import static com.liang.data.agent.common.constant.NodeOutputKey.*;

/**
 * Planner 节点 — 生成执行计划
 *
 * <p>根据用户查询、Schema、证据等信息，让 LLM 生成一个包含多个步骤的执行计划 (Plan JSON)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlannerNode implements NodeAction {

    private final LlmService llmService;

    private static final BeanOutputConverter<Plan> CONVERTER = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
    });

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        boolean isNl2SqlOnly = StateUtil.getObjectValue(state, IS_ONLY_NL2SQL, Boolean.class, false);

        return isNl2SqlOnly ? handleNl2SqlOnly(state) : handlePlanGenerate(state);
    }

    /**
     * NL2SQL-only 模式: 直接跳过 LLM 交互返回固定执行计划
     */
    private Map<String, Object> handleNl2SqlOnly(OverAllState state) {
        log.info("PlannerNode - NL2SQL-only 模式，使用固定计划");
        String fixedPlan = Plan.nl2SqlPlan();

        Flux<ChatResponse> displayFlux = Flux.just(
                ChatResponseUtil.createResponse("NL2SQL 模式，使用固定执行计划"),
                ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()),
                ChatResponseUtil.createPureResponse(fixedPlan),
                ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign())
        );

        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(), state, v -> Map.of(
                        PLANNER_NODE_OUTPUT, fixedPlan,
                        PLAN_CURRENT_STEP, 1),
                displayFlux
        );

        return Map.of(PLANNER_NODE_OUTPUT, generator);
    }

    /**
     * 正常模式: 调用大模型动态规划执行计划
     */
    private Map<String, Object> handlePlanGenerate(OverAllState state) {
        // 1. 获取增强后的查询
        String canonicalQuery = StateUtil.getCanonicalQuery(state);

        // 2. 检查是否存在来自上一次执行的计划验证报错
        String validationError = StateUtil.getStringValue(state, PLAN_VALIDATION_ERROR, null);
        if (validationError != null) {
            log.info("PlannerNode - 侦测到上轮计划验证失败，进入计划修复生成模式，错误: {}", validationError);
        } else {
            log.info("PlannerNode - 开始生成初始执行计划，查询: {}", canonicalQuery);
        }

        // 3. 准备提示词上下文数据 (包含 Schema 结构、物理/逻辑外键、证据、以及语义模型信息)
        SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
        String evidence = StateUtil.getStringValue(state, EVIDENCE_OUTPUT);
        String schemaInfo = PromptHelper.buildMixMacSqlDbPrompt(schemaDTO, true);
        String semanticModelPrompt = StateUtil.getStringValue(state, GENERATED_SEMANTIC_MODEL_PROMPT, "");

        // 4. 根据是否修复模式构建相应的 Prompt 变量数据
        String userQuestionPrompt = buildUserPrompt(canonicalQuery, validationError, state);

        String prompt = PromptConstant.getPlannerPromptTemplate().render(Map.of(
                "user_question", userQuestionPrompt,
                "schema", schemaInfo,
                "evidence", evidence,
                "semantic_model", semanticModelPrompt,
                "plan_validation_error", formatValidationError(validationError),
                "format", CONVERTER.getFormat()
        ));

        log.debug("Planner 提示词装配完成:\n{}", prompt);

        // 5. 触发流式 LLM 调用并推送图形响应
        Flux<ChatResponse> responseFlux = llmService.callUser(prompt);

        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGenerator(
                this.getClass(), state, responseFlux,
                Flux.just(ChatResponseUtil.createResponse("正在生成执行计划..."),
                        ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())),
                Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
                        ChatResponseUtil.createResponse("\n执行计划生成完成！")),
                planOutput -> {
                    String planJson = MarkdownParserUtil.extractRawText(planOutput.trim());
                    log.info("生成的执行计划 JSON 数据: {}", planJson);
                    return Map.of(PLANNER_NODE_OUTPUT, planJson, PLAN_CURRENT_STEP, 1);
                }
        );

        return Map.of(PLANNER_NODE_OUTPUT, generator);
    }

    /**
     * 构建融合了纠错与原计划参数的动态提示词主体
     */
    private String buildUserPrompt(String input, String validationError, OverAllState state) {
        if (validationError == null) {
            return input;
        }

        // 读取此前生成但被驳回的执行计划
        String previousPlan = StateUtil.getStringValue(state, PLANNER_NODE_OUTPUT, "");
        return String.format("""
                        【重要反馈】用户/系统驳回了上一次生成的执行计划，反馈理由如下:
                        "%s"
                        
                        【原始查询需求】: %s
                        
                        【被驳回的旧执行计划】:
                        %s
                        
                        【纠错指令】: 请结合反馈意见，彻底修正被驳回计划中的缺陷并生成全新的、完全可用的执行计划。""",
                validationError, input, previousPlan
        );
    }

    /**
     * 格式化验证错误文本以供提示词内渲染
     */
    private String formatValidationError(String validationError) {
        return validationError != null ? String.format(
                "**【CRITICAL】计划验证反馈建议**:\n%s\n\n**请务必针对以上反馈进行自查和修复！**", validationError
        ) : "";
    }
}