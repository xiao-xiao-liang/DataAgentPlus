import assert from 'node:assert/strict';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const projectRoot = process.cwd();
const routingModuleUrl = pathToFileURL(path.join(projectRoot, 'src/layout/agentRouting.ts')).href;
const currentAgentModuleUrl = pathToFileURL(path.join(projectRoot, 'src/stores/currentAgent.ts')).href;
const reportModuleUrl = pathToFileURL(path.join(projectRoot, 'src/views/Home/reportPanelState.ts')).href;
const reportLayoutModuleUrl = pathToFileURL(path.join(projectRoot, 'src/views/Home/reportLayoutState.ts')).href;

const { buildPathWithAgentId, resolveSessionAgentId } = await import(routingModuleUrl);
const { getCurrentAgentSnapshot, setCurrentAgentSnapshot } = await import(currentAgentModuleUrl);
const { getNextReportPanelState } = await import(reportModuleUrl);
const { REPORT_PANEL_DEFAULT_WIDTH, REPORT_PANEL_MAX_WIDTH, REPORT_PANEL_MIN_WIDTH, clampReportPanelWidth, getReportPanelWidthFromDrag } = await import(reportLayoutModuleUrl);

assert.equal(resolveSessionAgentId(9, '2'), '9');
assert.equal(resolveSessionAgentId(undefined, '2'), '2');
assert.equal(buildPathWithAgentId('/data', '7'), '/data');
assert.equal(buildPathWithAgentId('/agent', '7'), '/agent');
assert.equal(buildPathWithAgentId('/knowledge', '7'), '/knowledge');
assert.equal(buildPathWithAgentId('/knowledge/candidates', '7'), '/knowledge/candidates?agentId=7');
assert.equal(buildPathWithAgentId('/knowledge/candidates', 'default'), '/knowledge/candidates');

setCurrentAgentSnapshot({ agentId: '8', agentName: '测试智能体' });
assert.deepEqual(getCurrentAgentSnapshot(), { agentId: '8', agentName: '测试智能体' });

let state = getNextReportPanelState({
  hasReport: true,
  previousContent: '',
  nextContent: '# 报告',
  isManuallyCollapsed: false,
});
assert.equal(state.isOpen, true);
assert.equal(state.isManuallyCollapsed, false);

state = getNextReportPanelState({
  hasReport: true,
  previousContent: '# 报告',
  nextContent: '# 报告\n## 新增内容',
  isManuallyCollapsed: true,
});
assert.equal(state.isOpen, false);
assert.equal(state.isManuallyCollapsed, true);

state = getNextReportPanelState({
  hasReport: false,
  previousContent: '# 报告',
  nextContent: '',
  isManuallyCollapsed: true,
});
assert.equal(state.isOpen, false);
assert.equal(state.isManuallyCollapsed, false);

assert.equal(REPORT_PANEL_DEFAULT_WIDTH, 640);
assert.equal(clampReportPanelWidth(REPORT_PANEL_MIN_WIDTH - 80), REPORT_PANEL_MIN_WIDTH);
assert.equal(clampReportPanelWidth(REPORT_PANEL_MAX_WIDTH + 80), REPORT_PANEL_MAX_WIDTH);
assert.equal(clampReportPanelWidth(720), 720);
assert.equal(getReportPanelWidthFromDrag({ viewportWidth: 1600, pointerClientX: 900 }), 700);
assert.equal(getReportPanelWidthFromDrag({ viewportWidth: 1600, pointerClientX: 200 }), REPORT_PANEL_MAX_WIDTH);
assert.equal(getReportPanelWidthFromDrag({ viewportWidth: 1600, pointerClientX: 1300 }), REPORT_PANEL_MIN_WIDTH);
