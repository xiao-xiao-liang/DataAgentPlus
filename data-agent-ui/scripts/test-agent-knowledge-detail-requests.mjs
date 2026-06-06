import assert from 'node:assert/strict';
import fs from 'node:fs';

const source = fs.readFileSync('src/views/KnowledgeCenter/components/KnowledgeDetail.tsx', 'utf8');

assert.match(
  source,
  /fetch\(`\/api\/v1\/agent-knowledge\/\$\{selectedFile\.backendId\}\/chunks\?agentId=\$\{agentId\}`\)/,
  '加载知识分块时必须携带 agentId 查询参数',
);

assert.match(
  source,
  /fetch\(`\/api\/v1\/agent-knowledge\/\$\{targetFile\.backendId\}\?agentId=\$\{agentId\}`,\s*\{\s*method:\s*'DELETE'\s*\}\)/s,
  '删除知识文档时必须携带 agentId 查询参数',
);
