package com.liang.data.agent.workflow.config;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.CreateOption;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.liang.data.agent.workflow.dispatcher.*;
import com.liang.data.agent.workflow.node.*;
import com.liang.data.agent.workflow.util.NodeBeanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.liang.data.agent.common.constant.ControlFlowKey.*;
import static com.liang.data.agent.common.constant.ModeSwitch.*;
import static com.liang.data.agent.common.constant.NodeOutputKey.*;
import static com.liang.data.agent.common.constant.StateKey.*;

/**
 * 工作流图编排配置
 *
 * <p>定义 NL2SQL 的完整 StateGraph：16 个节点 + 11 个路由</p>
 */
@Slf4j
@Configuration
public class WorkflowConfiguration {

    /**
     * 创建工作流 checkpoint 持久化存储。
     *
     * <p>图恢复依赖框架 checkpoint，仅保存业务快照不足以跨进程恢复；
     * 使用 MySQL 存储后，服务重启后仍可从人工审核等中断点继续执行。</p>
     *
     * @param dataSource 应用数据源
     * @return 工作流 checkpoint 存储器
     */
    @Bean
    public BaseCheckpointSaver workflowCheckpointSaver(DataSource dataSource) {
        return MysqlSaver.builder()
                .dataSource(dataSource)
                .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                .build();
    }

    @Bean
    public StateGraph nl2sqlGraph(NodeBeanUtil nodeBeanUtil) throws GraphStateException {
        // ==================== 1. 定义 State Key 策略 ====================
        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> m = new HashMap<>();

            // 用户输入
            m.put(INPUT_KEY, KeyStrategy.REPLACE);
            m.put(AGENT_ID, KeyStrategy.REPLACE);
            m.put(DATASOURCE_ID, KeyStrategy.REPLACE);
            m.put(THREAD_ID, KeyStrategy.REPLACE);
            m.put(MULTI_TURN_CONTEXT, KeyStrategy.REPLACE);

            // 节点输出
            m.put(INTENT_RECOGNITION_NODE_OUTPUT, KeyStrategy.REPLACE);
            m.put(QUERY_ENHANCE_NODE_OUTPUT, KeyStrategy.REPLACE);
            m.put(EVIDENCE_OUTPUT, KeyStrategy.REPLACE);
            m.put(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, KeyStrategy.REPLACE);
            m.put(COLUMN_DOCUMENTS_FOR_SCHEMA_OUTPUT, KeyStrategy.REPLACE);
            m.put(SCHEMA_RECALL_NODE_OUTPUT, KeyStrategy.REPLACE);
            m.put(TABLE_RELATION_OUTPUT, KeyStrategy.REPLACE);
            m.put(TABLE_RELATION_EXCEPTION_OUTPUT, KeyStrategy.REPLACE);
            m.put(TABLE_RELATION_RETRY_COUNT, KeyStrategy.REPLACE);
            m.put(DB_DIALECT_TYPE, KeyStrategy.REPLACE);
            m.put(GENERATED_SEMANTIC_MODEL_PROMPT, KeyStrategy.REPLACE);
            m.put(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, KeyStrategy.REPLACE);
            m.put(CLARIFICATION_REQUEST, KeyStrategy.REPLACE);
            m.put(CLARIFICATION_FEEDBACK_DATA, KeyStrategy.REPLACE);
            m.put(CLARIFICATION_NORMALIZED_OUTPUT, KeyStrategy.REPLACE);
            m.put(CLARIFICATION_CONFIRM_DATA, KeyStrategy.REPLACE);
            m.put(CLARIFICATION_EVIDENCE, KeyStrategy.REPLACE);
            m.put(CLARIFICATION_CONFIRMED, KeyStrategy.REPLACE);
            m.put(MEMORY_CANDIDATE_OUTPUT, KeyStrategy.REPLACE);
            m.put(MEMORY_CANDIDATE_ID, KeyStrategy.REPLACE);
            m.put(MEMORY_SAVE_REQUIRED, KeyStrategy.REPLACE);

            // SQL 链路
            m.put(SQL_GENERATE_OUTPUT, KeyStrategy.REPLACE);
            m.put(SQL_GENERATE_COUNT, KeyStrategy.REPLACE);
            m.put(SQL_REGENERATE_REASON, KeyStrategy.REPLACE);
            m.put(SQL_GENERATE_SCHEMA_MISSING_ADVICE, KeyStrategy.REPLACE);
            m.put(SEMANTIC_CONSISTENCY_NODE_OUTPUT, KeyStrategy.REPLACE);
            m.put(SQL_EXECUTE_NODE_OUTPUT, KeyStrategy.REPLACE);
            m.put(SQL_RESULT_LIST_MEMORY, KeyStrategy.REPLACE);

            // Plan 链路
            m.put(PLANNER_NODE_OUTPUT, KeyStrategy.REPLACE);
            m.put(PLAN_CURRENT_STEP, KeyStrategy.REPLACE);
            m.put(PLAN_NEXT_NODE, KeyStrategy.REPLACE);
            m.put(PLAN_VALIDATION_STATUS, KeyStrategy.REPLACE);
            m.put(PLAN_VALIDATION_ERROR, KeyStrategy.REPLACE);
            m.put(PLAN_REPAIR_COUNT, KeyStrategy.REPLACE);

            // Python 链路
            m.put(PYTHON_GENERATE_NODE_OUTPUT, KeyStrategy.REPLACE);
            m.put(PYTHON_EXECUTE_NODE_OUTPUT, KeyStrategy.REPLACE);
            m.put(PYTHON_ANALYSIS_NODE_OUTPUT, KeyStrategy.REPLACE);
            m.put(PYTHON_IS_SUCCESS, KeyStrategy.REPLACE);
            m.put(PYTHON_TRIES_COUNT, KeyStrategy.REPLACE);
            m.put(PYTHON_FALLBACK_MODE, KeyStrategy.REPLACE);

            // 模式开关
            m.put(IS_ONLY_NL2SQL, KeyStrategy.REPLACE);
            m.put(HUMAN_REVIEW_ENABLED, KeyStrategy.REPLACE);
            m.put(HUMAN_FEEDBACK_DATA, KeyStrategy.REPLACE);
            m.put(CLARIFICATION_NEXT_NODE, KeyStrategy.REPLACE);
            m.put(WAIT_FOR_CLARIFICATION, KeyStrategy.REPLACE);
            m.put(WAIT_FOR_CLARIFICATION_CONFIRM, KeyStrategy.REPLACE);
            m.put(MEMORY_CANDIDATE_NEXT_NODE, KeyStrategy.REPLACE);

            // 最终结果
            m.put(RESULT, KeyStrategy.REPLACE);
            return m;
        };

