package com.liang.data.agent.controller;

import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.model.AiModelRegistry;
import com.liang.data.agent.ai.prompt.DefaultPromptContributorManager;
import com.liang.data.agent.ai.prompt.PromptContribution;
import com.liang.data.agent.ai.prompt.PromptContributorContext;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.ai.vectorstore.AgentVectorStoreService;
import com.liang.data.agent.common.enums.TextType;
import com.liang.data.agent.common.enums.VectorType;
import com.liang.data.agent.gateway.api.ModelGatewayScenes;
import com.liang.data.agent.workflow.dto.node.EvidenceQueryRewriteDTO;
import com.liang.data.agent.workflow.prompt.PromptHelper;
import com.liang.data.agent.workflow.util.FluxUtil;
import com.liang.data.agent.workflow.util.JsonParseUtil;
import com.liang.data.agent.workflow.util.MarkdownParserUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/test/phase3")
@RequiredArgsConstructor
public class Phase3TestController {

    private final LlmService llmService;
    private final AiModelRegistry registry;
    private final DefaultPromptContributorManager promptManager;
    private final AgentVectorStoreService vectorStoreService;
    private final JsonParseUtil jsonParseUtil;

    /**
     * 验证 1: LLM 连通性与模型动态加载
     * 访问: http://localhost:18080/test/phase3/chat
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> testChat() {
        return llmService.callUser(ModelGatewayScenes.DIAGNOSTIC_CHAT, "你好，请做个详细的自我介绍。")
                .map(ChatResponseUtil::getText)
                .filter(StringUtils::hasLength)
                .doOnNext(text -> log.info("【Chat接口】输出数据片段: [{}]", text));
    }

    /**
     * 验证前三节点的串联流式输出效果
     * 访问: POST /test/phase3/stream-three-nodes
     */
    @PostMapping(value = "/stream-three-nodes", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> streamThreeNodes(@RequestBody StreamThreeNodesRequest request) {
        String query = request.getQuery();
        String multiTurn = StringUtils.hasText(request.getMultiTurn()) ? request.getMultiTurn() : "(无)";

        Flux<String> intentSource = llmService.callUser(ModelGatewayScenes.INTENT_RECOGNITION,
                        PromptHelper.buildIntentRecognitionPrompt(multiTurn, query))
                .map(ChatResponseUtil::getText)
                .filter(StringUtils::hasLength);

        return FluxUtil.cascadeFlux(
                intentSource,
                intentOutput -> buildEvidenceRecallFlux(request, multiTurn, query, intentOutput),
                this::collectText,
                Flux.just("正在进行意图识别...\n", TextType.JSON.getStartSign()),
                Flux.just(TextType.JSON.getEndSign(), "\n意图识别完成！\n"),
                Flux.empty()
        ).doOnNext(text -> log.info("【三节点流】输出数据片段: [{}]", text));
    }

    /**
     * 验证 2: 动态刷新功能
     * 访问: http://localhost:18080/test/phase3/refresh
     * 验证方式：修改数据库的 apiKey 成一个错误的，调一下这个接口，再调 chat 接口看是否报错
     */
    @GetMapping("/refresh")
    public String testRefresh() {
        registry.refreshChat();
        registry.refreshEmbedding();
        return "模型缓存已清理，下次调用将重新从数据库加载配置。";
    }

    /**
     * 验证 3: Prompt SPI 组装管理器
     * 访问: http://localhost:18080/test/phase3/prompt
     */
    @GetMapping("/prompt")
    public String testPromptManager() {
        PromptContributorContext context = new PromptContributorContext() {
            @Override
            public List<org.springframework.ai.chat.messages.Message> getMessages() {
                return List.of();
            }

            @Override
            public Optional<org.springframework.ai.chat.messages.SystemMessage> getSystemMessage() {
                return Optional.empty();
            }

            @Override
            public Map<String, Object> getAttributes() {
                return Map.of();
            }

            @Override
            public Optional<String> getPhase() {
                return Optional.empty();
            }
        };

        PromptContribution contribution = promptManager.contribute(context);
        return "目前注册的 PromptContributor 数量: " + promptManager.getContributors().size() + "\n"
                + "组装后的结果是否为空: " + contribution.isEmpty();
    }

    /**
     * 验证 4: VectorStore (需要确保 Milvus Docker 正常启动并已配置 Embeding 模型)
     * 访问: http://localhost:18080/test/phase3/vector
     */
    @GetMapping("/vector")
    public String testVectorStore() {
        Document doc = new Document("向量检索测试文档: MySQL 是一种关系型数据库。");
        doc.getMetadata().put("agent_id", "test-agent-001");
        doc.getMetadata().put("vector_type", VectorType.KNOWLEDGE.getCode());

        vectorStoreService.addDocuments("test-agent-001", List.of(doc));

        List<Document> results = vectorStoreService.search("test-agent-001", "什么是 MySQL？", VectorType.KNOWLEDGE, 3, 0.1);

        return "插入成功，并成功检索到: " + results.size() + " 条记录。\n"
                + "第一条内容: " + (results.isEmpty() ? "无" : results.getFirst().getText());
    }

    private Flux<String> buildEvidenceRecallFlux(StreamThreeNodesRequest request, String multiTurn, String query,
                                                 String intentOutput) {
        Flux<String> evidenceSource = llmService.callUser(ModelGatewayScenes.EVIDENCE_RECALL,
                        PromptHelper.buildEvidenceQueryRewritePrompt(multiTurn, query))
                .map(ChatResponseUtil::getText)
                .filter(StringUtils::hasLength);

        return FluxUtil.cascadeFlux(
                evidenceSource,
                rewriteOutput -> buildQueryEnhanceFlux(multiTurn, query, buildEvidenceText(request.getAgentId(), rewriteOutput)),
                this::collectText,
                Flux.just("正在查询重写以更好召回证据...\n", TextType.JSON.getStartSign()),
                Flux.just(TextType.JSON.getEndSign(), "\n查询重写完成！\n"),
                Flux.empty()
        );
    }

    private Flux<String> buildQueryEnhanceFlux(String multiTurn, String query, String evidence) {
        Flux<String> queryEnhanceSource = llmService.callUser(ModelGatewayScenes.QUERY_ENHANCE,
                        PromptHelper.buildQueryEnhancePrompt(multiTurn, query, evidence))
                .map(ChatResponseUtil::getText)
                .filter(StringUtils::hasLength);

        return Flux.concat(
                Flux.just(evidence, "正在进行问题增强...\n", TextType.JSON.getStartSign()),
                queryEnhanceSource,
                Flux.just(TextType.JSON.getEndSign(), "\n问题增强完成！\n")
        );
    }

    private String buildEvidenceText(String agentId, String rewriteOutput) {
        String standaloneQuery = extractStandaloneQuery(rewriteOutput);
        if (!StringUtils.hasText(standaloneQuery)) {
            return "未能进行查询重写！\n无\n";
        }

        StringBuilder streamText = new StringBuilder();
        streamText.append("重写后查询：\n");
        streamText.append(standaloneQuery).append("\n");
        streamText.append("正在获取证据...\n");

        List<Document> documents = vectorStoreService.search(agentId, standaloneQuery, VectorType.KNOWLEDGE);
        if (documents == null || documents.isEmpty()) {
            streamText.append("未找到证据！\n无\n");
            return streamText.toString();
        }

        streamText.append("已找到 ").append(documents.size()).append(" 条相关证据\n");
        for (int i = 0; i < documents.size(); i++) {
            String content = documents.get(i).getText();
            streamText.append("证据").append(i + 1).append(": ").append(summarize(content)).append("\n");
        }
        return streamText.toString();
    }

    private String extractStandaloneQuery(String llmOutput) {
        try {
            String content = MarkdownParserUtil.extractRawText(llmOutput);
            EvidenceQueryRewriteDTO dto = jsonParseUtil.tryConvertToObject(content, EvidenceQueryRewriteDTO.class);
            return dto.getStandaloneQuery();
        }
        catch (Exception ignored) {
            return null;
        }
    }

    private Mono<String> collectText(Flux<String> flux) {
        return flux.collectList().map(parts -> String.join("", parts));
    }

    private String summarize(String content) {
        return content.length() > 100 ? content.substring(0, 100) + "..." : content;
    }

    @Data
    public static class StreamThreeNodesRequest {
        private String agentId;
        private String query;
        private String multiTurn;
    }
}
