package com.liang.data.agent.workflow.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.ai.util.ChatResponseUtil;
import com.liang.data.agent.common.schema.SchemaDTO;
import com.liang.data.agent.dal.connector.bo.DbConfigBO;
import com.liang.data.agent.gateway.constants.ModelGatewayConstant;
import com.liang.data.agent.workflow.dto.node.SemanticConsistencyDTO;
import com.liang.data.agent.workflow.dto.node.SqlGenerationDTO;
import com.liang.data.agent.workflow.prompt.PromptHelper;
import com.liang.data.agent.workflow.service.Nl2SqlService;
import com.liang.data.agent.workflow.util.JsonParseUtil;
import com.liang.data.agent.workflow.util.MarkdownParserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NL2SQL 核心服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Nl2SqlServiceImpl implements Nl2SqlService {

    private final LlmService llmService;
    private final JsonParseUtil jsonParseUtil;

    @Override
    public Flux<ChatResponse> generateSql(SqlGenerationDTO dto) {
        log.info("开始生成 SQL - 问题: {}, 数据库方言: {}", dto.getQuery(), dto.getDialect());
        String prompt = PromptHelper.buildNewSqlGeneratorPrompt(dto);
        log.debug("SQL 生成 Prompt: \n{}", prompt);
        return llmService.callUser(ModelGatewayConstant.SQL_GENERATION, prompt);
    }

    @Override
    public Flux<ChatResponse> fixSql(SqlGenerationDTO dto) {
        log.info("开始修复 SQL - 异常信息: {}", dto.getExceptionMessage());
        String prompt = PromptHelper.buildSqlErrorFixerPrompt(dto);
        log.debug("SQL 修复 Prompt: \n{}", prompt);
        return llmService.callUser(ModelGatewayConstant.SQL_REPAIR, prompt);
    }

    @Override
    public Flux<ChatResponse> fineSelect(SchemaDTO schema, String input, String evidence,
                                         String advice, DbConfigBO dbConfig, Consumer<SchemaDTO> consumer) {
        log.info("开始 Schema 精细选择 - 建议: {}", advice);

        // 1. 调用优化后的 PromptHelper (单轮 Prompt 内置重试建议，避免原版低效的串行双 LLM 级联调用)
        String prompt = PromptHelper.buildMixSelectorPrompt(evidence, input, schema, advice);
        log.debug("Schema 精选 Selector Prompt: \n{}", prompt);

        StringBuilder textAccumulator = new StringBuilder();

        // 2. 调用大模型并流式累加文本
        return llmService.callUser(ModelGatewayConstant.SCHEMA_MIX_SELECT, prompt)
                .doOnNext(chatResponse -> {
                    String piece = ChatResponseUtil.getText(chatResponse);
                    textAccumulator.append(piece);
                })
                .doOnComplete(() -> {
                    String fullResponse = textAccumulator.toString();
                    log.info("Schema 精细选择 LLM 响应结束，响应长度: {}", fullResponse.length());

                    // 3. 鲁棒的 JSON 数组与兜底正则匹配解析
                    List<String> selectedTableNames = parseTableNamesRobustly(fullResponse);
                    log.info("选中的表名列表: {}", selectedTableNames);

                    // 4. 精细过滤并缩减 SchemaDTO
                    if (schema.getTables() != null) {
                        int originSize = schema.getTables().size();
                        schema.getTables().removeIf(table ->
                                !selectedTableNames.contains(table.getName().toLowerCase())
                        );
                        log.info("Schema 表过滤完成: {} -> {} 张表", originSize, schema.getTables().size());
                    }

                    // 5. 触发回调将精炼后的 SchemaDTO 回传给 TableRelationNode
                    consumer.accept(schema);
                });
    }

    @Override
    public Flux<ChatResponse> checkSemanticConsistency(SemanticConsistencyDTO dto) {
        log.info("开始语义一致性检查");
        String prompt = PromptHelper.buildSemanticConsistencyPrompt(dto);
        log.debug("语义一致性检查 Prompt: \n{}", prompt);
        return llmService.callUser(ModelGatewayConstant.SEMANTIC_CONSISTENCY, prompt);
    }

    /**
     * 鲁棒解析表名列表：先尝试 Jackson 标准反序列化；若失败，启用正则兜底匹配，保障抗幻觉能力。
     */
    private List<String> parseTableNamesRobustly(String content) {
        if (StringUtils.isBlank(content)) {
            return Collections.emptyList();
        }

        try {
            // 尝试提取 Markdown 代码块中的 JSON 纯文本并解析
            String jsonText = MarkdownParserUtil.extractRawText(content);
            List<String> parsedList = jsonParseUtil.tryConvertToObject(jsonText, new TypeReference<>() {
            });
            if (parsedList != null) {
                return parsedList.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("fineSelect 返回 JSON 反序列化解析失败，启用正则匹配兜底解析。");
        }

        // 正则表达式匹配所有的引号内容作为兜底（支持单/双引号引起来的表名）
        Set<String> fallbackTableNames = new HashSet<>();
        Pattern pattern = Pattern.compile("[\"']([a-zA-Z_][a-zA-Z0-9_]*)[\"']");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            fallbackTableNames.add(matcher.group(1).trim().toLowerCase());
        }
        return new ArrayList<>(fallbackTableNames);
    }
}
