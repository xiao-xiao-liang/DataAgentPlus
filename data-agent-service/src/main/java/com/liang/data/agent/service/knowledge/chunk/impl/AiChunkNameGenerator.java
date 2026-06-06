package com.liang.data.agent.service.knowledge.chunk.impl;

import com.liang.data.agent.ai.llm.LlmService;
import com.liang.data.agent.service.knowledge.chunk.ChunkNameGenerator;
import com.liang.data.agent.service.knowledge.chunk.KnowledgeChunkProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 基于大语言模型的知识分块名称生成器。
 */
@Component
@RequiredArgsConstructor
public class AiChunkNameGenerator implements ChunkNameGenerator {

    private static final int MAX_NAME_LENGTH = 40;
    private final LlmService llmService;
    private final KnowledgeChunkProperties properties;

    @Override
    public String generate(String content, Integer chunkOrder) {
        try {
            String prompt = "请为以下知识分块生成一个不超过20个汉字的名称，只返回名称，不要解释：\n" + content;
            String generated = llmService.toStringFlux(llmService.callUser(prompt))
                    .collectList()
                    .map(parts -> String.join("", parts))
                    .block(properties.getAiNameTimeout());
            if (generated != null && !generated.isBlank()) {
                return normalize(generated);
            }
        } catch (RuntimeException ignored) {
            // AI 生成失败时使用正文首行降级，名称生成不阻塞分块维护。
        }
        return fallback(content, chunkOrder);
    }

    private String fallback(String content, Integer chunkOrder) {
        if (content != null) {
            for (String line : content.lines().toList()) {
                if (!line.isBlank()) {
                    return normalize(line);
                }
            }
        }
        return "分块 #" + chunkOrder;
    }

    private String normalize(String value) {
        String normalized = value.trim().replaceAll("^[\"'“”]+|[\"'“”]+$", "");
        return normalized.substring(0, Math.min(normalized.length(), MAX_NAME_LENGTH));
    }
}
