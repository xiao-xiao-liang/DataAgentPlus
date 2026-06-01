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
  assert.match(html, /Python/);
  assert.match(html, /日志/);
  assert.doesNotMatch(html, /代码块/);
  assert.doesNotMatch(html, /自动换行/);
  assert.doesNotMatch(html, /复制/);
  assert.doesNotMatch(html, /print/);

  const expandedHtml = renderToStaticMarkup(React.createElement(CodeBlock, {
    language: 'python',
    code: 'print("hello")',
    defaultOpen: true,
  }));

  assert.match(expandedHtml, /aria-expanded="true"/);
  assert.match(expandedHtml, /自动换行/);
  assert.match(expandedHtml, /复制/);
  assert.match(expandedHtml, /print/);

  const jsonHtml = renderToStaticMarkup(React.createElement(CodeBlock, {
    language: 'json',
    code: '{"ok": true}',
  }));

  assert.match(jsonHtml, /JSON/);
  assert.match(jsonHtml, /日志/);
  assert.match(jsonHtml, /aria-expanded="false"/);
  assert.doesNotMatch(jsonHtml, /&quot;ok&quot;/);

  assert.match(homeViewSource, /const \[isOpen,\s*setIsOpen\] = useState\(false\)/);
  assert.match(homeViewSource, /<span className="truncate text-\[12px\] font-medium">\{languageLabel\}<\/span>/);
  assert.match(homeViewSource, />日志<\/span>/);
  assert.doesNotMatch(homeViewSource, />代码块<\/span>/);
} finally {
  await server.close();
}
