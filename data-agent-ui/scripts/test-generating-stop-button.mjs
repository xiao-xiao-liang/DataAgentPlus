import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const projectRoot = process.cwd();
const homeViewPath = path.join(projectRoot, 'src/views/Home/index.tsx');
const homeViewSource = fs.readFileSync(homeViewPath, 'utf8');

assert.match(
  homeViewSource,
  /const \[isGenerating,\s*setIsGenerating\] = useState\(false\)/,
  '流式输出期间应有独立的生成中状态'
);

assert.match(
  homeViewSource,
  /AbortController/,
  '流式请求应通过 AbortController 支持前端终止'
);

assert.match(
  homeViewSource,
  /const handleStopGenerating = \(\) => \{/,
  '输入框按钮应绑定停止生成处理函数'
);

assert.match(
  homeViewSource,
  /isGenerating \? \([\s\S]*<Square className="w-3 h-3 fill-current stroke-\[2\.5\]" \/>[\s\S]*\) : \([\s\S]*<ArrowUp className="w-4 h-4 stroke-\[2\.5\]" \/>/,
  '生成中按钮应从发送箭头切换为停止方块图标'
);

assert.match(
  homeViewSource,
  /signal: abortController\.signal/,
  '所有流式 fetch 应传入当前 AbortController 的 signal'
);
