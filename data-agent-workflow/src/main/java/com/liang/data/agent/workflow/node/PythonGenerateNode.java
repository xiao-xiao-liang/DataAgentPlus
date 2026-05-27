package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.constant.StateKey;
import com.liang.data.agent.common.enums.TextType;
import com.liang.data.agent.common.schema.SchemaDTO;
import com.liang.data.agent.workflow.dto.planner.ExecutionStep;
import com.liang.data.agent.workflow.prompt.PromptConstant;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.MarkdownParserUtil;
import com.liang.data.agent.workflow.util.PlanProcessUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.liang.data.agent.common.constant.ControlFlowKey.PYTHON_IS_SUCCESS;
import static com.liang.data.agent.common.constant.ControlFlowKey.PYTHON_TRIES_COUNT;
import static com.liang.data.agent.common.constant.NodeOutputKey.*;

/**
 * Python 代码生成节点
 *
 * <p>根据 SQL 查询结果 + 步骤描述，让 LLM 生成 Python 数据分析代码</p>
 * <p>支持重试: 如果上次生成的代码执行失败，会把错误信息反馈给 LLM</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonGenerateNode implements NodeAction {

    private final LlmService llmService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
        boolean hasValue = StateUtil.hasValue(state, SQL_RESULT_LIST_MEMORY);
        List<Map<String, String>> sqlResults = hasValue ? StateUtil.getListValue(state, SQL_RESULT_LIST_MEMORY) : new ArrayList<>();

        boolean codeRunSuccess = StateUtil.getObjectValue(state, PYTHON_IS_SUCCESS, Boolean.class, true);
        String userQuery = StateUtil.getCanonicalQuery(state);

        int triesCount = StateUtil.getObjectValue(state, PYTHON_TRIES_COUNT, Integer.class, 0);

        // 重试模式: 附加上次失败的代码和错误信息
        if (!codeRunSuccess) {
            String lastCode = StateUtil.getStringValue(state, PYTHON_GENERATE_NODE_OUTPUT);
            String lastError = StateUtil.getStringValue(state, PYTHON_EXECUTE_NODE_OUTPUT);
            userQuery += String.format("""
                    上次尝试生成的Python代码运行失败，请你重新生成符合要求的Python代码。
                    【上次生成代码】
                    ```python
                    %s
                    ```
                    【运行错误信息】
                    ```
                    %s
                    ```
                    """, lastCode, lastError);
        }

        ExecutionStep step = PlanProcessUtil.getExecutingStep(state);

        // TODO: 从配置获取 python_memory 和 python_timeout
        String systemPrompt = PromptConstant.getPythonGeneratorPromptTemplate().render(Map.of(
                "python_memory", "256",
                "python_timeout", "30",
                "database_schema", OBJECT_MAPPER.writeValueAsString(schemaDTO),
                "sample_input", OBJECT_MAPPER.writeValueAsString(sqlResults.stream().limit(5).toList()),
                "plan_description", OBJECT_MAPPER.writeValueAsString(step.getToolParameters())
        ));

        Flux<ChatResponse> pythonFlux = llmService.call(systemPrompt, userQuery);

        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(), state,
                aiResponse -> {
                    // 提取代码 (去掉 TextType 标记和 Markdown 包裹)
                    aiResponse = aiResponse.substring(
                            TextType.PYTHON.getStartSign().length(),
                            aiResponse.length() - TextType.PYTHON.getEndSign().length());
                    aiResponse = MarkdownParserUtil.extractRawText(aiResponse);
                    log.info("生成的 Python 代码:\n{}", aiResponse);
                    return Map.of(PYTHON_GENERATE_NODE_OUTPUT, aiResponse, PYTHON_TRIES_COUNT, triesCount + 1);
                },
                Flux.concat(
                        Flux.just(ChatResponseUtil.createPureResponse(TextType.PYTHON.getStartSign())),
                        pythonFlux,
                        Flux.just(ChatResponseUtil.createPureResponse(TextType.PYTHON.getEndSign()))
                )
        );
        return Map.of(PYTHON_GENERATE_NODE_OUTPUT, generator);
    }
}
