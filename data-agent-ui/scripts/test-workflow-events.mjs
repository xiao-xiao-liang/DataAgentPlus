import assert from 'node:assert/strict';
import { createServer } from 'vite';

const server = await createServer({
  server: { middlewareMode: true },
  appType: 'custom',
  logLevel: 'silent',
});

try {
  const {
    splitWorkflowEvents,
  } = await server.ssrLoadModule('/src/views/Home/workflowEvents.ts');

  const raw = [
    '@@DATA_AGENT_EVENT@@{"eventType":"clarification_request","payload":{"question":"请补充统计口径"}}@@END_DATA_AGENT_EVENT@@',
    '请补充统计口径',
  ].join('\n');

  const parsed = splitWorkflowEvents(raw);

  assert.equal(parsed.visibleContent.trim(), '请补充统计口径');
  assert.deepEqual(parsed.events, [
    {
      eventType: 'clarification_request',
      payload: {
        question: '请补充统计口径',
      },
    },
  ]);
} finally {
  await server.close();
}
