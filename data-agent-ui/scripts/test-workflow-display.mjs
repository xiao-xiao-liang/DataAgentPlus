import assert from 'node:assert/strict';
import { createServer } from 'vite';

const fencedPython = '```python\nimport sys\nprint("ok")\n```';
const pythonStdoutJson = '{"most_time_consuming_node":{"name":"RETRIEVE","avg_duration_ms":91335.75},"details":[]}';
const queryEnhanceJson = '{"canonical_query":"分析检索增强生成链路","expanded_queries":["查看RAG链路耗时"]}';
const planJson = JSON.stringify({
  thought_process: '用户需要分析 RAG 链路耗时，我会先查询再建模。',
  execution_plan: [
    {
      step: 1,
      tool_to_use: 'sql_generate',
      tool_parameters: { instruction: '查询各节点平均耗时并按耗时降序排列。' },
    },
    {
      step: 2,
      tool_to_use: 'python_generate',
      tool_parameters: { instruction: '读取 SQL 结果并找出耗时最长的节点。' },
    },
  ],
});
const mixedPythonStdoutText = `开始执行 Python 数据分析代码...
${pythonStdoutJson}
Python 代码数据处理执行成功！
$$$分析引擎标准输出:`;

const server = await createServer({
  server: { middlewareMode: true },
  appType: 'custom',
  logLevel: 'silent',
});

try {
  const {
    normalizeCodeForDisplay,
    isStructuredAnalysisOutput,
    formatStructuredAnalysisOutput,
    isPythonExecutionResidue,
    extractExecutionPlanView,
    getPythonExecutionOutputBlock,
  } = await server.ssrLoadModule('/src/views/Home/workflowDisplay.ts');

  assert.equal(normalizeCodeForDisplay(fencedPython, 'python'), 'import sys\nprint("ok")');
  assert.equal(isStructuredAnalysisOutput(pythonStdoutJson), true);
  assert.equal(isStructuredAnalysisOutput(queryEnhanceJson), false);
  assert.equal(isPythonExecutionResidue(mixedPythonStdoutText), true);
  assert.equal(formatStructuredAnalysisOutput(pythonStdoutJson), JSON.stringify(JSON.parse(pythonStdoutJson), null, 2));
  assert.equal(isStructuredAnalysisOutput('Python 代码数据处理执行成功！'), false);
  assert.equal(isPythonExecutionResidue('这是一段普通分析结论'), false);

  assert.deepEqual(extractExecutionPlanView(planJson), {
    thoughtProcess: '用户需要分析 RAG 链路耗时，我会先查询再建模。',
    steps: [
      { step: 1, tool: 'sql_generate', instruction: '查询各节点平均耗时并按耗时降序排列。' },
      { step: 2, tool: 'python_generate', instruction: '读取 SQL 结果并找出耗时最长的节点。' },
    ],
  });

  assert.deepEqual(getPythonExecutionOutputBlock([
    { type: 'json', content: queryEnhanceJson },
    { type: 'python', content: 'print("ok")' },
    { type: 'json', content: pythonStdoutJson },
  ]), { type: 'json', content: pythonStdoutJson });
} finally {
  await server.close();
}
