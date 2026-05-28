package com.liang.data.agent.workflow.node;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.ai.vectorstore.AgentVectorStoreService;
import com.liang.data.agent.common.enums.TextType;
import com.liang.data.agent.common.enums.VectorType;
import com.liang.data.agent.dal.entity.AgentKnowledgeEntity;
import com.liang.data.agent.dal.mapper.AgentKnowledgeMapper;
import com.liang.data.agent.workflow.dto.node.EvidenceQueryRewriteDTO;
import com.liang.data.agent.workflow.prompt.PromptHelper;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.JsonParseUtil;
import com.liang.data.agent.workflow.util.MarkdownParserUtil;
import com.liang.data.agent.workflow.util.StateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.stream.Collectors;

import static com.liang.data.agent.common.constant.NodeOutputKey.EVIDENCE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.AGENT_ID;
import static com.liang.data.agent.common.constant.StateKey.INPUT_KEY;
import static com.liang.data.agent.common.constant.StateKey.MULTI_TURN_CONTEXT;

/**
 * 证据召回节点
 *
 * <p>工作流程:
 * 1. LLM 重写查询 (消解指代、补全上下文)
 * 2. 向量检索业务术语文档 (BUSINESS_TERM) 与智能体知识文档 (KNOWLEDGE)
 * 3. 批量拉取知识库实体，消除 N+1 查询风险
 * 4. 格式化并拼接证据输出
 * </p>
 *
 * <p>输出: EVIDENCE_OUTPUT -> 证据文本</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvidenceRecallNode implements NodeAction {

    private final LlmService llmService;
    private final JsonParseUtil jsonParseUtil;
    private final AgentVectorStoreService vectorStoreService;
    private final AgentKnowledgeMapper agentKnowledgeMapper;

    // 元数据常量定义
    private static final String METADATA_KNOWLEDGE_ID = "agentKnowledgeId";
    private static final String METADATA_KNOWLEDGE_TYPE = "concreteAgentKnowledgeType";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String userInput = StateUtil.getStringValue(state, INPUT_KEY);
        String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");
        String agentId = StateUtil.getStringValue(state, AGENT_ID);
        log.info("证据召回 - 问题: {}, agentId: {}", userInput, agentId);

        // 1. LLM 查询重写
        String prompt = PromptHelper.buildEvidenceQueryRewritePrompt(multiTurn, userInput);
        Flux<ChatResponse> responseFlux = llmService.callUser(prompt);
        Sinks.Many<String> evidenceDisplaySink = Sinks.many().multicast().onBackpressureBuffer();
        Map<String, Object> resultMap = new HashMap<>();

        // 1.1 LLM 重写 + 向量检索
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> rewriteAndRecallFlux = FluxUtil.createStreamingGenerator(
                this.getClass(), state, responseFlux,
                Flux.just(
                        ChatResponseUtil.createResponse("正在查询重写以更好召回证据..."),
                        ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())
                ),
                Flux.just(
                        ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
                        ChatResponseUtil.createResponse("\n查询重写完成！")
                ),
                result -> {
                    resultMap.putAll(getEvidences(result, agentId, evidenceDisplaySink));
                    return resultMap;
                }
        );

        // 1.2 证据展示
        Flux<GraphResponse<StreamingOutput<ChatResponse>>> evidenceFlux = FluxUtil.createStreamingGenerator(
                this.getClass(),
                state,
                evidenceDisplaySink.asFlux().map(ChatResponseUtil::createPureResponse),
                Flux.empty(),
                Flux.empty(),
                ignored -> resultMap
        );

        return Map.of(EVIDENCE_OUTPUT, rewriteAndRecallFlux.concatWith(evidenceFlux));
    }

    /**
     * 从向量库检索证据并拼接
     */
    private Map<String, Object> getEvidences(String llmOutput, String agentId, Sinks.Many<String> sink) {
        try {
            // 重写后的独立查询句（去多轮指代）
            String standaloneQuery = extractStandaloneQuery(llmOutput);
            if (!StringUtils.hasText(standaloneQuery)) {
                log.debug("查询重写结果为空");
                sink.tryEmitNext("未能进行查询重写！\n");
                return Map.of(EVIDENCE_OUTPUT, "无");
            }

            sink.tryEmitNext("重写后查询：\n");
            sink.tryEmitNext(standaloneQuery + "\n");
            sink.tryEmitNext("正在获取证据...\n");

            // 1. 双路并行向量库召回
            List<Document> knowledgeDocs = new ArrayList<>();
            List<Document> businessDocs = new ArrayList<>();

            collectDocuments(agentId, standaloneQuery, VectorType.KNOWLEDGE, knowledgeDocs);
            collectDocuments(agentId, standaloneQuery, VectorType.BUSINESS_TERM, businessDocs);

            List<Document> allDocuments = new ArrayList<>();
            allDocuments.addAll(businessDocs);
            allDocuments.addAll(knowledgeDocs);

            if (allDocuments.isEmpty()) {
                sink.tryEmitNext("未找到任何证据文档！\n");
                return Map.of(EVIDENCE_OUTPUT, "无");
            }

            sink.tryEmitNext("已找到 " + allDocuments.size() + " 条相关证据，准备处理来源...\n");

            // 2. 收集所有 KNOWLEDGE 文档关联的知识ID，进行批量预查询（消除 N+1 缺陷）
            Set<Integer> knowledgeIds = new HashSet<>();
            for (Document doc : knowledgeDocs) {
                Object kidVal = doc.getMetadata().get(METADATA_KNOWLEDGE_ID);
                if (kidVal != null) {
                    try {
                        if (kidVal instanceof Number num) {
                            knowledgeIds.add(num.intValue());
                        } else {
                            knowledgeIds.add(Integer.parseInt(kidVal.toString()));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            Map<Integer, AgentKnowledgeEntity> knowledgeMap = new HashMap<>();
            if (!knowledgeIds.isEmpty()) {
                try {
                    List<AgentKnowledgeEntity> entities = agentKnowledgeMapper.selectBatchIds(knowledgeIds);
                    if (entities != null) {
                        for (AgentKnowledgeEntity entity : entities) {
                            if (entity != null && entity.getId() != null) {
                                knowledgeMap.put(entity.getId(), entity);
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.error("批量查询知识库实体失败，ids: {}", knowledgeIds, ex);
                }
            }

            // 3. 构建证据字符串内容
            String businessKnowledgeContent = buildBusinessKnowledgeContent(businessDocs);
            String agentKnowledgeContent = buildAgentKnowledgeContent(knowledgeDocs, knowledgeMap);

            String businessPrompt = PromptHelper.buildBusinessKnowledgePrompt(businessKnowledgeContent);
            String agentPrompt = PromptHelper.buildAgentKnowledgePrompt(agentKnowledgeContent);

            String finalEvidence = businessKnowledgeContent.isEmpty() && agentKnowledgeContent.isEmpty() ? "无"
                    : businessPrompt + (agentKnowledgeContent.isEmpty() ? "" : "\n\n" + agentPrompt);

            // 4. 流式向前端输出部分摘要信息（防刷保护，限长100）
            for (int i = 0; i < allDocuments.size(); i++) {
                String text = allDocuments.get(i).getText();
                String summary = text.length() > 100 ? text.substring(0, 100) + "..." : text;
                sink.tryEmitNext(String.format("证据%d: %s\n", i + 1, summary));
            }

            log.info("证据召回与处理完成。术语数: {}，知识数: {}，总长: {}",
                    businessDocs.size(), knowledgeDocs.size(), finalEvidence.length());

            return Map.of(EVIDENCE_OUTPUT, finalEvidence);
        } catch (Exception e) {
            log.error("获取并处理证据时发生异常", e);
            sink.tryEmitNext("证据检索或数据拉取失败！\n");
            return Map.of(EVIDENCE_OUTPUT, "无");
        } finally {
            sink.tryEmitComplete();
        }
    }

    /**
     * 封装单类型向量库召回方法
     */
    private void collectDocuments(String agentId, String standaloneQuery, VectorType vectorType, List<Document> allDocuments) {
        try {
            List<Document> documents = vectorStoreService.search(agentId, standaloneQuery, vectorType);
            if (documents != null && !documents.isEmpty()) {
                allDocuments.addAll(documents);
            }
        } catch (Exception e) {
            log.warn("向量库检索出现异常，agentId: {}, vectorType: {}", agentId, vectorType.getCode(), e);
        }
    }

    /**
     * 构建业务术语明文（直接换行拼接）
     */
    private String buildBusinessKnowledgeContent(List<Document> businessDocs) {
        if (businessDocs.isEmpty()) {
            return "";
        }
        return businessDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 构建智能体专属知识文本（按类型回填来源描述，并对无匹配记录进行降级）
     */
    private String buildAgentKnowledgeContent(List<Document> knowledgeDocs, Map<Integer, AgentKnowledgeEntity> knowledgeMap) {
        if (knowledgeDocs.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < knowledgeDocs.size(); i++) {
            Document doc = knowledgeDocs.get(i);
            Map<String, Object> metadata = doc.getMetadata();
            
            String type = (String) metadata.get(METADATA_KNOWLEDGE_TYPE);
            Object kidVal = metadata.get(METADATA_KNOWLEDGE_ID);
            
            Integer kid = null;
            if (kidVal != null) {
                try {
                    kid = kidVal instanceof Number num ? num.intValue() : Integer.parseInt(kidVal.toString());
                } catch (NumberFormatException ignored) {}
            }

            AgentKnowledgeEntity entity = kid != null ? knowledgeMap.get(kid) : null;

            if (entity != null) {
                if ("FAQ".equalsIgnoreCase(type) || "QA".equalsIgnoreCase(type)) {
                    // FAQ/QA 类型格式化为: [来源: 标题] Q: 问 A: 答
                    String title = StringUtils.hasText(entity.getTitle()) ? entity.getTitle() : "常见问题";
                    sb.append(i + 1).append(". [来源: ").append(title)
                      .append("] Q: ").append(doc.getText())
                      .append(" A: ").append(Objects.toString(entity.getContent(), ""))
                      .append("\n");
                } else {
                    // DOCUMENT 类型格式化为: [来源: 标题-原始文件名] 内容
                    String title = StringUtils.hasText(entity.getTitle()) ? entity.getTitle() : "知识文档";
                    String sourceFile = StringUtils.hasText(entity.getSourceFilename()) ? "-" + entity.getSourceFilename() : "";
                    sb.append(i + 1).append(". [来源: ").append(title).append(sourceFile)
                      .append("] ").append(doc.getText())
                      .append("\n");
                }
            } else {
                // 实体查询不到或 ID 为空时进行优雅兜底降级，格式: [来源: 知识库] 内容
                sb.append(i + 1).append(". [来源: 知识库] ").append(doc.getText()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 抽取 Standalone 格式查询串
     */
    private String extractStandaloneQuery(String llmOutput) {
        try {
            String content = MarkdownParserUtil.extractRawText(llmOutput);
            EvidenceQueryRewriteDTO dto = jsonParseUtil.tryConvertToObject(content, EvidenceQueryRewriteDTO.class);
            return dto.getStandaloneQuery();
        } catch (Exception e) {
            log.error("提取/反序列化 EvidenceQueryRewriteDTO 发生失败", e);
            return null;
        }
    }
}