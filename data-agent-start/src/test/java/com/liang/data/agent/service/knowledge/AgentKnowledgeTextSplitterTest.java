package com.liang.data.agent.service.knowledge;

import com.liang.data.agent.service.knowledge.splitter.AgentKnowledgeSplitParam;
import com.liang.data.agent.service.knowledge.splitter.AgentKnowledgeTextSplitter;
import com.liang.data.agent.service.knowledge.vo.AgentKnowledgeChunkVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 智能体知识文本分块器单元测试。
 */
class AgentKnowledgeTextSplitterTest {

    private final AgentKnowledgeTextSplitter splitter = new AgentKnowledgeTextSplitter();

    @Test
    void lengthSplitterShouldContinuouslyCoverOriginalText() {
        String text = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        List<AgentKnowledgeChunkVO> chunks = splitter.split(1, text, new AgentKnowledgeSplitParam("length", 10, 3));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(rebuild(chunks)).isEqualTo(text);
    }

    @Test
    void titleSplitterShouldSplitLongBlockWithoutDroppingText() {
        String text = "# 第一章\n"
                + "第一段内容包含准点率、延误时长、运行区间。\n"
                + "第二段内容继续描述列车从A站到B站的实际耗时。\n"
                + "# 第二章\n"
                + "第三段内容描述整体准点率统计口径。";

        List<AgentKnowledgeChunkVO> chunks = splitter.split(2, text, new AgentKnowledgeSplitParam("title", 24, 5));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(rebuild(chunks)).isEqualTo(text);
    }

    @Test
    void titleSplitterShouldPreserveMarkdownStructureWhenRebuilt() {
        String text = "# 第一章\n\n"
                + "## 配置说明\n\n"
                + "- 一级列表\n"
                + "  - 二级列表不能丢缩进\n\n"
                + "```json\n"
                + "{\n"
                + "  \"name\": \"metro\"\n"
                + "}\n"
                + "```\n\n"
                + "![架构图](./images/arch.png)\n\n"
                + "## 第二章\n\n"
                + "正文内容继续。";

        List<AgentKnowledgeChunkVO> chunks = splitter.split(3, text, new AgentKnowledgeSplitParam("title", 40, 0));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(rebuild(chunks)).isEqualTo(text);
    }

    @Test
    void titleSplitterShouldNotBreakCodeFenceBlock() {
        String text = "# 示例\n\n"
                + "```json\n"
                + "{\n"
                + "  \"datasource\": \"metro\",\n"
                + "  \"enabled\": true\n"
                + "}\n"
                + "```\n\n"
                + "后续说明。";

        List<AgentKnowledgeChunkVO> chunks = splitter.split(4, text, new AgentKnowledgeSplitParam("title", 20, 0));

        assertThat(chunks).anySatisfy(chunk -> assertThat(chunk.getContent()).contains("```json\n"
                + "{\n"
                + "  \"datasource\": \"metro\",\n"
                + "  \"enabled\": true\n"
                + "}\n"
                + "```"));
        assertThat(rebuild(chunks)).isEqualTo(text);
    }

    private String rebuild(List<AgentKnowledgeChunkVO> chunks) {
        StringBuilder builder = new StringBuilder();
        for (AgentKnowledgeChunkVO chunk : chunks) {
            String content = chunk.getContent();
            if (builder.isEmpty()) {
                builder.append(content);
                continue;
            }
            int overlap = commonOverlap(builder, content);
            builder.append(content.substring(overlap));
        }
        return builder.toString();
    }

    private int commonOverlap(StringBuilder builder, String content) {
        int max = Math.min(builder.length(), content.length());
        for (int i = max; i > 0; i--) {
            if (builder.substring(builder.length() - i).equals(content.substring(0, i))) {
                return i;
            }
        }
        return 0;
    }
}