        // ==================== 2. 注册 16 个节点 ====================
        StateGraph graph = new StateGraph(NL2SQL_GRAPH_NAME, keyStrategyFactory)
                .addNode(INTENT_RECOGNITION_NODE, nodeBeanUtil.toAsyncNode(IntentRecognitionNode.class))
                .addNode(EVIDENCE_RECALL_NODE, nodeBeanUtil.toAsyncNode(EvidenceRecallNode.class))
                .addNode(QUERY_ENHANCE_NODE, nodeBeanUtil.toAsyncNode(QueryEnhanceNode.class))
                .addNode(SCHEMA_RECALL_NODE, nodeBeanUtil.toAsyncNode(SchemaRecallNode.class))
                .addNode(TABLE_RELATION_NODE, nodeBeanUtil.toAsyncNode(TableRelationNode.class))
                .addNode(FEASIBILITY_ASSESSMENT_NODE, nodeBeanUtil.toAsyncNode(FeasibilityAssessmentNode.class))
                .addNode(PLANNER_NODE, nodeBeanUtil.toAsyncNode(PlannerNode.class))
                .addNode(PLAN_EXECUTOR_NODE, nodeBeanUtil.toAsyncNode(PlanExecutorNode.class))
                .addNode(SQL_GENERATE_NODE, nodeBeanUtil.toAsyncNode(SqlGenerateNode.class))
                .addNode(SEMANTIC_CONSISTENCY_NODE, nodeBeanUtil.toAsyncNode(SemanticConsistencyNode.class))
                .addNode(SQL_EXECUTE_NODE, nodeBeanUtil.toAsyncNode(SqlExecuteNode.class))
                .addNode(PYTHON_GENERATE_NODE, nodeBeanUtil.toAsyncNode(PythonGenerateNode.class))
                .addNode(PYTHON_EXECUTE_NODE, nodeBeanUtil.toAsyncNode(PythonExecuteNode.class))
                .addNode(PYTHON_ANALYZE_NODE, nodeBeanUtil.toAsyncNode(PythonAnalyzeNode.class))
                .addNode(REPORT_GENERATOR_NODE, nodeBeanUtil.toAsyncNode(ReportGeneratorNode.class))
                .addNode(HUMAN_FEEDBACK_NODE, nodeBeanUtil.toAsyncNode(HumanFeedbackNode.class))
                .addNode(CLARIFICATION_ASK_NODE, nodeBeanUtil.toAsyncNode(ClarificationAskNode.class))
                .addNode(CLARIFICATION_NORMALIZE_NODE, nodeBeanUtil.toAsyncNode(ClarificationNormalizeNode.class))
                .addNode(CLARIFICATION_CONFIRM_NODE, nodeBeanUtil.toAsyncNode(ClarificationConfirmNode.class))
                .addNode(MEMORY_CANDIDATE_NODE, nodeBeanUtil.toAsyncNode(MemoryCandidateNode.class));

        // ==================== 3. 定义边和路由 ====================

        // 前置链路: 意图→证据→查询增强
        graph.addEdge(START, INTENT_RECOGNITION_NODE)
                .addConditionalEdges(INTENT_RECOGNITION_NODE,
                        edge_async(new IntentRecognitionDispatcher()),
                        Map.of(EVIDENCE_RECALL_NODE, EVIDENCE_RECALL_NODE, END, END))
                .addEdge(EVIDENCE_RECALL_NODE, QUERY_ENHANCE_NODE)
                .addConditionalEdges(QUERY_ENHANCE_NODE,
                        edge_async(new QueryEnhanceDispatcher()),
                        Map.of(SCHEMA_RECALL_NODE, SCHEMA_RECALL_NODE, END, END));

