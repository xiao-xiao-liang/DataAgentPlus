package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.config.DataAgentProperties;
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
import static com.liang.data.agent.common.constant.NodeOutputKey.*;

/**
 * Python 代码生成节点
 *
 * <p>根据 SQL 查询结果集与当前步骤计划描述，调用大模型生成相应的 Python 数据分析与可视化代码。
 * 同样在运行失败时收集错误堆栈反馈给大模型进行自愈，配置参数全面支持外部化。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonGenerateNode implements NodeAction {

    private final LlmService llmService;
    private final DataAgentProperties properties;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
        boolean hasValue = StateUtil.hasValue(state, SQL_RESULT_LIST_MEMORY);
        List<Map<String, String>> sqlResults = hasValue ? StateUtil.getListValue(state, SQL_RESULT_LIST_MEMORY) : new ArrayList<>();

        boolean codeRunSuccess = StateUtil.getObjectValue(state, PYTHON_IS_SUCCESS, Boolean.class, true);
        String userQuery = StateUtil.getCanonicalQuery(state);

        // 重试纠错模式: 附加上次失败的代码和错误堆栈信息
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

        // 使用配置中心外置的内存及超时设定
        String systemPrompt = PromptConstant.getPythonGeneratorPromptTemplate().render(Map.of(
                "python_memory", properties.getPythonMemoryLimit(),
                "python_timeout", properties.getPythonTimeout(),
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
                    log.debug("生成的 Python 代码:\n{}", aiResponse);
                    // 仅保存代码，不再此处修改重试次数，次数修改已归并到执行器节点
                    return Map.of(PYTHON_GENERATE_NODE_OUTPUT, aiResponse);
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
