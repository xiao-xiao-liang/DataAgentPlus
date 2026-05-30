package com.liang.data.agent.workflow.prompt;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.converter.BeanOutputConverter;

import com.liang.data.agent.workflow.dto.node.*;
import com.liang.data.agent.common.schema.ColumnDTO;
import com.liang.data.agent.common.schema.SchemaDTO;
import com.liang.data.agent.common.schema.TableDTO;
import com.liang.data.agent.dal.entity.SemanticModelEntity;

import lombok.NoArgsConstructor;

/**
 * Prompt 构建辅助类
 *
 * <p>封装各节点的 Prompt 参数填充逻辑，将 DTO → 模板参数 Map → 渲染后的 Prompt 字符串</p>
 */
@NoArgsConstructor
public final class PromptHelper {

    /**
     * 将 SchemaDTO 格式化为 SQL 上下文 Prompt
     *
     * <p>输出格式:
     * 【DB_ID】 mydb
     * # Table: orders, 订单表
     * [(order_id:INT, 订单ID, Primary Key),
     * (user_id:INT, 用户ID)]
     * 【Foreign keys】
     * orders.user_id = users.id
     * </p>
     */
    public static String buildMixMacSqlDbPrompt(SchemaDTO schemaDTO, Boolean withColumnType) {
        StringBuilder sb = new StringBuilder();
        sb.append("【DB_ID】 ").append(schemaDTO.getName() == null ? "" : schemaDTO.getName()).append("\n");
        for (TableDTO tableDTO : schemaDTO.getTables()) {
            sb.append(buildMixMacSqlTablePrompt(tableDTO, withColumnType)).append("\n");
        }
        List<String> foreignKeys = schemaDTO.getForeignKeys();
        if (foreignKeys != null && !foreignKeys.isEmpty()) {
            sb.append("【Foreign keys】\n").append(String.join("\n", foreignKeys));
        }
        return sb.toString();
    }

