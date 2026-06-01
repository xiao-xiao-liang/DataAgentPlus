import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const projectRoot = process.cwd();
const homeViewPath = path.join(projectRoot, 'src/views/Home/index.tsx');
const homeViewSource = fs.readFileSync(homeViewPath, 'utf8');

assert.match(
  homeViewSource,
  /activeHumanFeedbackPlanPreview/,
  '点击修改后应在输入框顶部展示执行计划引用'
);

assert.match(
  homeViewSource,
  /CornerDownRight/,
  '执行计划引用块应使用官方的折返箭头图标'
);

assert.match(
  homeViewSource,
  /handleHumanFeedback\(false, text\.trim\(\)\)/,
  '修改意见应通过底部输入框提交为人工反馈'
);

assert.match(
  homeViewSource,
  /approved \? \[\{[\s\S]*role: 'user' as const[\s\S]*content: '开始任务'/,
  '人工审核点击确认后应在前端即时展示“开始任务”用户消息'
);

assert.match(
  homeViewSource,
  /humanFeedbackPlanPreview\?: string/,
  '人工反馈修改消息应携带执行计划引用'
);

assert.match(
  homeViewSource,
  /humanFeedbackPlanPreview: activeHumanFeedbackPlanPreview/,
  '发送修改意见后应把执行计划引用写入用户消息'
);

assert.match(
  homeViewSource,
  /if \(msg\.humanFeedbackPlanPreview\)[\s\S]*grid-cols-\[minmax\(72px,1fr\)_auto\][\s\S]*col-start-2[\s\S]*max-w-\[80%\][\s\S]*justify-self-end[\s\S]*CornerDownRight[\s\S]*line-clamp-3[\s\S]*msg\.humanFeedbackPlanPreview[\s\S]*row-start-2[\s\S]*max-w-\[80%\][\s\S]*justify-self-end/,
  '带引用的用户消息应复刻官网 grid 布局'
);

assert.match(
  homeViewSource,
  /if \(msg\.humanFeedbackPlanPreview\)[\s\S]*return \([\s\S]*grid-cols-\[minmax\(72px,1fr\)_auto\][\s\S]*className="group flex w-full flex-col items-end/,
  '普通用户消息应保留原来的 flex 气泡布局'
);

assert.match(
  homeViewSource,
  /approveDisabled=\{feedbackSubmitting \|\| Boolean\(activeHumanFeedbackPlanPreview\)\}/,
  '进入修改态后确认按钮应置灰且不可点击'
);

assert.match(
  homeViewSource,
  /my-3 w-full max-w-\[640px\] select-none[\s\S]*rounded-\[10px\] border border-gray-200 bg-white[\s\S]*min-h-\[45px\][\s\S]*text-\[14px\] leading-7 text-\[#0A0A0B\]/,
  '人工审核确认条应使用官方 640px、45px、10px 圆角与主文字样式'
);

assert.match(
  homeViewSource,
  /handleCancelHumanFeedbackEdit[\s\S]*setActiveHumanFeedbackPlanPreview\(''\)[\s\S]*setInputValue\(''\)/,
  '点击引用块 X 后应清空修改态和输入框内容'
);

assert.doesNotMatch(
  homeViewSource,
  /提交修改意见/,
  '人工反馈修改不应再在上方确认卡片内展开提交按钮'
);
