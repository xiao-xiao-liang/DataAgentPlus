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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.liang.data.agent.common.constant.NodeOutputKey.EVIDENCE_OUTPUT;
import static com.liang.data.agent.common.constant.StateKey.AGENT_ID;
import static com.liang.data.agent.common.constant.StateKey.INPUT_KEY;
import static com.liang.data.agent.common.constant.StateKey.MULTI_TURN_CONTEXT;

/**
 * 证据召回节点
 *
 * <p>工作流程:
 * 1. LLM 重写查询 (消解指代、补全上下文)
 * 2. 向量检索业务知识文档
 * 3. 向量检索智能体知识文档
 * 4. 拼接证据输出
 * </p>
 *
 * <p>输出: EVIDENCE -> 证据文本</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvidenceRecallNode implements NodeAction {

    private final LlmService llmService;
    private final JsonParseUtil jsonParseUtil;
    private final AgentVectorStoreService vectorStoreService;

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
     * 从向量库检索证据
     */
    private Map<String, Object> getEvidences(String llmOutput, String agentId, Sinks.Many<String> sink) {
        try {
            // 重写后的完整句子（消除了多轮对话中的指代词）
            String standaloneQuery = extractStandaloneQuery(llmOutput);
            if (!StringUtils.hasText(standaloneQuery)) {
                log.debug("查询重写结果为空");
                sink.tryEmitNext("未能进行查询重写！\n");
                return Map.of(EVIDENCE_OUTPUT, "无");
            }

            sink.tryEmitNext("重写后查询：\n");
            sink.tryEmitNext(standaloneQuery + "\n");
            sink.tryEmitNext("正在获取证据...\n");

            List<Document> allDocuments = new ArrayList<>();
            collectDocuments(agentId, standaloneQuery, VectorType.KNOWLEDGE, allDocuments);
//            collectDocuments(agentId, standaloneQuery, VectorType.BUSINESS_TERM, allDocuments);

            if (allDocuments.isEmpty()) {
                sink.tryEmitNext("未找到证据！\n");
                return Map.of(EVIDENCE_OUTPUT, "无");
            }

            // 构建证据内容
            sink.tryEmitNext("已找到 " + allDocuments.size() + " 条相关证据\n");
            StringBuilder evidence = new StringBuilder();
            for (int i = 0; i < allDocuments.size(); i++) {
                String content = allDocuments.get(i).getText();
                evidence.append(i + 1).append(". ").append(content).append("\n");
                String summary = content.length() > 100 ? content.substring(0, 100) + "..." : content;
                sink.tryEmitNext(String.format("证据%d: %s\n", i + 1, summary));
            }

            log.info("召回证据 {} 条", allDocuments.size());
            return Map.of(EVIDENCE_OUTPUT, evidence.toString());
        } catch (Exception e) {
            log.error("获取证据时发生异常", e);
            sink.tryEmitNext("证据检索失败！\n");
            return Map.of(EVIDENCE_OUTPUT, "无");
        } finally {
            sink.tryEmitComplete();
        }
    }

    private void collectDocuments(String agentId, String standaloneQuery, VectorType vectorType, List<Document> allDocuments) {
        try {
            List<Document> documents = vectorStoreService.search(agentId, standaloneQuery, vectorType);
            if (documents != null && !documents.isEmpty()) {
                allDocuments.addAll(documents);
            }
        } catch (Exception e) {
            log.warn("向量检索异常, agentId: {}, vectorType: {}", agentId, vectorType.getCode(), e);
        }
    }

    private String extractStandaloneQuery(String llmOutput) {
        try {
            String content = MarkdownParserUtil.extractRawText(llmOutput);
            EvidenceQueryRewriteDTO dto = jsonParseUtil.tryConvertToObject(content, EvidenceQueryRewriteDTO.class);
            return dto.getStandaloneQuery();
        } catch (Exception e) {
            log.error("解析查询重写结果失败", e);
            return null;
        }
    }
}