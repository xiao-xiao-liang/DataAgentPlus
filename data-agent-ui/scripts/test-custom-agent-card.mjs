import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const file = path.resolve('src/views/CustomAgent/index.tsx');
const source = fs.readFileSync(file, 'utf8');

assert.match(
  source,
  /onClick=\{\(\) => \{\s*navigate\(`\/agent\/create\?id=\$\{agent\.id\}`\);\s*\}\}/s,
  '点击智能体卡片应进入编辑页面'
);

assert.equal(
  source.includes('group-hover:underline'),
  false,
  '智能体卡片悬浮时标题不应出现下划线'
);

assert.match(
  source,
  /rounded-full[^"]*text-xs[^"]*font-bold/,
  '状态标签字号应比原 text-[10px] 大一号'
);

assert.match(
  source,
  /text-gray-400 flex items-center gap-2 text-sm border-t/,
  '卡片底部元信息字号应比原 text-xs 大一号'
);

console.log('CustomAgent card checks passed.');