        // Schema→SQL 链路
        graph.addConditionalEdges(SCHEMA_RECALL_NODE,
                        edge_async(new SchemaRecallDispatcher()),
                        Map.of(TABLE_RELATION_NODE, TABLE_RELATION_NODE,
                                CLARIFICATION_ASK_NODE, CLARIFICATION_ASK_NODE,
                                END, END))
                .addConditionalEdges(TABLE_RELATION_NODE,
                        edge_async(new TableRelationDispatcher()),
                        Map.of(FEASIBILITY_ASSESSMENT_NODE, FEASIBILITY_ASSESSMENT_NODE,
                                TABLE_RELATION_NODE, TABLE_RELATION_NODE, END, END))
                .addConditionalEdges(FEASIBILITY_ASSESSMENT_NODE,
                        edge_async(new FeasibilityAssessmentDispatcher()),
                        Map.of(PLANNER_NODE, PLANNER_NODE,
                                CLARIFICATION_ASK_NODE, CLARIFICATION_ASK_NODE,
                                END, END))
                .addEdge(CLARIFICATION_ASK_NODE, CLARIFICATION_NORMALIZE_NODE)
                .addEdge(CLARIFICATION_NORMALIZE_NODE, CLARIFICATION_CONFIRM_NODE)
                .addConditionalEdges(CLARIFICATION_CONFIRM_NODE,
                        edge_async(new ClarificationConfirmDispatcher()),
                        Map.of(MEMORY_CANDIDATE_NODE, MEMORY_CANDIDATE_NODE,
                                SCHEMA_RECALL_NODE, SCHEMA_RECALL_NODE,
                                PLANNER_NODE, PLANNER_NODE,
                                END, END))
                .addConditionalEdges(MEMORY_CANDIDATE_NODE,
                        edge_async(new MemoryCandidateDispatcher()),
                        Map.of(SCHEMA_RECALL_NODE, SCHEMA_RECALL_NODE,
                                PLANNER_NODE, PLANNER_NODE,
                                END, END));

        // Plan 链路
        graph.addEdge(PLANNER_NODE, PLAN_EXECUTOR_NODE)
                .addConditionalEdges(PLAN_EXECUTOR_NODE,
                        edge_async(new PlanExecutorDispatcher()),
                        Map.of(PLANNER_NODE, PLANNER_NODE,
                                SQL_GENERATE_NODE, SQL_GENERATE_NODE,
                                PYTHON_GENERATE_NODE, PYTHON_GENERATE_NODE,
                                REPORT_GENERATOR_NODE, REPORT_GENERATOR_NODE,
                                HUMAN_FEEDBACK_NODE, HUMAN_FEEDBACK_NODE,
                                END, END));

        // SQL 生成→语义检查→执行
        graph.addConditionalEdges(SQL_GENERATE_NODE,
                        nodeBeanUtil.toAsyncEdge(SqlGenerateDispatcher.class),
                        Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE,
                                SEMANTIC_CONSISTENCY_NODE, SEMANTIC_CONSISTENCY_NODE, END, END))
                .addConditionalEdges(SEMANTIC_CONSISTENCY_NODE,
                        edge_async(new SemanticConsistenceDispatcher()),
                        Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE,
                                SQL_EXECUTE_NODE, SQL_EXECUTE_NODE))
                .addConditionalEdges(SQL_EXECUTE_NODE,
                        edge_async(new SQLExecutorDispatcher()),
                        Map.of(SQL_GENERATE_NODE, SQL_GENERATE_NODE,
                                PLAN_EXECUTOR_NODE, PLAN_EXECUTOR_NODE));

        // Python 链路
        graph.addEdge(PYTHON_GENERATE_NODE, PYTHON_EXECUTE_NODE)
                .addConditionalEdges(PYTHON_EXECUTE_NODE,
                        edge_async(new PythonExecutorDispatcher()),
                        Map.of(PYTHON_ANALYZE_NODE, PYTHON_ANALYZE_NODE,
                                PYTHON_GENERATE_NODE, PYTHON_GENERATE_NODE, END, END))
                .addEdge(PYTHON_ANALYZE_NODE, PLAN_EXECUTOR_NODE);

        // Human Feedback 链路
        graph.addConditionalEdges(HUMAN_FEEDBACK_NODE,
                edge_async(new HumanFeedbackDispatcher()),
                Map.of(PLANNER_NODE, PLANNER_NODE,
                        PLAN_EXECUTOR_NODE, PLAN_EXECUTOR_NODE, END, END));

        // Report → END
        graph.addEdge(REPORT_GENERATOR_NODE, END);

        // ==================== 4. 输出图结构 ====================
        GraphRepresentation repr = graph.getGraph(GraphRepresentation.Type.PLANTUML, "nl2sql workflow");
        log.info("工作流 PlantUML 图:\n\n{}\n", repr.content());

        return graph;
    }
}
