import assert from 'node:assert/strict';
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
  const { BackendErrorNotice } = await server.ssrLoadModule('/src/views/Home/components/BackendErrorNotice.tsx');

  const candidateHtml = renderToStaticMarkup(React.createElement(MemoryCandidateCard, {
    title: '候选业务知识',
    content: JSON.stringify({
      businessTerm: '活跃用户',
      description: '最近 7 天有登录行为的用户',
      calculationRule: 'COUNT(DISTINCT user_id)',
      synonyms: ['AU', '活跃人数'],
    }),
    onIgnore: () => {},
    onSave: () => {},
    onPublish: () => {},
  }));

  assert.match(candidateHtml, /候选业务知识/);
  assert.match(candidateHtml, /活跃用户/);
  assert.match(candidateHtml, /保存/);
  assert.match(candidateHtml, /发布/);
  assert.doesNotMatch(candidateHtml, /COUNT\(DISTINCT user_id\)/);
  assert.doesNotMatch(candidateHtml, /最近 7 天有登录行为的用户/);

  const errorHtml = renderToStaticMarkup(React.createElement(BackendErrorNotice, {
    content: '执行计划生成完成！\n计划校验失败: JsonMappingException: Unexpected end-of-input\nat com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:123)',
  }));

  assert.match(errorHtml, /后台工作流执行异常/);
  assert.match(errorHtml, /JsonMappingException/);
  assert.match(errorHtml, /复制/);
  assert.doesNotMatch(errorHtml, /ObjectMapper\.java:123/);
} finally {
  await server.close();
}
