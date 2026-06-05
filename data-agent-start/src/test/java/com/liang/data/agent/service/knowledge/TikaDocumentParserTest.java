package com.liang.data.agent.service.knowledge;

import com.liang.data.agent.service.knowledge.parser.TikaDocumentParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 智能体知识文档解析器单元测试。
 */
class TikaDocumentParserTest {

    private final TikaDocumentParser parser = new TikaDocumentParser();

    @Test
    void markdownParserShouldPreserveOriginalStructure() {
        String markdown = "# 标题\r\n\r\n"
                + "- 一级列表\r\n"
                + "  - 二级列表保留缩进\r\n\r\n"
                + "```json\r\n"
                + "{\r\n"
                + "\t\"name\": \"metro\"\r\n"
                + "}\r\n"
                + "```\r\n\r\n\r\n"
                + "结尾段落  \r\n";

        String parsed = parser.parse(markdown.getBytes(StandardCharsets.UTF_8), "demo.md", "md");

        assertThat(parsed).isEqualTo(markdown.replace("\r\n", "\n").replace("\r", "\n"));
    }
}
