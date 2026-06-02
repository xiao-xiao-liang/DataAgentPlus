import assert from 'node:assert/strict';
import { createServer } from 'vite';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

const reportMarkdown = `# Knowledge Report

## 1. Summary
- This report covers **3 knowledge bases** and **7 documents**.

### 3.2 Result Set
| Knowledge Base | Document | Chunk Count |
| :--- | :--- | :--- |
| HR | policy.md | 5 |
| Training | training-v1.docx | 256 |

\`\`\`echarts
{
  "title": { "text": "Chunk Count" },
  "xAxis": { "type": "category", "data": ["policy.md", "training-v1.docx"] },
  "yAxis": { "type": "value" },
  "series": [{ "type": "bar", "data": [5, 256] }]
}
\`\`\``;

const actionListMarkdown = `## 5. Actions
1. **Review large document chunking**  
   Check training-v1.docx and compare chunking parameters.

2. **Unify chunking rules**  
   Define recommended chunk ranges for each knowledge base.`;

const malformedListMarkdown = `## 5. 建议与后续行动
1.**评审大文档分块策略**  
   重点检查 中台操作培训-v1.docx 的原始大小，对比所用分块算法（如固定长度、语义分块等）的参数。

- **内容规模极端化**：“大数据培训”分块数是其他两个知识库的16倍以上。

2. **复查“HR系统知识库”小粒度文档**  

   对仅 2~3 块的文档进行内容抽查，确认是否因分块过大而丢失了关键段落的独立性。`;

const brokenEchartsMarkdown = `## 4.1 Chart

\`\`\`echarts
{
  "title": { "text": "Chunk Count" },
  "xAxis": {
    "type": "category",
    "data": ["policy.md", "rules.md", "training-v1.docx"]
  },
  "yAxis": { "type": "value" },
  "series": [
    {
      "name": "chunks",
      "type": "bar",
      "data": [
        { "value": 5, "itemStyle": { "color": "#5470c6" } },
        { "value": 2, "itemStyle": { "c6" } },
color": "#5470        { "value": 256, "itemStyle": { "color": "#ee6666" } }
      ]
    }
  ]
}
\`\`\``;

const chartThenStreamingTextA = `${reportMarkdown}

First paragraph after chart`;

const chartThenStreamingTextB = `${reportMarkdown}

First paragraph after chart with more streamed text`;

const fencedMarkdownReport = `\`\`\`markdown
# 系统链路核心瓶颈分析报告

## 1. 执行摘要
- **核心瓶颈节点**：retrieval-engine。
\`\`\``;

const spacedEmphasisMarkdown = `*报告生成时间：分析基于 14 条成功链路数据，建议在更大样本量下验证结论稳定性。 *`;

const tableAfterBoldLabelMarkdown = `### 3.1 数据查询
使用 SQL 对 \`t_rag_trace_node\` 表按 \`node_name\` 分组，保留 \`node_type\`，计算平均耗时 \`avg_duration_ms\` 和最大耗时 \`max_duration_ms\`。

**查询结果**：
| node_name               | node_type        | avg_duration_ms | max_duration_ms |
| ----------------------- | ---------------- | --------------- | --------------- |
| retrieval-engine        | RETRIEVE         | 91335.7500      | 420723          |
| intent-resolve          | INTENT           | 45937.0714      | 297022          |`;

const thematicBreakThenHeadingMarkdown = `### 4.3 会话时长特征
- 平均时长 \`6.5小时\` 显著偏高，这可能是以下原因导致：
  - **长连接/挂机**：用户在 \`2026-03\` 进行的 \`4\` 次会话可能为持续性测试会话，未及时关闭。
  - **“最后消息时间”滞后**：\`last_time\` 字段的更新逻辑可能存在延迟。

---

## 5. 图表可视化分析
由于系统仅包含 \`2026-03\` 一个维度的有效数据，无法绘制反映“变化趋势”的折线图或柱状图。`;

const stuckHeadingAndListMarkdown = `图1：全生命周期数据量极低，仅有4次记录
---
##6. 建议与后续行动
基于“零趋势、孤点数据”的现状，给出以下冷启动建议：
###6.1 数据完整性排查（最高优先级）
当前 \`5月、6月\` 无数据，与用户预期严重不符，建议立即排查：
-**埋点与逻辑检查**：确认 \`create_time\` 字段是否按预期写入，排查系统时间是否被人为回滚或修改。
- **数据清除策略**：核对是否存在自动化脚本误判，将 \`2026-05\` 的数据物理删除或标记为 \`deleted=1\`。`;

const server = await createServer({
  server: { middlewareMode: true },
  appType: 'custom',
  logLevel: 'silent',
});

