import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { createServer } from 'vite';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

const projectRoot = process.cwd();
const homeViewSource = fs.readFileSync(path.join(projectRoot, 'src/views/Home/index.tsx'), 'utf8');

const server = await createServer({
  server: { middlewareMode: true },
  appType: 'custom',
  logLevel: 'silent',
});

try {
  const { CodeBlock } = await server.ssrLoadModule('/src/views/Home/components/CodeBlock.tsx');
  const html = renderToStaticMarkup(React.createElement(CodeBlock, {
    language: 'python',
    code: 'print("hello")',
  }));

  assert.match(html, /aria-expanded="false"/);
  assert.match(html, /代码块/);
  assert.match(html, /Python/);
  assert.match(html, /复制/);
  assert.doesNotMatch(html, /print/);

  const expandedHtml = renderToStaticMarkup(React.createElement(CodeBlock, {
    language: 'python',
    code: 'print("hello")',
    defaultOpen: true,
  }));

  assert.match(expandedHtml, /aria-expanded="true"/);
  assert.match(expandedHtml, /print/);

  const jsonHtml = renderToStaticMarkup(React.createElement(CodeBlock, {
    language: 'json',
    code: '{"ok": true}',
  }));

  assert.match(jsonHtml, /JSON/);
  assert.match(jsonHtml, /aria-expanded="false"/);
  assert.doesNotMatch(jsonHtml, /&quot;ok&quot;/);

  assert.match(homeViewSource, /const \[isOpen,\s*setIsOpen\] = useState\(false\)/);
  assert.match(homeViewSource, /<span className="text-\[13px\] font-medium">JSON<\/span>/);
  assert.match(homeViewSource, /<span>自动换行<\/span>/);
  assert.match(homeViewSource, /<span>\{copied \? '已复制' : '复制'\}<\/span>/);
} finally {
  await server.close();
}
