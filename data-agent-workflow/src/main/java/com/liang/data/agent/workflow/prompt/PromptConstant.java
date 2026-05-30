package com.liang.data.agent.workflow.prompt;

import com.liang.data.agent.ai.prompt.PromptLoader;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.prompt.PromptTemplate;

/**
 * Prompt 模板常量类
 *
 * <p>每个方法对应 resources/prompts/ 下的一个 .txt 模板文件，
 * 通过 PromptLoader 加载并包装为 Spring AI 的 PromptTemplate</p>
 *
 * <p>注意: 我们复用 ai-core 模块中已有的 PromptLoader (带缓存 + ServiceException 异常处理)，
 * 但 prompt 模板文件放在 workflow 模块的 resources/prompts/ 目录下</p>
 */
@NoArgsConstructor
public final class PromptConstant {

    /**
     * 意图识别
     */
    public static PromptTemplate getIntentRecognitionPromptTemplate() {
        return new PromptTemplate(PromptLoader.load("intent-recognition"));
    }

    /**
     * 证据查询重写
     */
    public static PromptTemplate getEvidenceQueryRewritePromptTemplate() {
        return new PromptTemplate(PromptLoader.load("evidence-query-rewrite"));
    }

    /**
     * 智能体知识模板
     */
    public static PromptTemplate getAgentKnowledgePromptTemplate() {
        return new PromptTemplate(PromptLoader.load("agent-knowledge"));
    }

    /**
     * 业务知识模板
     */
    public static PromptTemplate getBusinessKnowledgePromptTemplate() {
        return new PromptTemplate(PromptLoader.load("business-knowledge"));
    }

    /**
     * 查询增强
     */
    public static PromptTemplate getQueryEnhancementPromptTemplate() {
        return new PromptTemplate(PromptLoader.load("query-enhancement"));
    }

    /**
     * 可行性评估
     */
    public static PromptTemplate getFeasibilityAssessmentPromptTemplate() {
        return new PromptTemplate(PromptLoader.load("feasibility-assessment"));
    }

    /**
     * 混合选择器 (Schema 召回)
     */
    public static PromptTemplate getMixSelectorPromptTemplate() {
        return new PromptTemplate(PromptLoader.load("mix-selector"));
    }

    /**
     * 语义一致性校验
     */
    public static PromptTemplate getSemanticConsistencyPromptTemplate() {
        return new PromptTemplate(PromptLoader.load("semantic-consistency"));
    }

    /**
     * SQL 生成
     */
    public static PromptTemplate getNewSqlGeneratorPromptTemplate() {
        return new PromptTemplate(PromptLoader.load("new-sql-generate"));
    }

    /**
     * SQL 错误修复
     */
    public static PromptTemplate getSqlErrorFixerPromptTemplate() {
        return new PromptTemplate(PromptLoader.load("sql-error-fixer"));
    }

    /**
     * 执行计划生成
     */
    public static PromptTemplate getPlannerPromptTemplate() {
        return new PromptTemplate(PromptLoader.load("planner"));
    }

    /**
     * 报告生成 (纯文本)
     */
    public static PromptTemplate getReportGeneratorPlainPromptTemplate() {
        return new PromptTemplate(PromptLoader.load("report-generator-plain"));
    }

    /**
     * Python 代码生成
     */
    public static PromptTemplate getPythonGeneratorPromptTemplate() {
        return new PromptTemplate(PromptLoader.load("python-generator"));
    }

    /**
     * Python 结果分析
     */
    public static PromptTemplate getPythonAnalyzePromptTemplate() {
        return new PromptTemplate(PromptLoader.load("python-analyze"));
    }

    /**
     * 语义模型
     */
    public static PromptTemplate getSemanticModelPromptTemplate() {
        return new PromptTemplate(PromptLoader.load("semantic-model"));
    }

    /**
     * JSON 修复
     */
    public static PromptTemplate getJsonFixPromptTemplate() {
        return new PromptTemplate(PromptLoader.load("json-fix"));
    }

    /**
     * 数据视图分析
     */
    public static PromptTemplate getDataViewAnalyzePromptTemplate() {
        return new PromptTemplate(PromptLoader.load("data-view-analyze"));
    }

    public static PromptTemplate getClarificationNormalizePromptTemplate() {
        return new PromptTemplate(PromptLoader.load("clarification-normalize"));
    }

    public static PromptTemplate getMemoryCandidatePromptTemplate() {
        return new PromptTemplate(PromptLoader.load("memory-candidate"));
    }
}
