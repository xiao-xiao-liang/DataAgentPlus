import assert from 'node:assert/strict';
import fs from 'node:fs';

const source = fs.readFileSync('src/views/KnowledgeCenter/index.tsx', 'utf8');

assert.match(
  source,
  /className="[^"]*pointer-events-none[^"]*absolute left-0 top-0 z-20 flex h-\[3\.75rem\] w-12/,
  '知识中心左上角浮层不能拦截详情页和分块页的返回按钮点击',
);

assert.match(
  source,
  /className="[^"]*pointer-events-auto[^"]*mx-3 hidden size-7/,
  '浮层内按钮需要保留独立接收点击事件的能力',
);
