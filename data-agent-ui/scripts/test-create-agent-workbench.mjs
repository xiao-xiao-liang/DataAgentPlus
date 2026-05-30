import fs from 'node:fs';
import path from 'node:path';

const file = path.resolve('src/views/CreateAgent/index.tsx');
const source = fs.readFileSync(file, 'utf8');

const expectations = [
  ['defaults to the agent workbench overview', "const DEFAULT_AGENT_SECTION: AgentSection = 'overview'"],
  ['initializes active section from url', 'useState<AgentSection>(querySection)'],
  ['defines overview as a first-class section', "type AgentSection = 'overview' | 'info' | 'datasource' | 'apikey'"],
  ['renders the workbench overview title', 'Agent 能力工作台'],
  ['renders the capability assembly grid', '能力装配'],
  ['shows basic info capability card', '基础信息'],
  ['shows datasource capability card', '数据环境'],
  ['shows knowledge capability card for future expansion', '知识库'],
  ['shows API capability card', 'API 调用'],
  ['shows scheduled run capability card for future expansion', '周期运行'],
  ['shows publishing readiness card', '发布检查'],
  ['shows readiness checklist', '上线检查'],
  ['keeps future knowledge work disabled instead of routing to missing UI', '知识库绑定能力即将接入'],
  ['keeps future schedule work disabled instead of routing to missing UI', '周期运行能力即将接入'],
  ['uses overview navigation label', '配置总览'],
  ['uses Yiwen-style rounded rectangle card token', 'YIWEN_CARD_CLASS'],
  ['uses compact rounded rectangle radius', 'rounded-[14px]'],
  ['uses soft blue card border', 'border-[#dbe8f7]'],
  ['uses soft blue-tinted card shadow', 'shadow-[0_8px_20px_rgba(31,74,125,0.05)]'],
  ['uses compact capability card height', 'h-[104px]'],
  ['uses three-column capability grid', 'grid grid-cols-3 gap-3'],
  ['prevents overview inner scrollbars', 'overflow-hidden bg-[#fbfdff] p-4'],
  ['uses pale blue icon tile', 'bg-[#eef5ff]'],
  ['reads active section from url tab parameter', 'getAgentSectionFromSearch'],
  ['writes active section to url tab parameter', "params.set('tab', section)"],
  ['makes capability card itself clickable', 'onClick={card.onClick}'],
  ['uses shared refined style on detail panels', 'DETAIL_PANEL_CLASS'],
  ['adds API call examples', '调用示例'],
  ['shows curl API example tab', "label: 'curl'"],
  ['shows JavaScript API example tab', "label: 'JavaScript'"],
  ['shows Python API example tab', "label: 'Python'"],
  ['uses X-API-Key header in examples', 'X-API-Key'],
  ['copies the active API example', 'handleCopyExample'],
  ['shows API key status control', 'API Key 状态'],
  ['supports deleting API key from the page', 'handleDeleteKey'],
  ['supports enabling and disabling API key from the page', 'handleToggleApiKeyEnabled'],
  ['hides global footer actions on datasource and api tabs', "activeTab === 'overview' || activeTab === 'info'"]
];

const forbidden = [
  ['does not keep a separate capability action button', '{card.action}']
];

const missing = expectations.filter(([, needle]) => !source.includes(needle));
const presentForbidden = forbidden.filter(([, needle]) => source.includes(needle));

if (missing.length > 0 || presentForbidden.length > 0) {
  console.error('CreateAgent workbench checks failed:');
  for (const [label, needle] of missing) {
    console.error(`- ${label}: missing "${needle}"`);
  }
  for (const [label, needle] of presentForbidden) {
    console.error(`- ${label}: found forbidden "${needle}"`);
  }
  process.exit(1);
}

console.log('CreateAgent workbench checks passed.');