    /**
     * 将单个 TableDTO 格式化为表描述
     */
    public static String buildMixMacSqlTablePrompt(TableDTO tableDTO, Boolean withColumnType) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Table: ").append(tableDTO.getName());
        if (!StringUtils.equals(tableDTO.getName(), tableDTO.getDescription())) {
            sb.append(StringUtils.isBlank(tableDTO.getDescription()) ? "" : ", " + tableDTO.getDescription());
        }
        sb.append("\n[");
        List<String> columnLines = new ArrayList<>();
        for (ColumnDTO columnDTO : tableDTO.getColumn()) {
            StringBuilder line = new StringBuilder();
            line.append("(").append(columnDTO.getName());
            if (Boolean.TRUE.equals(withColumnType)) {
                line.append(":").append(Objects.toString(columnDTO.getType(), "").toUpperCase(Locale.ROOT));
            }
            if (!StringUtils.equals(columnDTO.getDescription(), columnDTO.getName())) {
                line.append(", ").append((Objects.toString(columnDTO.getDescription(), "")));
            }
            if (tableDTO.getPrimaryKeys() != null && tableDTO.getPrimaryKeys().contains(columnDTO.getName())) {
                line.append(", Primary Key");
            }
            List<String> enumData = Optional.ofNullable(columnDTO.getData())
                    .orElse(new ArrayList<>())
                    .stream()
                    .filter(d -> !StringUtils.isEmpty(d))
                    .collect(Collectors.toList());
            if (!enumData.isEmpty() && !"id".equals(columnDTO.getName())) {
                line.append(", Examples: [");
                List<String> data = new ArrayList<>(enumData.subList(0, Math.min(3, enumData.size())));
                line.append(String.join(",", data)).append("]");
            }
            line.append(")");
            columnLines.add(line.toString());
        }
        sb.append("\n").append(String.join(",\n", columnLines));
        sb.append("\n]");
        return sb.toString();
    }

    // ==================== 节点 Prompt 构建 ====================

    /**
     * 构建意图识别 Prompt
     */
    public static String buildIntentRecognitionPrompt(String multiTurn, String latestQuery) {
        Map<String, Object> params = new HashMap<>();
        params.put("multi_turn", multiTurn != null ? multiTurn : "(无)"); // 多轮对话上下文
        params.put("latest_query", latestQuery); // 当前用户最新问题
        var converter = new BeanOutputConverter<>(IntentRecognitionOutputDTO.class);
        params.put("format", converter.getFormat()); // 要求模型按什么结构输出
        return PromptConstant.getIntentRecognitionPromptTemplate().render(params);
    }

    /**
     * 构建证据查询重写 Prompt
     */
    public static String buildEvidenceQueryRewritePrompt(String multiTurn, String latestQuery) {
        Map<String, Object> params = new HashMap<>();
        params.put("multi_turn", multiTurn != null ? multiTurn : "(无)");
        params.put("latest_query", latestQuery);
        var converter = new BeanOutputConverter<>(EvidenceQueryRewriteDTO.class);
        params.put("format", converter.getFormat());
        return PromptConstant.getEvidenceQueryRewritePromptTemplate().render(params);
    }

    /**
     * 构建查询增强 Prompt
     */
    public static String buildQueryEnhancePrompt(String multiTurn, String latestQuery, String evidence) {
        Map<String, Object> params = new HashMap<>();
        params.put("multi_turn", multiTurn != null ? multiTurn : "(无)");
        params.put("latest_query", latestQuery);
        params.put("evidence", StringUtils.isBlank(evidence) ? "无" : evidence); // 召回出来的业务证据、知识片段
        params.put("current_time_info", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        var converter = new BeanOutputConverter<>(QueryEnhanceOutputDTO.class);
        params.put("format", converter.getFormat());
        return PromptConstant.getQueryEnhancementPromptTemplate().render(params);
    }

    /**
     * 构建可行性评估 Prompt
     */
    public static String buildFeasibilityAssessmentPrompt(String canonicalQuery, SchemaDTO recalledSchema, String evidence, String multiTurn) {
        Map<String, Object> params = new HashMap<>();
        String schemaInfo = buildMixMacSqlDbPrompt(recalledSchema, true);
        params.put("canonical_query", canonicalQuery != null ? canonicalQuery : ""); // 规范化后的问题
        params.put("recalled_schema", schemaInfo);
        params.put("evidence", evidence != null ? evidence : "");
        params.put("multi_turn", multiTurn != null ? multiTurn : "(无)");
        var converter = new BeanOutputConverter<>(FeasibilityAssessmentOutputDTO.class);
        params.put("format", converter.getFormat());
        return PromptConstant.getFeasibilityAssessmentPromptTemplate().render(params);
    }

    /**
     * 构建混合选择器 Prompt (Schema 召回)
     */
    public static String buildClarificationNormalizePrompt(String originalQuestion,
                                                           String clarificationQuestion,
                                                           String userAnswer,
                                                           String evidence) {
        Map<String, Object> params = new HashMap<>();
        params.put("original_question", StringUtils.defaultIfBlank(originalQuestion, ""));
        params.put("clarification_question", StringUtils.defaultIfBlank(clarificationQuestion, ""));
        params.put("user_answer", StringUtils.defaultIfBlank(userAnswer, ""));
        params.put("evidence", StringUtils.defaultIfBlank(evidence, ""));
        var converter = new BeanOutputConverter<>(ClarificationNormalizedDTO.class);
        params.put("format", converter.getFormat());
        return PromptConstant.getClarificationNormalizePromptTemplate().render(params);
    }

    public static String buildMemoryCandidatePrompt(String sourceQuestion, String clarificationEvidence) {
        Map<String, Object> params = new HashMap<>();
        params.put("source_question", StringUtils.defaultIfBlank(sourceQuestion, ""));
        params.put("clarification_evidence", StringUtils.defaultIfBlank(clarificationEvidence, ""));
        var converter = new BeanOutputConverter<>(MemoryCandidateOutputDTO.class);
        params.put("format", converter.getFormat());
        return PromptConstant.getMemoryCandidatePromptTemplate().render(params);
    }

    public static String buildMixSelectorPrompt(String evidence, String question, SchemaDTO schemaDTO, String advice) {
        String schemaInfo = buildMixMacSqlDbPrompt(schemaDTO, true);
        Map<String, Object> params = new HashMap<>();
        params.put("schema_info", schemaInfo); // 数据库 schema 描述
        params.put("question", question);
        params.put("evidence", StringUtils.isBlank(evidence) ? "无" : evidence);
        params.put("advice", StringUtils.isBlank(advice) ? "无" : advice);
        return PromptConstant.getMixSelectorPromptTemplate().render(params);
    }

    public static String buildMixSelectorPrompt(String evidence, String question, SchemaDTO schemaDTO) {
        return buildMixSelectorPrompt(evidence, question, schemaDTO, null);
    }

    /**
     * 构建 SQL 生成 Prompt
     */
    public static String buildNewSqlGeneratorPrompt(SqlGenerationDTO dto) {
        String schemaInfo = buildMixMacSqlDbPrompt(dto.getSchemaInfo(), true);
        Map<String, Object> params = new HashMap<>();
        params.put("dialect", dto.getDialect()); // SQL 方言，比如 MySQL / PostgreSQL
        params.put("question", dto.getQuery());
        params.put("schema_info", schemaInfo);
        params.put("evidence", dto.getEvidence());
        params.put("execution_description", dto.getExecutionDescription()); // 执行步骤/计划上下文
        return PromptConstant.getNewSqlGeneratorPromptTemplate().render(params);
    }

    /**
     * 构建 SQL 错误修复 Prompt
     */
    public static String buildSqlErrorFixerPrompt(SqlGenerationDTO dto) {
        String schemaInfo = buildMixMacSqlDbPrompt(dto.getSchemaInfo(), true);
        Map<String, Object> params = new HashMap<>();
        params.put("dialect", dto.getDialect());
        params.put("question", dto.getQuery());
        params.put("schema_info", schemaInfo);
        params.put("evidence", dto.getEvidence());
        params.put("error_sql", dto.getSql()); // SQL 修复场景下的错误输入
        params.put("error_message", dto.getExceptionMessage()); // SQL 修复场景下的错误输入
        params.put("execution_description", dto.getExecutionDescription());
        return PromptConstant.getSqlErrorFixerPromptTemplate().render(params);
    }

    /**
     * 构建语义一致性检查 Prompt
     */
    public static String buildSemanticConsistencyPrompt(SemanticConsistencyDTO dto) {
        Map<String, Object> params = new HashMap<>();
        params.put("dialect", dto.getDialect());
        params.put("execution_description", dto.getExecutionDescription());
        params.put("user_query", dto.getUserQuery());
        params.put("evidence", dto.getEvidence());
        params.put("schema_info", dto.getSchemaInfo());
        params.put("sql", dto.getSql());
        return PromptConstant.getSemanticConsistencyPromptTemplate().render(params);
    }

    /**
     * 构建业务知识 Prompt
     */
    public static String buildBusinessKnowledgePrompt(String businessTerms) {
        Map<String, Object> params = new HashMap<>();
        params.put("businessKnowledge", StringUtils.isNotBlank(businessTerms) ? businessTerms : "无"); // 额外知识注入
        return PromptConstant.getBusinessKnowledgePromptTemplate().render(params);
    }

    /**
     * 构建智能体知识 Prompt
     */
    public static String buildAgentKnowledgePrompt(String agentKnowledge) {
        Map<String, Object> params = new HashMap<>();
        params.put("agentKnowledge", StringUtils.isNotBlank(agentKnowledge) ? agentKnowledge : "无"); // 额外知识注入
        return PromptConstant.getAgentKnowledgePromptTemplate().render(params);
    }

    /**
     * 构建报告生成 Prompt (简化版，暂不支持 UserPromptConfig 优化)
     */
    public static String buildReportGeneratorPrompt(String userRequirementsAndPlan, String analysisStepsAndData, String summaryAndRecommendations) {
        Map<String, Object> params = new HashMap<>();
        // 报告生成所需材料
        params.put("user_requirements_and_plan", userRequirementsAndPlan);
        params.put("analysis_steps_and_data", analysisStepsAndData);
        params.put("summary_and_recommendations", summaryAndRecommendations);
        params.put("json_example", ""); // TODO: Phase 5+ 添加图表 JSON 示例
        params.put("optimization_section", "");
        return PromptConstant.getReportGeneratorPlainPromptTemplate().render(params);
    }

    /**
     * 构建语义模型 Prompt
     */
    public static String buildSemanticModelPrompt(List<SemanticModelEntity> semanticModels) {
        Map<String, Object> params = new HashMap<>();
        String semanticModel = (semanticModels == null || semanticModels.isEmpty()) ? ""
                : semanticModels.stream().map(sm -> String.format(
                        "对话字段名称: %s; 数据库字段名: %s; 字段同义词: %s; 字段描述: %s; 字段类型: %s",
                        sm.getBusinessName(),
                        sm.getTableName() + "." + sm.getColumnName(),
                        Objects.toString(sm.getSynonyms(), ""),
                        Objects.toString(sm.getBusinessDescription(), ""),
                        Objects.toString(sm.getDataType(), "")
                )).collect(Collectors.joining(";\n"));
        params.put("semanticModel", semanticModel);
        return PromptConstant.getSemanticModelPromptTemplate().render(params);
    }

    // ==================== Phase 7 新增方法 ====================

    /**
     * 构建 Python 分析 Prompt
     *
     * <p>将用户查询和 Python 执行输出填充到 python-analyze.txt 模板中</p>
     */
    public static String buildPythonAnalyzePrompt(String userQuery, String pythonOutput) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_query", StringUtils.isBlank(userQuery) ? "数据分析" : userQuery);
        params.put("python_output", StringUtils.isBlank(pythonOutput) ? "{}" : pythonOutput);
        return PromptConstant.getPythonAnalyzePromptTemplate().render(params);
    }

    /**
     * 构建数据视图分析 Prompt（用于 SQL 结果图表类型推荐）
     *
     * <p>data-view-analyze.txt 模板分为系统提示词和用户提示词两部分，
     * 以 "=== 用户输入 ===" 分隔。此方法只返回系统提示词部分。</p>
     */
    public static String buildDataViewAnalyzeSystemPrompt() {
        String fullPrompt = PromptConstant.getDataViewAnalyzePromptTemplate().render(
                Map.of("format", DISPLAY_STYLE_FORMAT));
        // 分割系统提示词和用户提示词模板
        String[] parts = fullPrompt.split("=== 用户输入 ===", 2);
        return parts[0].trim();
    }

    /**
     * 构建数据视图分析的用户提示词
     */
    public static String buildDataViewAnalyzeUserPrompt(String userQuery, String sampleDataJson) {
        return String.format("""
                # 正式任务

                <最新>用户输入: %s
                范例数据: %s

                # 输出
                """, StringUtils.isBlank(userQuery) ? "数据可视化" : userQuery, sampleDataJson);
    }

    /**
     * 构建结构化的用户需求与执行计划描述（用于报告生成）
     */
    public static String buildUserRequirementsAndPlan(String userInput, com.liang.data.agent.workflow.dto.planner.Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户原始需求\n");
        sb.append(userInput).append("\n\n");

        if (plan.getThoughtProcess() != null) {
            sb.append("## 执行计划概述\n");
            sb.append("**思考过程**: ").append(plan.getThoughtProcess()).append("\n\n");
        }

        sb.append("## 详细执行步骤\n");
        var executionPlan = plan.getExecutionPlan();
        if (executionPlan != null) {
            for (int i = 0; i < executionPlan.size(); i++) {
                var step = executionPlan.get(i);
                sb.append("### 步骤 ").append(i + 1).append(": 编号 ").append(step.getStep()).append("\n");
                sb.append("**工具**: ").append(step.getToolToUse()).append("\n");
                if (step.getToolParameters() != null) {
                    sb.append("**参数描述**: ").append(step.getToolParameters().getInstruction()).append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 构建结构化的分析步骤与数据描述（用于报告生成）
     */
    public static String buildAnalysisStepsAndData(com.liang.data.agent.workflow.dto.planner.Plan plan, Map<String, String> executionResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 数据执行结果\n");

        if (executionResults == null || executionResults.isEmpty()) {
            sb.append("暂无执行结果数据\n");
            return sb.toString();
        }

        var executionPlan = plan.getExecutionPlan();
        for (Map.Entry<String, String> entry : executionResults.entrySet()) {
            String stepKey = entry.getKey();
            String stepResult = entry.getValue();

            // 跳过 _analysis 后缀的条目（稍后关联展示）
            if (stepKey.endsWith("_analysis")) {
                continue;
            }

            sb.append("### ").append(stepKey).append("\n");

            // 尝试关联步骤描述
            try {
                int stepIndex = Integer.parseInt(stepKey.replace("step_", "")) - 1;
                if (executionPlan != null && stepIndex >= 0 && stepIndex < executionPlan.size()) {
                    var step = executionPlan.get(stepIndex);
                    sb.append("**步骤编号**: ").append(step.getStep()).append("\n");
                    sb.append("**使用工具**: ").append(step.getToolToUse()).append("\n");
                    if (step.getToolParameters() != null) {
                        sb.append("**参数描述**: ").append(step.getToolParameters().getInstruction()).append("\n");
                        if (step.getToolParameters().getSqlQuery() != null) {
                            sb.append("**执行SQL**: \n```sql\n")
                                    .append(step.getToolParameters().getSqlQuery())
                                    .append("\n```\n");
                        }
                    }
                }
            } catch (NumberFormatException ignored) {
                // 忽略 step key 解析异常
            }

            sb.append("**执行结果**: \n```json\n").append(stepResult).append("\n```\n\n");

            // 关联 Python 分析结果
            String analysisKey = stepKey + "_analysis";
            String analysisResult = executionResults.get(analysisKey);
            if (analysisResult != null && !analysisResult.trim().isEmpty()) {
                sb.append("**Python 分析结果**: ").append(analysisResult).append("\n\n");
            }
        }

        return sb.toString();
    }

    /**
     * DisplayStyleBO 的 JSON 格式描述（用于 LLM 输出约束）
     */
    private static final String DISPLAY_STYLE_FORMAT = """
            {"type": "图表类型(table/column/bar/line/pie)", "title": "图表标题", "x": "X轴字段名", "y": "Y轴字段名"}
            """;
}
