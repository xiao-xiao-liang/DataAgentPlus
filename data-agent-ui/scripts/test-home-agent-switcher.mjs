import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const file = path.resolve('src/views/Home/index.tsx');
const source = fs.readFileSync(file, 'utf8');

assert.match(
  source,
  /type ChatAgentOption = \{[\s\S]*description: string;[\s\S]*\}/,
  'Chat Agent 下拉列表项应包含名称和描述'
);

assert.match(
  source,
  /fetch\('\/api\/agent\/list'\)[\s\S]*setAgentOptions/,
  'Chat Agent 下拉应从后端加载 Agent 列表'
);

assert.equal(
  source.includes('placeholder="搜索..."'),
  true,
  'Chat Agent 下拉应包含搜索输入框'
);

assert.match(
  source,
  /DropdownMenu\.Content[\s\S]*w-\[400px\][\s\S]*rounded-lg[\s\S]*shadow-\[0_4px_6px_-1px_rgba\(0,0,0,0\.10\),0_2px_4px_-2px_rgba\(0,0,0,0\.10\)\]/,
  'Chat Agent 下拉弹层应为 400px 宽、8px 圆角和轻阴影'
);

assert.match(
  source,
  /className="h-full min-w-0 flex-1 border-0 bg-transparent p-0 text-\[14px\] font-normal leading-\[21px\]/,
  'Chat Agent 搜索输入应使用 14px 字号和 21px 行高'
);

assert.match(
  source,
  /'flex h-\[50px\] cursor-pointer items-center justify-between rounded-md px-2 outline-none transition-colors'/,
  'Chat Agent 列表项高度应保持紧凑'
);

assert.equal(
  source.includes('text-[18px]'),
  false,
  'Chat Agent 下拉不应使用过大的 18px 字号'
);

assert.match(
  source,
  /setCurrentAgent\(\{ agentId: agent\.id, agentName: agent\.name \}\);[\s\S]*navigate\('\/chat'\);/,
  '选择 Agent 后应更新当前 Agent 并回到 Chat'
);

assert.equal(
  source.includes("navigate('/agent/create')"),
  true,
  'Chat Agent 下拉应提供创建自定义 Agent 入口'
);

console.log('Home Agent switcher checks passed.');
