import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createServer } from 'vite';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

const server = await createServer({
  server: { middlewareMode: true },
  appType: 'custom',
  logLevel: 'silent',
});

try {
  const { MemoryCandidateCard } = await server.ssrLoadModule('/src/views/Home/components/MemoryCandidateCard.tsx');
  const html = renderToStaticMarkup(React.createElement(MemoryCandidateCard, {
    title: '候选业务知识',
    content: JSON.stringify({
      businessTerm: '活跃用户',
      description: '最近 7 天有登录行为的用户',
      calculationRule: 'COUNT(DISTINCT user_id)',
      synonyms: ['AU', '活跃人数'],
      isRecall: true,
    }),
    onIgnore: () => {},
    onSave: () => {},
    onPublish: () => {},
  }));

  assert.match(html, /候选业务知识/);
  assert.match(html, /活跃用户/);
  assert.match(html, /保存为候选知识/);
  assert.match(html, /直接发布/);
  assert.doesNotMatch(html, /最近 7 天有登录行为的用户/);
  assert.doesNotMatch(html, /COUNT\(DISTINCT user_id\)/);
  assert.doesNotMatch(html, /businessTerm/);
  assert.doesNotMatch(html, /calculationRule/);
  assert.doesNotMatch(html, /\{&quot;/);

  const disabledButtons = html.match(/<button(?=[^>]*\sdisabled(?:=""|\s|>))[^>]*>/g) || [];
  assert.equal(disabledButtons.length, 0);

  const homeSource = fs.readFileSync('src/views/Home/index.tsx', 'utf8');
  const cardUsage = homeSource.match(/<MemoryCandidateCard[\s\S]*?\/>/)?.[0] || '';
  assert.match(cardUsage, /actionStatus=\{candidateActionStatus\}/);
  assert.doesNotMatch(cardUsage, /disabled=\{feedbackSubmitting\}/);
} finally {
  await server.close();
}
