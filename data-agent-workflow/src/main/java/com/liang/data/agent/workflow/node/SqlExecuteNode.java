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
import com.liang.data.agent.common.ratelimit.ResourceType;
import com.liang.data.agent.dal.connector.DatabaseAccessor;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import com.liang.data.agent.dal.connector.bo.DisplayStyleBO;
import com.liang.data.agent.dal.connector.bo.ResultBO;
import com.liang.data.agent.dal.connector.bo.ResultSetBO;
import com.liang.data.agent.dal.entity.DatasourceEntity;
import com.liang.data.agent.dal.mapper.AgentDatasourceMapper;
import com.liang.data.agent.dal.mapper.DatasourceMapper;
import com.liang.data.agent.service.ratelimit.ResourceGate;
import com.liang.data.agent.service.ratelimit.ResourcePermit;
import com.liang.data.agent.workflow.dto.SqlRetryDTO;
import com.liang.data.agent.workflow.prompt.PromptHelper;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.JsonParseUtil;
import com.liang.data.agent.workflow.util.MarkdownParserUtil;
import com.liang.data.agent.workflow.util.PlanProcessUtil;
import com.liang.data.agent.workflow.util.SqlStatementGuard;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static com.liang.data.agent.common.constant.ControlFlowKey.SQL_GENERATE_COUNT;
import static com.liang.data.agent.common.constant.ControlFlowKey.SQL_REGENERATE_REASON;
import static com.liang.data.agent.common.constant.NodeOutputKey.*;
import static com.liang.data.agent.common.constant.StateKey.AGENT_ID;

