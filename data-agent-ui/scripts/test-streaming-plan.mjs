import assert from 'node:assert/strict';
import { createServer } from 'vite';

const partialPlan = `$$$json{
  "thought_process": "User asks for chunk counts. I will query documents",
  "execution_plan": [
    {
      "step": 1,
      "tool_to_use": "sql_generate",
      "tool_parameters": {
        "instruction": "Count chunks by document and knowledge base"
      }
    },
    {
      "step": 2,
      "tool_to_use": "report_generator",
      "tool_parameters": {
        "summary_and_recommendations": "Summarize the largest`;

const server = await createServer({
  server: { middlewareMode: true },
  appType: 'custom',
  logLevel: 'silent',
});

try {
  const { parseStreamingPlan } = await server.ssrLoadModule('/src/views/Home/streamingPlan.ts');
  const parsed = parseStreamingPlan(partialPlan);

  assert.ok(parsed);
  assert.equal(parsed.thought_process, 'User asks for chunk counts. I will query documents');
  assert.equal(parsed.execution_plan.length, 2);
  assert.equal(parsed.execution_plan[0].tool_to_use, 'sql_generate');
  assert.equal(parsed.execution_plan[0].tool_parameters.instruction, 'Count chunks by document and knowledge base');
  assert.equal(parsed.execution_plan[1].tool_to_use, 'report_generator');
  assert.equal(parsed.execution_plan[1].tool_parameters.summary_and_recommendations, 'Summarize the largest');
} finally {
  await server.close();
}
