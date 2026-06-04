import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const addDataPanelFile = path.resolve('src/views/DataCenter/components/AddDataPanel.tsx');
const addDataPanelSource = fs.readFileSync(addDataPanelFile, 'utf8');

const handleTestConnectionMatch = addDataPanelSource.match(
  /const handleTestConnection = async \(\) => \{[\s\S]*?\n  \};/
);

assert.ok(handleTestConnectionMatch, '应存在测试连接处理函数');
assert.doesNotMatch(
  handleTestConnectionMatch[0],
  /alert\(/,
  '测试连接失败提示不应使用浏览器 alert'
);

assert.match(
  addDataPanelSource,
  /const \[testMessage,\s*setTestMessage\] = useState<string>\(''\)/,
  '测试连接反馈应有组件内消息状态'
);

assert.match(
  addDataPanelSource,
  /testStatus === 'failed' && testMessage/,
  '连接失败时应在连接反馈区域展示具体失败信息'
);

assert.doesNotMatch(
  addDataPanelSource,
  /连接测试失败，请检查配置/,
  '连接失败不应再使用按钮旁的内联红字，避免挤乱连接区域布局'
);

console.log('DataCenter test connection feedback checks passed.');