try {
  const { MarkdownParser } = await server.ssrLoadModule('/src/views/Home/components/MarkdownParser.tsx');
  const html = renderToStaticMarkup(React.createElement(MarkdownParser, { content: reportMarkdown }));
  const actionListHtml = renderToStaticMarkup(React.createElement(MarkdownParser, { content: actionListMarkdown }));
  const malformedListHtml = renderToStaticMarkup(React.createElement(MarkdownParser, { content: malformedListMarkdown }));
  const brokenChartHtml = renderToStaticMarkup(React.createElement(MarkdownParser, { content: brokenEchartsMarkdown }));
  const chartThenTextAHtml = renderToStaticMarkup(React.createElement(MarkdownParser, { content: chartThenStreamingTextA }));
  const chartThenTextBHtml = renderToStaticMarkup(React.createElement(MarkdownParser, { content: chartThenStreamingTextB }));
  const fencedMarkdownReportHtml = renderToStaticMarkup(React.createElement(MarkdownParser, { content: fencedMarkdownReport }));
  const spacedEmphasisHtml = renderToStaticMarkup(React.createElement(MarkdownParser, { content: spacedEmphasisMarkdown }));
  const tableAfterBoldLabelHtml = renderToStaticMarkup(React.createElement(MarkdownParser, { content: tableAfterBoldLabelMarkdown }));
  const thematicBreakThenHeadingHtml = renderToStaticMarkup(React.createElement(MarkdownParser, { content: thematicBreakThenHeadingMarkdown }));
  const stuckHeadingAndListHtml = renderToStaticMarkup(React.createElement(MarkdownParser, { content: stuckHeadingAndListMarkdown }));

  assert.match(html, /<h1[^>]*>Knowledge Report<\/h1>/);
  assert.match(html, /<h2[^>]*>1\. Summary<\/h2>/);
  assert.match(html, /<h3[^>]*>3\.2 Result Set<\/h3>/);
  assert.match(html, /<table/);
  assert.match(html, /<th[^>]*>Knowledge Base<\/th>/);
  assert.match(html, /<td[^>]*>training-v1\.docx<\/td>/);
  assert.match(html, /data-testid="echarts-chart"/);
  assert.doesNotMatch(html, /<pre><div/);

  assert.match(actionListHtml, /<ol/);
  assert.match(actionListHtml, /<li[^>]*><strong>Review large document chunking<\/strong>/);
  assert.doesNotMatch(actionListHtml, /<li[^>]*>\s*<p/);

  assert.match(malformedListHtml, /<ol/);
  assert.match(malformedListHtml, /<li[^>]*><strong>评审大文档分块策略<\/strong>/);
  assert.match(malformedListHtml, /<ul/);
  assert.match(malformedListHtml, /<strong>内容规模极端化<\/strong>/);
  assert.doesNotMatch(malformedListHtml, /<del>/);

  assert.match(brokenChartHtml, /data-chart-option=/);
  assert.doesNotMatch(brokenChartHtml, /Chart render failed|Unexpected string/);

  const chartKeyA = chartThenTextAHtml.match(/data-chart-key="([^"]+)"/)?.[1];
  const chartKeyB = chartThenTextBHtml.match(/data-chart-key="([^"]+)"/)?.[1];
  assert.ok(chartKeyA);
  assert.equal(chartKeyA, chartKeyB);

  assert.match(fencedMarkdownReportHtml, /<h1[^>]*>系统链路核心瓶颈分析报告<\/h1>/);
  assert.match(fencedMarkdownReportHtml, /<h2[^>]*>1\. 执行摘要<\/h2>/);
  assert.doesNotMatch(fencedMarkdownReportHtml, /代码片段|MARKDOWN|<pre/);

  assert.match(spacedEmphasisHtml, /<em>报告生成时间：分析基于 14 条成功链路数据，建议在更大样本量下验证结论稳定性。<\/em>/);
  assert.match(tableAfterBoldLabelHtml, /<table/);
  assert.match(tableAfterBoldLabelHtml, /<th[^>]*>node_name<\/th>/);
  assert.match(tableAfterBoldLabelHtml, /<td[^>]*>retrieval-engine<\/td>/);
  assert.doesNotMatch(tableAfterBoldLabelHtml, /\| node_name/);

  assert.match(thematicBreakThenHeadingHtml, /<hr[^>]*>/);
  assert.match(thematicBreakThenHeadingHtml, /<h2[^>]*>5\. 图表可视化分析<\/h2>/);
  assert.match(thematicBreakThenHeadingHtml, /<ul class="[^"]*my-1\.5[^"]*">/);
  assert.match(thematicBreakThenHeadingHtml, /<ul class="[^"]*\[&amp;_ul\]:my-1[^"]*">/);
  assert.doesNotMatch(thematicBreakThenHeadingHtml, /<li class="[^"]*whitespace-pre-wrap[^"]*">/);
  assert.doesNotMatch(thematicBreakThenHeadingHtml, /##5\. 图表可视化分析/);

  assert.match(stuckHeadingAndListHtml, /<hr[^>]*>/);
  assert.match(stuckHeadingAndListHtml, /<h2[^>]*>6\. 建议与后续行动<\/h2>/);
  assert.match(stuckHeadingAndListHtml, /<h3[^>]*>6\.1 数据完整性排查（最高优先级）<\/h3>/);
  assert.match(stuckHeadingAndListHtml, /<ul/);
  assert.match(stuckHeadingAndListHtml, /<strong>埋点与逻辑检查<\/strong>/);
  assert.doesNotMatch(stuckHeadingAndListHtml, /###6\.1|-\*\*埋点/);
} finally {
  await server.close();
}
