import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const addDataPanelFile = path.resolve('src/views/DataCenter/components/AddDataPanel.tsx');
const dataCenterFile = path.resolve('src/views/DataCenter/index.tsx');

const addDataPanelSource = fs.readFileSync(addDataPanelFile, 'utf8');
const dataCenterSource = fs.readFileSync(dataCenterFile, 'utf8');

assert.match(
  addDataPanelSource,
  /postgresql:\s*5432/,
  'PostgreSQL 数据源应默认使用 postgresql 类型和 5432 端口'
);

assert.match(
  addDataPanelSource,
  /type:\s*'postgresql'[\s\S]*port:\s*getDefaultDatabasePortText\('postgresql'\)/,
  '切换到 PostgreSQL 数据源时应使用 PostgreSQL 默认端口'
);

assert.match(
  addDataPanelSource,
  /port:\s*Number\.parseInt\(dbForm\.port,\s*10\)\s*\|\|\s*getDefaultDatabasePort\(dbForm\.type\)/,
  '测试连接端口兜底应按数据库类型选择默认端口'
);

assert.match(
  addDataPanelSource,
  /dbForm:\s*\{[\s\S]*type:\s*dbForm\.type,[\s\S]*name:\s*dbForm\.name/,
  '确认导入时应把数据库类型透传给数据中心'
);

assert.match(
  addDataPanelSource,
  /description:\s*''/,
  '数据库表单应提供数据源描述字段的状态'
);

assert.match(
  addDataPanelSource,
  /description:\s*dbForm\.description\.trim\(\)/,
  '创建数据源请求应提交数据源描述'
);

assert.match(
  dataCenterSource,
  /dbForm\?:\s*\{[\s\S]*description\?:\s*string/,
  '数据中心确认参数应声明 dbForm.description'
);

console.log('DataCenter PostgreSQL datasource checks passed.');
