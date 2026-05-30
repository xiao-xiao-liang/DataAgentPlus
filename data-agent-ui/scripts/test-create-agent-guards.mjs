import fs from 'node:fs';
import path from 'node:path';

const file = path.resolve('src/views/CreateAgent/index.tsx');
const source = fs.readFileSync(file, 'utf8');

const expectations = [
  ['tracks whether datasource is bound before table configuration', 'isDatasourceBound'],
  ['tracks whether the selected datasource has pending bind changes', 'hasDatasourceSelectionChanged'],
  ['locks table selection until datasource is bound', 'canConfigureTables'],
  ['blocks debugging when datasource is not bound', 'showNotification(\'请先绑定数据源'],
  ['blocks publishing when datasource is not bound', '发布前请先绑定数据源'],
  ['explains locked table configuration', '请先绑定数据源，绑定成功后才能选择需要同步/向量化的数据表。'],
  ['uses management-oriented API key wording', 'API 调用凭证']
];

const missing = expectations.filter(([, needle]) => !source.includes(needle));

const syncHandlerIndex = source.indexOf('const handleSyncSchema = async () => {');
const bindEndpointIndex = syncHandlerIndex === -1
  ? -1
  : source.indexOf('/datasource/${selectedDsId}', syncHandlerIndex);
if (
  bindEndpointIndex === -1 ||
  source.indexOf('body: JSON.stringify(selectedTables)', bindEndpointIndex) === -1
) {
  missing.push([
    'persists selected table bindings before vectorization',
    'body: JSON.stringify(selectedTables) after /datasource/${selectedDsId}'
  ]);
}

if (source.includes('/schema/sync')) {
  missing.push([
    'does not bypass table binding persistence during vectorization',
    'remove direct /schema/sync call from CreateAgent'
  ]);
}

if (missing.length > 0) {
  console.error('CreateAgent guard checks failed:');
  for (const [label, needle] of missing) {
    console.error(`- ${label}: missing "${needle}"`);
  }
  process.exit(1);
}

console.log('CreateAgent guard checks passed.');
