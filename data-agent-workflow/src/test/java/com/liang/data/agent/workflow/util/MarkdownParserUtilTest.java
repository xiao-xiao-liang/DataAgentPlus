package com.liang.data.agent.workflow.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownParserUtilTest {

    @Test
    void shouldUnwrapOnlyOuterMarkdownFence() {
        String markdown = """
                ```markdown
                # 系统会话全景分析报告

                ```sql
                SELECT COUNT(*) FROM t_conversation;
                ```

                ```echarts
                {"series":[{"type":"bar","data":[1]}]}
                ```
                ```
                """;

        String result = MarkdownParserUtil.unwrapOuterMarkdownFence(markdown);

        assertThat(result).startsWith("# 系统会话全景分析报告");
        assertThat(result).doesNotStartWith("```markdown");
        assertThat(result).contains("```sql");
        assertThat(result).contains("```echarts");
    }

    @Test
    void shouldKeepNormalMarkdownWithInnerCodeFence() {
        String markdown = """
                # 系统会话全景分析报告

                ```sql
                SELECT COUNT(*) FROM t_conversation;
                ```
                """;

        String result = MarkdownParserUtil.unwrapOuterMarkdownFence(markdown);

        assertThat(result).isEqualTo(markdown);
    }
}
