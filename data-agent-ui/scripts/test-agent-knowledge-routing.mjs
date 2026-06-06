import assert from 'node:assert/strict';
import { createServer } from 'vite';

const server = await createServer({
  server: { middlewareMode: true },
  appType: 'custom',
  logLevel: 'silent',
});

try {
  const {
    getAgentIdFromKnowledgeBaseId,
    resolveKnowledgeAgentId,
  } = await server.ssrLoadModule('/src/views/KnowledgeCenter/agentKnowledgeRouting.ts');

  assert.equal(getAgentIdFromKnowledgeBaseId('kb-agent-4'), '4');
  assert.equal(getAgentIdFromKnowledgeBaseId('kb-agent-default'), 'default');
  assert.equal(getAgentIdFromKnowledgeBaseId('other-kb'), null);

  assert.equal(resolveKnowledgeAgentId('1', 'kb-agent-4'), '4');
  assert.equal(resolveKnowledgeAgentId('default', undefined), '1');
  assert.equal(resolveKnowledgeAgentId('7', undefined), '7');
} finally {
  await server.close();
}
