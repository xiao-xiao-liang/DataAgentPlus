package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.enums.TextType;
import com.liang.data.agent.dal.connector.DatabaseAccessor;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import com.liang.data.agent.dal.connector.bo.ResultSetBO;
import com.liang.data.agent.dal.entity.AgentDatasourceEntity;
import com.liang.data.agent.dal.entity.DatasourceEntity;
import com.liang.data.agent.dal.mapper.AgentDatasourceMapper;
import com.liang.data.agent.dal.mapper.DatasourceMapper;
import com.liang.data.agent.workflow.dto.SqlRetryDTO;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.PlanProcessUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.liang.data.agent.common.constant.ControlFlowKey.SQL_GENERATE_COUNT;
import static com.liang.data.agent.common.constant.ControlFlowKey.SQL_REGENERATE_REASON;
import static com.liang.data.agent.common.constant.NodeOutputKey.*;
import static com.liang.data.agent.common.constant.StateKey.AGENT_ID;

/**
 * SQL 执行节点
 *
 * <p>调用 DatabaseAccessor 执行 SQL，成功则返回结果集，失败则设置重试标记</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlExecuteNode implements NodeAction {

    private final DatabaseAccessor databaseAccessor;
    private final DatasourceMapper datasourceMapper;
    private final AgentDatasourceMapper agentDatasourceMapper;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String sql = StateUtil.getStringValue(state, SQL_GENERATE_OUTPUT);
        String agentId = StateUtil.getStringValue(state, AGENT_ID);

        log.info("SQL 执行 - 开始，智能体 ID: {}, SQL: {}", agentId, sql);

        // ======================== 阶段一：获取激活的数据源配置 ========================
        // 1. 查询激活数据源关联
        Integer datasourceId = agentDatasourceMapper.getActiveDatasource(Integer.valueOf(agentId));

        if (Objects.isNull(datasourceId)) {
            log.warn("智能体 {} 未配置或激活任何数据源", agentId);
            return buildErrorResponse(state, "智能体未配置激活的数据源", null);
        }

        // 2. 获取数据源连接实体
        DatasourceEntity datasource = datasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            log.warn("数据源配置未找到，数据源 ID: {}", datasourceId);
            return buildErrorResponse(state, "关联数据源配置缺失，ID: " + datasourceId, null);
        }

        DbConfigBO agentDbConfig = DbConfigBO.from(datasource);

        Map<String, Object> resultMap = new HashMap<>();

        // ======================== 阶段二：SQL 执行与防爆展示格式化 ========================
        try {
            // 3. 执行 SQL 并得到结果集 ResultSetBO
            ResultSetBO resultSetBO = databaseAccessor.executeSql(agentDbConfig, sql);

            // 4. 格式化结果集为明文表格文本 (内置 100 行限流以防御 OOM 异常)
            String resultText = formatQueryResult(resultSetBO);
            log.info("SQL 执行成功，返回数据量: {} 行", resultSetBO.data() != null ? resultSetBO.data().size() : 0);

            // 5. 累加明文结果至 memory (跨步骤累积)
            Map<String, String> existingResults = StateUtil.getMapValue(state, SQL_EXECUTE_NODE_OUTPUT);
            int stepNumber = PlanProcessUtil.getCurrentStepNumber(state);
            Map<String, String> updatedResults = PlanProcessUtil.addStepResult(existingResults, stepNumber, resultText);

            // 6. 回写当前步骤生成的 SQL 语句 (供 Planner/报告生成等分析环节做追溯)
            try {
                var step = PlanProcessUtil.getCurrentExecutionStep(state);
                if (step.getToolParameters() != null) {
                    step.getToolParameters().setSqlQuery(sql);
                }
            } catch (Exception ex) {
                log.debug("设置步骤 SQL 失败 (可能不在 Plan 模式): {}", ex.getMessage());
            }

            resultMap.put(SQL_EXECUTE_NODE_OUTPUT, updatedResults);
            // 缓存真实的物理数据行，后续 Python 运行节点可能要消费该内存
            resultMap.put(SQL_RESULT_LIST_MEMORY, resultSetBO.data());
            resultMap.put(SQL_REGENERATE_REASON, SqlRetryDTO.empty());
            resultMap.put(SQL_GENERATE_COUNT, 0); // 执行成功重置重试计数为 0

            // ======================== 阶段三：流式响应推送 ========================
            Flux<ChatResponse> displayFlux = Flux.just(
                    ChatResponseUtil.createResponse("SQL 执行成功！"),
                    ChatResponseUtil.createPureResponse(TextType.RESULT_SET.getStartSign()),
                    ChatResponseUtil.createPureResponse(resultText),
                    ChatResponseUtil.createPureResponse(TextType.RESULT_SET.getEndSign())
            );

            Flux<GraphResponse<StreamingOutput<ChatResponse>>> generator = FluxUtil.createStreamingGeneratorWithMessages(
                    this.getClass(), state, v -> resultMap, displayFlux
            );

            return Map.of(SQL_EXECUTE_NODE_OUTPUT, generator);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            log.error("SQL 执行异常 - SQL: \n{}\n原因: {}", sql, errorMsg, e);
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
     * 格式化查询结果为明文文本表格 (限制最多前 100 行展示以防御 OOM 风险)
     */
    private String formatQueryResult(ResultSetBO resultSet) {
        if (resultSet == null || resultSet.data() == null || resultSet.data().isEmpty()) {
            return "查询结果为空";
        }

        StringBuilder sb = new StringBuilder();
        List<String> headers = resultSet.columns();

        // 打印表头
        sb.append(String.join("\t", headers)).append("\n");
        sb.append("-".repeat(Math.max(headers.size() * 15, 30))).append("\n");

        // 打印数据行 (限流 100 行)
        int maxRows = Math.min(resultSet.data().size(), 100);
        for (int i = 0; i < maxRows; i++) {
            Map<String, String> row = resultSet.data().get(i);
            List<String> values = headers.stream()
                    .map(h -> Objects.toString(row.get(h), ""))
                    .toList();
            sb.append(String.join("\t", values)).append("\n");
        }

        if (resultSet.data().size() > maxRows) {
            sb.append("... 还有 ").append(resultSet.data().size() - maxRows).append(" 行未显示\n");
        }

        return sb.toString();
    }
}
