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
} finally {
  await server.close();
}