/**
 * SQL 执行节点
 *
 * <p>调用 DatabaseAccessor 执行生成的 SQL。
 * 成功时利用大模型分析前 20 条样本数据并推荐最适合的图表展示风格（折线/饼图等），
 * 并最终将结果组合为 ResultBO 以 JSON 格式流式输出给前端。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlExecuteNode implements NodeAction {

    private final DatabaseAccessor databaseAccessor;
    private final DatasourceMapper datasourceMapper;
    private final AgentDatasourceMapper agentDatasourceMapper;
    private final LlmService llmService;
    private final DataAgentProperties properties;
    private final JsonParseUtil jsonParseUtil;
    private final ResourceGate resourceGate;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int SAMPLE_DATA_LIMIT = 20;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String sql = StateUtil.getStringValue(state, SQL_GENERATE_OUTPUT);
        String agentId = StateUtil.getStringValue(state, AGENT_ID);

        log.info("SQL 执行 - 开始，智能体 ID: {}, SQL: {}", agentId, sql);

        // ======================== 阶段一：获取激活的数据源配置 ========================
        if (!SqlStatementGuard.isSingleStatementQuery(sql)) {
            return buildErrorResponse(state, "SQL 包含多条语句，请仅生成一条可执行的查询语句", null);
        }

        ResourcePermit permit = resourceGate.tryAcquire(ResourceType.SQL_EXECUTION, "agent-" + agentId, Duration.ZERO);
        if (!permit.acquired()) {
            return buildErrorResponse(state, "SQL 执行资源繁忙，请稍后重试", null);
        }

        try (permit) {
            return executeWithPermit(state, sql, agentId);
        }
    }

    private Map<String, Object> executeWithPermit(OverAllState state, String sql, String agentId) throws Exception {
        // ======================== 阶段一：获取激活的数据源配置 ========================
        Integer datasourceId = agentDatasourceMapper.getActiveDatasource(Integer.valueOf(agentId));

        if (Objects.isNull(datasourceId)) {
            log.warn("智能体 {} 未配置或激活任何数据源", agentId);
            return buildErrorResponse(state, "智能体未配置激活的数据源", null);
        }

        DatasourceEntity datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            log.warn("数据源配置未找到，数据源 ID: {}", datasourceId);
            return buildErrorResponse(state, "关联数据源配置缺失，ID: " + datasourceId, null);
        }

        DbConfigBO agentDbConfig = DbConfigBO.from(datasource);
        Map<String, Object> resultMap = new HashMap<>();

        // ======================== 阶段二：SQL 执行与图表智能推荐 ========================
        try {
            // 1. 执行 SQL 并得到结果集 ResultSetBO
            ResultSetBO resultSetBO = databaseAccessor.executeSql(agentDbConfig, sql);

            // 2. 调用大模型分析获取最契合的图表展示配置
            DisplayStyleBO displayStyleBO = enrichResultSetWithChartConfig(state, resultSetBO);

            // 3. 打包为完整的 ResultBO 对象
            ResultBO resultBO = new ResultBO(resultSetBO, displayStyleBO);

            String strResultSetJson = OBJECT_MAPPER.writeValueAsString(resultSetBO);
            String strResultJson = OBJECT_MAPPER.writeValueAsString(resultBO);

            // 4. 将明文格式 JSON 写入 memory (跨步骤累积，供后续 Planner 或报告引用)
            Map<String, String> existingResults = StateUtil.getMapValue(state, SQL_EXECUTE_NODE_OUTPUT);
            int stepNumber = PlanProcessUtil.getExecutingStepNumber(state);
            Map<String, String> updatedResults = PlanProcessUtil.addStepResult(existingResults, stepNumber, strResultSetJson);

            // 5. 回写当前步骤生成的 SQL 语句
            try {
                var step = PlanProcessUtil.getExecutingStep(state);
                if (step.getToolParameters() != null) {
                    step.getToolParameters().setSqlQuery(sql);
                }
            } catch (Exception ex) {
                log.debug("设置步骤 SQL 失败: {}", ex.getMessage());
            }

            resultMap.put(SQL_EXECUTE_NODE_OUTPUT, updatedResults);
            resultMap.put(SQL_RESULT_LIST_MEMORY, resultSetBO.data());
            resultMap.put(SQL_REGENERATE_REASON, SqlRetryDTO.empty());
            resultMap.put(SQL_GENERATE_COUNT, 0); // 重置重试计数为 0

            // ======================== 阶段三：流式响应推送 ========================
            Flux<ChatResponse> displayFlux = Flux.just(
                    ChatResponseUtil.createResponse("SQL 执行成功，正在渲染展示图表...\n"),
                    ChatResponseUtil.createPureResponse(TextType.RESULT_SET.getStartSign()),
                    ChatResponseUtil.createPureResponse(strResultJson),
                    ChatResponseUtil.createPureResponse(TextType.RESULT_SET.getEndSign())
            );

            Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                    this.getClass(), state, v -> resultMap, displayFlux
            );

            return Map.of(SQL_EXECUTE_NODE_OUTPUT, generator);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            log.error("SQL 执行或图表推荐异常 - SQL: \n{}\n原因: {}", sql, errorMsg, e);
            return buildErrorResponse(state, errorMsg, resultMap);
        }
    }

    /**
     * 统一构建并封装失败的流式响应结果
     */
    private Map<String, Object> buildErrorResponse(OverAllState state, String errorMsg, Map<String, Object> customMap) {
        Map<String, Object> responseMap = customMap != null ? customMap : new HashMap<>();
        responseMap.put(SQL_REGENERATE_REASON, SqlRetryDTO.sqlExecute(errorMsg));

        Flux<ChatResponse> errorFlux = Flux.just(
                ChatResponseUtil.createResponse("SQL 执行失败: " + errorMsg)
        );

        Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                this.getClass(), state, v -> responseMap, errorFlux
        );

        return Map.of(SQL_EXECUTE_NODE_OUTPUT, generator);
    }

    /**
     * 调用大模型分析 SQL 结果并智能提取最适合的图表展示风格
     */
    private DisplayStyleBO enrichResultSetWithChartConfig(OverAllState state, ResultSetBO resultSetBO) {
        // 1. 若配置中显式禁用了图表推荐，直接降级至表格展示
        if (!properties.isEnableSqlResultChart()) {
            log.debug("SQL 结果图表渲染已禁用，默认使用 Table 展示风格");
            return DisplayStyleBO.tableDefault();
        }

        if (resultSetBO == null || resultSetBO.data() == null || resultSetBO.data().isEmpty()) {
            return DisplayStyleBO.tableDefault();
        }

        try {
            // 2. 获取用户意图及 CanonicalQuery
            String userQuery = StateUtil.getCanonicalQuery(state);

            // 3. 截取前 20 条数据范例转为 JSON，控制 token 大小以防止过长
            List<Map<String, String>> sampleData = resultSetBO.data().stream()
                    .limit(SAMPLE_DATA_LIMIT)
                    .toList();
            String sampleDataJson = OBJECT_MAPPER.writeValueAsString(sampleData);

            // 4. 加载 data-view 系统与用户提示词模板
            String systemPrompt = PromptHelper.buildDataViewAnalyzeSystemPrompt();
            String userPrompt = PromptHelper.buildDataViewAnalyzeUserPrompt(userQuery, sampleDataJson);

            log.debug("准备进行图表智能推荐，SystemPrompt:\n{}", systemPrompt);
            log.debug("UserPrompt:\n{}", userPrompt);

            // 5. 同步阻塞请求 LLM 并在超时保护下拦截
            String chartConfigJson = llmService.toStringFlux(llmService.call(systemPrompt, userPrompt))
                    .collect(StringBuilder::new, StringBuilder::append)
                    .map(StringBuilder::toString)
                    .block(Duration.ofMillis(properties.getEnrichSqlResultTimeout()));

            if (chartConfigJson != null && !chartConfigJson.trim().isEmpty()) {
                String content = MarkdownParserUtil.extractRawText(chartConfigJson.trim());
                DisplayStyleBO displayStyleBO = jsonParseUtil.tryConvertToObject(content, DisplayStyleBO.class);
                if (displayStyleBO != null) {
                    log.info("智能图表分析推荐成功，推荐图表类型: {}, 标题: {}", displayStyleBO.type(), displayStyleBO.title());
                    return displayStyleBO;
                }
            }
            log.warn("大模型返回图表配置为空，触发表格优雅兜底");
        } catch (Exception e) {
            if (isTimeoutException(e)) {
                log.warn("SQL 结果图表推荐在 {} 毫秒后超时；回退至表格显示", properties.getEnrichSqlResultTimeout());
            } else {
                log.warn("SQL 结果图表推荐失败：{}；回退至表格显示", e.toString());
            }
            log.debug("SQL 结果图表推荐回退详情", e);
        }
        return DisplayStyleBO.tableDefault();
    }

    private boolean isTimeoutException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
