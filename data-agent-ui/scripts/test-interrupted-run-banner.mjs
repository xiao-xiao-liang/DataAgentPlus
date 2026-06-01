import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const projectRoot = process.cwd();
const homeViewPath = path.join(projectRoot, 'src/views/Home/index.tsx');
const homeViewSource = fs.readFileSync(homeViewPath, 'utf8');
const resumeBannerIndex = homeViewSource.indexOf('上次会话异常中断，是否继续分析');

assert.notEqual(resumeBannerIndex, -1, '应存在恢复提示文案');

const resumeBannerSource = homeViewSource.slice(
  Math.max(0, resumeBannerIndex - 320),
  resumeBannerIndex + 960
);

assert.match(
  homeViewSource,
  /isChatState && interruptedRun\?\.resumable[\s\S]*max-w-\[800px\][\s\S]*className=\{clsx\([\s\S]*group\/composer/,
  '恢复提示应放在输入框上方，并与 800px 卡片区域左右对齐'
);

assert.match(
  homeViewSource,
  /interruptedRun\?\.resumable[\s\S]*rounded-\[10px\] border border-gray-200 bg-white/,
  '恢复提示应使用 10px 圆角的白底细边框确认条样式'
);

assert.match(
  resumeBannerSource,
  /上次会话异常中断，是否继续分析/,
  '恢复提示应保留继续分析的主文案'
);

assert.match(
  resumeBannerSource,
  /text-\[14px\] leading-7 text-\[#0A0A0B\]/,
  '恢复提示的文案应使用 14px、28px 行高和主文字色'
);

assert.match(
  resumeBannerSource,
  /rounded-\[10px\] border border-\[#151517\] bg-\[#151517\] px-3 py-1 text-\[14px\] leading-5 font-medium text-\[#FAFAFA\][\s\S]*继续分析/,
  '恢复提示的按钮应使用深色小按钮样式'
);
