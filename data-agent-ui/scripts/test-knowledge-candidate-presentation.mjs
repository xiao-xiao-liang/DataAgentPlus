import assert from 'node:assert/strict';
import { createServer } from 'vite';

const candidate = {
  id: 1,
  agentId: 1,
  sourceQuestion: '帮我分析系统链路的核心瓶颈',
  normalizedContent: JSON.stringify({
    businessTerm: '系统链路核心瓶颈',
    description: '定位耗时最高的节点，并识别核心性能瓶颈。',
    calculationRule: '按各节点的 duration_ms 字段进行排序与聚合分析。',
    synonyms: ['链路瓶颈', '核心瓶颈'],
    isRecall: true,
    extraNote: '优先关注 retrieval-engine'
  }),
  candidateType: 'BUSINESS_KNOWLEDGE',
  title: '系统链路核心瓶颈业务口径',
  scope: 'AGENT',
  status: 'DRAFT',
  confidenceScore: 0.95,
};

const fallbackCandidate = {
  ...candidate,
  id: 2,
  title: '无法解析的候选知识',
  normalizedContent: '这是一段普通文本，不是 JSON',
};

const server = await createServer({
  server: { middlewareMode: true },
  appType: 'custom',
  logLevel: 'silent',
});

try {
  const mod = await server.ssrLoadModule('/src/views/KnowledgeCenter/knowledgeCandidatePresentation.ts');
  const { buildKnowledgeCandidateViewModel } = mod;

  const viewModel = buildKnowledgeCandidateViewModel(candidate);
  assert.equal(viewModel.businessTerm, '系统链路核心瓶颈');
  assert.equal(viewModel.description, '定位耗时最高的节点，并识别核心性能瓶颈。');
  assert.equal(viewModel.calculationRule, '按各节点的 duration_ms 字段进行排序与聚合分析。');
  assert.deepEqual(viewModel.synonyms, ['链路瓶颈', '核心瓶颈']);
  assert.equal(viewModel.badges.includes('可召回'), true);
  assert.equal(viewModel.extraEntries[0]?.label, '补充信息');
  assert.match(viewModel.extraEntries[0]?.value || '', /优先关注 retrieval-engine/);

  const fallbackViewModel = buildKnowledgeCandidateViewModel(fallbackCandidate);
  assert.equal(fallbackViewModel.businessTerm, '无法解析的候选知识');
  assert.equal(fallbackViewModel.description, '这是一段普通文本，不是 JSON');
  assert.equal(fallbackViewModel.synonyms.length, 0);
} finally {
  await server.close();
}
