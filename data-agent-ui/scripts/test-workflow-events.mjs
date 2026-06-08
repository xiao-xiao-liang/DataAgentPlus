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
    '@@DATA_AGENT_EVENT@@{"eventType":"node_started","payload":{"nodeName":"SqlExecuteNode","content":""}}@@END_DATA_AGENT_EVENT@@',
    '@@DATA_AGENT_EVENT@@{"eventType":"node_output","payload":{"nodeName":"SqlExecuteNode","content":"执行中"}}@@END_DATA_AGENT_EVENT@@',
    '@@DATA_AGENT_EVENT@@{"eventType":"node_completed","payload":{"nodeName":"SqlExecuteNode","content":""}}@@END_DATA_AGENT_EVENT@@',
    '@@DATA_AGENT_EVENT@@{"eventType":"waiting_user_input","payload":{"nodeName":"ClarificationAskNode","content":"等待澄清"}}@@END_DATA_AGENT_EVENT@@',
    '@@DATA_AGENT_EVENT@@{"eventType":"workflow_error","payload":{"nodeName":"","content":"boom"}}@@END_DATA_AGENT_EVENT@@',
    '@@DATA_AGENT_EVENT@@{"eventType":"workflow_done","payload":{"nodeName":"","content":""}}@@END_DATA_AGENT_EVENT@@',
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
    {
      eventType: 'node_started',
      payload: {
        nodeName: 'SqlExecuteNode',
        content: '',
      },
    },
    {
      eventType: 'node_output',
      payload: {
        nodeName: 'SqlExecuteNode',
        content: '执行中',
      },
    },
    {
      eventType: 'node_completed',
      payload: {
        nodeName: 'SqlExecuteNode',
        content: '',
      },
    },
    {
      eventType: 'waiting_user_input',
      payload: {
        nodeName: 'ClarificationAskNode',
        content: '等待澄清',
      },
    },
    {
      eventType: 'workflow_error',
      payload: {
        nodeName: '',
        content: 'boom',
      },
    },
    {
      eventType: 'workflow_done',
      payload: {
        nodeName: '',
        content: '',
      },
    },
  ]);
} finally {
  await server.close();
}
