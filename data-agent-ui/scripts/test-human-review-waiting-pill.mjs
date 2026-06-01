import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const projectRoot = process.cwd();
const homeViewPath = path.join(projectRoot, 'src/views/Home/index.tsx');
const homeViewSource = fs.readFileSync(homeViewPath, 'utf8');

assert.match(
  homeViewSource,
  /我将在你确认后继续/,
  '人工审核开启后应展示底部等待确认提示'
);

assert.match(
  homeViewSource,
  /isChatState && hasPendingHumanReviewNotice[\s\S]*rounded-\[24px\] border border-\[#D9B54A\] bg-white[\s\S]*className=\{clsx\([\s\S]*group\/composer/,
  '等待确认提示应位于输入框上方，并使用白底金边胶囊样式'
);

assert.match(
  homeViewSource,
  /inline-flex h-9 items-center justify-center gap-2 rounded-\[24px\] border border-\[#D9B54A\] bg-white px-6 text-\[14px\] leading-5 font-normal text-\[#3B3B3B\]/,
  '等待确认提示应使用官方尺寸与文字样式'
);

assert.match(
  homeViewSource,
  /<ListTodo className="size-4 shrink-0 text-\[#B88A00\]" strokeWidth=\{2\} \/>/,
  '等待确认提示应展示列表待办图标'
);

assert.match(
  homeViewSource,
  /text-\[#3B3B3B\][\s\S]*我将在你确认后继续/s,
  '等待确认提示应使用官方文字颜色'
);
