import React, { useState, useEffect, useRef, useMemo } from 'react';
import { useLocation, useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { Settings2, ArrowUp, RefreshCcw, X, Plus, BookOpen, Atom, ChevronDown, Sheet, Maximize2, Upload, Plug, ChevronRight, Check, Sparkles, LineChart, Network, Clock, Code2, Database, FileText, Search, GitBranch, CircleHelp, Compass, Menu, ChevronsLeft, ChevronsRight, ListTodo, CornerDownRight, Square, Copy, WrapText } from 'lucide-react';
import clsx from 'clsx';
import * as HoverCard from '@radix-ui/react-hover-card';
import * as Tooltip from '@radix-ui/react-tooltip';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import * as Dialog from '@radix-ui/react-dialog';
import { MOCK_PREVIEW_DATA, INITIAL_FILES } from '../DataCenter/mockData';
import type { LayoutOutletContext } from '../../layout/GlobalLayout';
import { useCurrentAgentStore } from '../../stores/currentAgent';
import { InteractiveReport } from './components/InteractiveReport';
import { ClarificationCard } from './components/ClarificationCard';
import { MemoryCandidateCard } from './components/MemoryCandidateCard';
import { parseStreamingPlan } from './streamingPlan';
import { getNextReportPanelState } from './reportPanelState';
import { REPORT_PANEL_DEFAULT_WIDTH, clampReportPanelWidth } from './reportLayoutState';
import {
  formatStructuredAnalysisOutput,
  extractExecutionPlanView,
  getPythonExecutionOutputBlock,
  isPythonExecutionResidue,
  isStructuredAnalysisOutput,
  normalizeCodeForDisplay,
  tokenizeCodeForDisplay,
  type CodeLanguage,
  type CodeToken,
} from './workflowDisplay';
import { splitWorkflowEvents, type WorkflowEvent } from './workflowEvents';

// 消息 Block 数据结构
interface MessageBlock {
  type: 'text' | 'json' | 'python' | 'sql' | 'markdown-report' | 'result_set';
  content: string;
}

interface Message {
  role: 'user' | 'assistant';
  content?: string;
  humanFeedbackPlanPreview?: string;
  type?: 'text' | 'data';
  data?: any;
  blocks?: MessageBlock[];
  isComplete?: boolean;
  workflowEvents?: WorkflowEvent[];
}

type ChatMode = 'nl2sqlOnly' | 'humanReview' | null;

interface ClarificationRequestPayload {
  question: string;
  reason?: string;
  missingTerm?: string;
}

interface ClarificationConfirmationPayload {
  confirmationText: string;
}

interface MemoryCandidatePayload {
  candidateId?: number | string;
  title: string;
  normalizedContent: string;
  confidenceScore?: number;
}

interface WorkflowRunState {
  status?: string;
  resumable?: boolean;
  interruptReason?: string;
}

type MemoryCandidateActionStatus = 'idle' | 'pending' | 'submitted' | 'published' | 'ignored' | 'error';

type ChatAgentOption = {
  id: string;
  name: string;
  description: string;
};

const DEFAULT_CHAT_AGENT_OPTION: ChatAgentOption = {
  id: '1',
  name: 'Data Agent',
  description: '系统内置Data Agent',
};

const getPreviewData = (fileName: string) => {
  if (fileName.includes('餐厅')) return MOCK_PREVIEW_DATA.restaurant;
  if (fileName.includes('游戏')) return MOCK_PREVIEW_DATA.game;
  if (fileName.includes('信用卡')) return MOCK_PREVIEW_DATA.credit;
  return MOCK_PREVIEW_DATA.default;
};

// ================= 子组件：TaskToolCard (深度复刻 DataAgent 折叠卡片) =================
interface TaskToolCardProps {
  title: string;
  summary?: string;
  defaultOpen?: boolean;
  children: React.ReactNode;
}

const TaskToolCard: React.FC<TaskToolCardProps> = ({ 
  title, 
  summary, 
  defaultOpen = true, 
  children 
}) => {
  const [isOpen, setIsOpen] = useState(defaultOpen);

  return (
    <div className="my-2 w-full max-w-[640px] opacity-100 transition-opacity duration-300">
      <div className="group rounded-lg border border-gray-200 bg-white border-b-0 shadow-xs">
        {/* 头部按钮 */}
        <button 
          onClick={() => setIsOpen(!isOpen)}
          className="inline-flex items-center gap-2 whitespace-nowrap text-sm focus-visible:outline-none h-auto w-full flex-wrap justify-start rounded-lg bg-white px-4 font-normal hover:bg-gray-50/70 py-3 border-none outline-none cursor-pointer"
          type="button"
        >
          <ChevronDown className={clsx("mr-1 !size-4 min-w-4 text-gray-400 transition-all duration-200", isOpen ? "rotate-180" : "rotate-0")} />
          <span className="font-bold text-gray-800 text-[13px]">{title}</span>
          
          {/* 折叠时右侧的可选信息，或在不展开时显示的摘要 */}
          {!isOpen && summary && (
            <div className="text-gray-400 ml-7 w-full overflow-hidden text-ellipsis whitespace-nowrap text-left text-[11px] font-medium mt-1">
              {summary}
            </div>
          )}
        </button>

        {/* 折叠内容区域 */}
        {isOpen && (
          <div className="overflow-hidden rounded-lg animate-in fade-in slide-in-from-top-1 duration-200 border-t border-gray-100">
            <div className="overflow-hidden text-gray-600 relative rounded-lg border-b border-solid border-gray-200 bg-[#FAFAFC] p-5 text-sm">
              <div className="h-full w-full max-h-64 overflow-y-auto no-scrollbar select-text leading-relaxed">
                {children}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

// ================= 子组件：数据理解富文本 (DataUnderstanding) =================
const DataUnderstanding: React.FC = () => {
  return (
    <div className="text-[12.5px] text-gray-700 space-y-2 leading-relaxed">
      <p className="my-1 text-sm font-semibold text-gray-800">数据理解完成：</p>
      <ul className="ml-5 list-outside list-disc space-y-2 text-sm leading-[1.625rem]">
        <li className="py-0.5 text-sm leading-[1.625rem]">
          <p className="my-1 text-sm leading-6 first:mt-0 last:mb-0"><strong>数据内容</strong>：数据集记录了全球多个游戏的销售数据及评分信息，涵盖游戏名称、平台、发行年份、类型、发行商、各地区销量、全球总销量、媒体评分、用户评分及相关开发与分级信息。</p>
          <ul className="ml-5 list-outside list-disc text-sm leading-[1.625rem]">
            <li className="py-0.5 text-sm leading-[1.625rem]">涵盖游戏基本信息（如名称、平台、类型、发行年份等）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">各地区销量（北美、欧洲、日本、其他地区）及全球销量</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">媒体评分（Critic Score）与评分数量（Critic Count）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">用户评分（User Score）与评分数量（User Count）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">开发商（Developer）与游戏分级（Rating）</li>
          </ul>
        </li>
        <li className="py-0.5 text-sm leading-[1.625rem]">
          <p className="my-1 text-sm leading-6 first:mt-0 last:mb-0"><strong>数据粒度</strong>：一行 =「一款游戏 × 一个平台」</p>
          <ul className="ml-5 list-outside list-disc text-sm leading-[1.625rem]">
            <li className="py-0.5 text-sm leading-[1.625rem]">同一游戏可能因发布在不同平台而出现多次（如《Wii Sports》在 Wii 平台）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">销量数据为该游戏在该平台上的销售情况</li>
          </ul>
        </li>
        <li className="py-0.5 text-sm leading-[1.625rem]">
          <p className="my-1 text-sm leading-6 first:mt-0 last:mb-0"><strong>数据规模</strong>：共 16719 行，16 列，覆盖 11563 种游戏名称，31 种平台，13 种游戏类型，582 家发行商。</p>
        </li>
        <li className="py-0.5 text-sm leading-[1.625rem]">
          <p className="my-1 text-sm leading-6 first:mt-0 last:mb-0"><strong>关键特点</strong>：</p>
          <ul className="ml-5 list-outside list-disc text-sm leading-[1.625rem]">
            <li className="py-0.5 text-sm leading-[1.625rem]">游戏销量单位为百万份（Millions of units sold）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">时间跨度从 1980 年代至 2020 年代初（Year_of_Release）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">销量分布高度集中于头部游戏（如《Wii Sports》销量达 82.53 百万）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">评分数据（Critic Score 和 User Score）存在大量缺失值</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">用户评分（User_Score）为字符串类型，部分为数值（如 "8"），部分为 "tbd"</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">Rating 列使用 ESRB 分级标准（如 E、T、M 等），但缺失值较多</li>
          </ul>
        </li>
        <li className="py-0.5 text-sm leading-[1.625rem]">
          <p className="my-1 text-sm leading-6 first:mt-0 last:mb-0"><strong>数据质量问题</strong>：</p>
          <ul className="ml-5 list-outside list-disc text-sm leading-[1.625rem]">
            <li className="py-0.5 text-sm leading-[1.625rem]">多列存在缺失值，尤其是 Critic_Score、Critic_Count、User_Score、User_Count、Developer、Rating 等列</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">Year_of_Release 存在 NaN 值（发布年份缺失）</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">User_Score 为 object 类型，包含字符串 "tbd"，需转换为数值或处理</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">Genre 列存在 2 个缺失值</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">Developer 列缺失值高达 6623 条，占总行数近 40%</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">Rating 列缺失值达 6769 条，需注意在分析分级相关问题时的样本偏差</li>
          </ul>
        </li>
        <li className="py-0.5 text-sm leading-[1.625rem]">
          <p className="my-1 text-sm leading-6 first:mt-0 last:mb-0"><strong>重要的数据处理方式</strong></p>
          <ul className="ml-5 list-outside list-disc text-sm leading-[1.625rem]">
            <li className="py-0.5 text-sm leading-[1.625rem]">在分析评分相关指标前，需处理 User_Score 中的 "tbd" 字符串，建议替换为 NaN 并转为数值类型</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">对于缺失值较多的列（如 Developer、Rating），在分析时应明确指出样本范围</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">销量数据（NA_Sales 等）为 float64 类型，可直接用于数值 analysis</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">若需对游戏进行唯一标识，应结合 Name + Platform 或添加唯一索引</li>
            <li className="py-0.5 text-sm leading-[1.625rem]">在进行时间序列分析时，需剔除 Year_of_Release 为 NaN 的记录</li>
          </ul>
        </li>
      </ul>
    </div>
  );
};

// ================= 子组件：分析彻底结束后的耗时时间轴进度图 (AnalysisTimeLine) =================
const AnalysisTimeLine: React.FC<{ query: string; blocks: MessageBlock[] }> = ({ query, blocks }) => {
  const isGame = query.includes('游戏') || query.includes('营销') || query.includes('销量') || query.includes('Strategy') || query.includes('SLG');
  
  // 尝试从 blocks 中寻找计划 JSON
  const jsonBlock = blocks?.find(b => b.type === 'json' && b.content.includes('execution_plan'));
  let planObj: any = null;
  if (jsonBlock) {
    try {
      const clean = jsonBlock.content.replace('$$$json', '').trim();
      planObj = JSON.parse(clean);
    } catch (e) {}
  }

  let nodes: { label: string; duration: string; icon: React.ReactNode }[] = [];

  if (planObj && Array.isArray(planObj.execution_plan) && planObj.execution_plan.length > 0) {
    const steps = planObj.execution_plan;
    nodes = steps.map((step: any, idx: number) => {
      let icon = <Clock className="size-3.5 text-gray-500" />;
      if (step.tool_to_use === 'sql_generate') {
        icon = <LineChart className="size-3.5 text-gray-500" />;
      } else if (step.tool_to_use === 'python_generate' || step.tool_to_use === 'python_analyze') {
        icon = <Network className="size-3.5 text-gray-500" />;
      } else if (step.tool_to_use === 'report_generator') {
        icon = <Code2 className="size-3.5 text-gray-500" />;
      }

      // 获取友好展示名字
      let label = '';
      if (isGame) {
        if (step.tool_to_use === 'sql_generate') label = '策略类游戏的全球销量表现与区域市场偏好分析';
        else if (step.tool_to_use === 'python_generate') label = '策略类游戏的评分-销量关系建模与口碑价值量化';
        else if (step.tool_to_use === 'report_generator') label = '报告生成';
      }
      
      if (!label) {
        label = step.tool_parameters?.instruction || step.tool_parameters?.summary_and_recommendations || `${step.tool_to_use.toUpperCase()}_NODE`;
      }

      // 动态时长分配，模拟人类质感
      let duration = '45s';
      if (idx === steps.length - 1) {
        duration = isGame ? '2m2s' : '1m10s';
      } else {
        const randSec = 15 + ((idx * 17) % 30);
        duration = `${randSec}s`;
      }

      return {
        label,
        duration,
        icon
      };
    });
  } else {
    // 兜底逻辑
    if (isGame) {
      nodes = [
      { label: '策略类游戏的全球销量表现与区域市场偏好分析', duration: '3m35s', icon: <LineChart className="size-3.5 text-gray-500" /> },
      { label: '策略类游戏的评分-销量关系建模与口碑价值量化', duration: '3m29s', icon: <Network className="size-3.5 text-gray-500" /> },
      { label: '策略类游戏的成功要素拆解与发行商能力映射', duration: '1m49s', icon: <Clock className="size-3.5 text-gray-500" /> },
      { label: '策略类游戏平台生态与营销渠道适配性推断', duration: '1m34s', icon: <Sheet className="size-3.5 text-gray-500" /> },
      { label: '报告生成', duration: '2m2s', icon: <Code2 className="size-3.5 text-gray-500" /> },
      ];
    } else {
      if (blocks?.some(block => block.type === 'sql' || block.type === 'result_set')) {
        nodes.push({ label: '执行数据查询并返回结果集', duration: '45s', icon: <LineChart className="size-3.5 text-gray-500" /> });
      }
      if (blocks?.some(block => block.type === 'python')) {
        nodes.push({ label: '运行分析代码并整理输出', duration: '1m20s', icon: <Network className="size-3.5 text-gray-500" /> });
      }
      if (blocks?.some(block => block.type === 'markdown-report')) {
        nodes.push({ label: '生成最终分析报告', duration: '1m10s', icon: <Code2 className="size-3.5 text-gray-500" /> });
      }
      if (nodes.length === 0) {
        nodes = [
          { label: '整理任务执行结果', duration: '45s', icon: <Clock className="size-3.5 text-gray-500" /> },
          { label: '生成最终回复', duration: '1m10s', icon: <Code2 className="size-3.5 text-gray-500" /> },
        ];
      }
    }
  }

  return (
    <div className="w-full max-w-[640px] bg-white border border-gray-200 rounded-xl p-5 shadow-xs my-3 animate-in fade-in duration-300 select-none">
      <div className="space-y-5">
        {nodes.map((node, idx) => (
          <div key={idx} className="flex items-center justify-between relative gap-3">
            {idx < nodes.length - 1 && (
              <div className="absolute left-3.5 top-6 w-[1.5px] h-6 bg-gray-150/80"></div>
            )}
            <div className="flex items-center gap-3">
              <div className="size-7 rounded-full bg-gray-50 border border-gray-200 flex items-center justify-center shrink-0 z-10">
                {node.icon}
              </div>
              <span className="text-[12.5px] font-semibold text-gray-800 truncate max-w-[420px]">{node.label}</span>
            </div>
            <span className="text-[11px] text-gray-400 font-mono font-medium">{node.duration}</span>
          </div>
        ))}
      </div>
      <div className="border-t border-gray-100 mt-5 pt-4 flex items-center justify-between">
        <span className="text-[12px] text-gray-500 font-medium">本次分析已经结束啦，如果还有什么想了解的，可以继续提问哦</span>
        <span className="text-[10.5px] font-bold text-emerald-600 bg-emerald-50 border border-emerald-100 px-2 py-0.5 rounded-full flex items-center gap-0.5 shrink-0">
          <Check className="size-3 stroke-[3]" /> 任务已完成
        </span>
      </div>
    </div>
  );
};

// ================= 子组件：执行计划步骤条 =================
interface ExecutionStep {
  step: number;
  tool_to_use: string;
  tool_parameters?: {
    instruction?: string;
    summary_and_recommendations?: string;
    sql_query?: string;
  };
}

interface Plan {
  thought_process: string;
  execution_plan: ExecutionStep[];
}

const parsePlanJson = (planJson?: string): Plan | null => {
  return parseStreamingPlan(planJson) as Plan | null;
};

const buildHumanFeedbackPlanPreview = (planJson?: string) => {
  const plan = parsePlanJson(planJson);
  const steps = plan?.execution_plan || [];
  if (steps.length === 0) {
    return planJson?.trim() || '';
  }

  return steps
    .map((step) => {
      const detail = step.tool_parameters?.instruction || step.tool_parameters?.summary_and_recommendations || step.tool_to_use || '执行计划步骤';
      return `# ${step.step}. ${detail}`;
    })
    .join(' <br> ');
};

// ================= 子组件：SQL/Python 代码框 =================
const tokenClassName: Record<CodeToken['type'], string> = {
  plain: 'text-gray-700',
  keyword: 'text-indigo-650 font-semibold',
  string: 'text-emerald-700',
  number: 'text-amber-700',
  comment: 'text-gray-400 italic',
  operator: 'text-sky-700',
};

const getCodeLanguageLabel = (language: CodeLanguage) => {
  if (language === 'json') return 'JSON';
  return language === 'python' ? 'Python' : 'SQL';
};

const CodeBlock: React.FC<{ language: CodeLanguage; code: string; defaultOpen?: boolean }> = ({ language, code, defaultOpen = false }) => {
  const [copied, setCopied] = useState(false);
  const [isOpen, setIsOpen] = useState(defaultOpen);
  const [isWrapped, setIsWrapped] = useState(false);
  const displayCode = normalizeCodeForDisplay(code, language);
  const tokenLines = tokenizeCodeForDisplay(code, language);
  const languageLabel = getCodeLanguageLabel(language);
  const handleCopy = () => {
    navigator.clipboard.writeText(displayCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="my-1 w-full overflow-hidden rounded-lg border border-gray-200 bg-[#F6F7F9] font-mono text-[12px] leading-relaxed shadow-2xs select-text">
      <div className={clsx(
        'flex min-h-12 items-center gap-3 px-3 py-2 select-none',
        isOpen && 'border-b border-gray-200'
      )}>
        <button
          type="button"
          onClick={() => setIsOpen(prev => !prev)}
          aria-expanded={isOpen}
          className="inline-flex min-w-0 flex-1 items-center gap-2 border-0 bg-transparent p-0 text-left text-gray-600 cursor-pointer"
        >
          {isOpen ? <ChevronDown className="size-4 shrink-0" /> : <ChevronRight className="size-4 shrink-0" />}
          <span className="truncate text-[13px] font-medium">代码块</span>
        </button>
        <div className="ml-auto flex items-center gap-2 text-gray-500">
          <span className="text-[13px] font-medium">{languageLabel}</span>
          <span className="h-5 w-px bg-gray-300" />
          <button
            type="button"
            onClick={() => setIsWrapped(prev => !prev)}
            className={clsx(
              'inline-flex h-8 items-center gap-1.5 rounded-md px-2 text-[12px] font-medium transition-colors cursor-pointer',
              isWrapped ? 'bg-white text-gray-800 shadow-2xs' : 'text-gray-500 hover:bg-white hover:text-gray-800'
            )}
            aria-pressed={isWrapped}
            title="自动换行"
          >
            <WrapText className="size-4" />
            <span>自动换行</span>
          </button>
          <button
            type="button"
            onClick={handleCopy}
            className="inline-flex h-8 items-center gap-1.5 rounded-md px-2 text-[12px] font-medium text-gray-500 transition-colors hover:bg-white hover:text-gray-800 cursor-pointer"
            title="复制代码"
          >
            <Copy className="size-4" />
            <span>{copied ? "已复制" : "复制"}</span>
          </button>
        </div>
      </div>
      {isOpen && (
        <pre className={clsx(
          'max-h-[520px] overflow-auto p-4 text-[12px] leading-relaxed',
          isWrapped ? 'whitespace-pre-wrap break-words' : 'whitespace-pre'
        )}>
          <code>
            {tokenLines.map((line, lineIndex) => (
              <React.Fragment key={`line-${lineIndex}`}>
                {line.map((token, tokenIndex) => (
                  <span key={`${lineIndex}-${tokenIndex}`} className={tokenClassName[token.type]}>
                    {token.text}
                  </span>
                ))}
                {lineIndex < tokenLines.length - 1 && '\n'}
              </React.Fragment>
            ))}
          </code>
        </pre>
      )}
    </div>
  );
};

const StructuredAnalysisOutputBlock: React.FC<{ content: string }> = ({ content }) => {
  const [copied, setCopied] = useState(false);
  const [isOpen, setIsOpen] = useState(false);
  const [isWrapped, setIsWrapped] = useState(false);
  const formatted = formatStructuredAnalysisOutput(content);

  const handleCopy = () => {
    navigator.clipboard.writeText(formatted);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="mt-3 w-full overflow-hidden rounded-lg border border-gray-200 bg-[#F6F7F9] font-mono text-[12px] leading-relaxed shadow-2xs select-text">
      <div className={clsx(
        'flex min-h-12 items-center gap-3 px-3 py-2 select-none',
        isOpen && 'border-b border-gray-200'
      )}>
        <button
          type="button"
          onClick={() => setIsOpen(prev => !prev)}
          aria-expanded={isOpen}
          className="inline-flex min-w-0 flex-1 items-center gap-2 border-0 bg-transparent p-0 text-left text-gray-600 cursor-pointer"
        >
          {isOpen ? <ChevronDown className="size-4 shrink-0" /> : <ChevronRight className="size-4 shrink-0" />}
          <span className="truncate text-[13px] font-medium">代码块</span>
        </button>
        <div className="ml-auto flex items-center gap-2 text-gray-500">
          <span className="text-[13px] font-medium">JSON</span>
          <span className="h-5 w-px bg-gray-300" />
          <button
            type="button"
            onClick={() => setIsWrapped(prev => !prev)}
            className={clsx(
              'inline-flex h-8 items-center gap-1.5 rounded-md px-2 text-[12px] font-medium transition-colors cursor-pointer',
              isWrapped ? 'bg-white text-gray-800 shadow-2xs' : 'text-gray-500 hover:bg-white hover:text-gray-800'
            )}
            aria-pressed={isWrapped}
            title="自动换行"
          >
            <WrapText className="size-4" />
            <span>自动换行</span>
          </button>
          <button
            type="button"
            onClick={handleCopy}
            className="inline-flex h-8 items-center gap-1.5 rounded-md px-2 text-[12px] font-medium text-gray-500 transition-colors hover:bg-white hover:text-gray-800 cursor-pointer"
            title="复制代码"
          >
            <Copy className="size-4" />
            <span>{copied ? '已复制' : '复制'}</span>
          </button>
        </div>
      </div>
      {isOpen && (
        <pre className={clsx(
          'max-h-[520px] overflow-auto p-4 text-[12px] leading-relaxed text-gray-700',
          isWrapped ? 'whitespace-pre-wrap break-words' : 'whitespace-pre'
        )}>
          {formatted}
        </pre>
      )}
    </div>
  );
};

// ================= 子组件：SQL 执行结果集表格 =================
const extractJsonPayload = (value: string) => {
  const stripped = value
    .replace('$$$result_set', '')
    .replace(/\$\$\$/g, '')
    .trim();
  const objectStart = stripped.indexOf('{');
  const arrayStart = stripped.indexOf('[');
  const starts = [objectStart, arrayStart].filter((pos) => pos >= 0);
  if (starts.length === 0) return stripped;
  const start = Math.min(...starts);
  const open = stripped[start];
  const close = open === '[' ? ']' : '}';
  const end = stripped.lastIndexOf(close);
  return end >= start ? stripped.slice(start, end + 1).trim() : stripped.slice(start).trim();
};

type EmbeddedResultSetPayload = {
  payload: string;
  start: number;
  end: number;
};

const extractEmbeddedResultSetPayloads = (value: string): EmbeddedResultSetPayload[] => {
  const payloads: EmbeddedResultSetPayload[] = [];
  let searchIndex = 0;

  while (searchIndex < value.length) {
    const resultSetIndex = value.indexOf('"resultSet"', searchIndex);
    if (resultSetIndex < 0) break;

    const objectStart = value.lastIndexOf('{', resultSetIndex);
    if (objectStart < 0) break;

    const alreadyCaptured = payloads.some((payload) => objectStart >= payload.start && objectStart < payload.end);
    if (alreadyCaptured) {
      searchIndex = resultSetIndex + '"resultSet"'.length;
      continue;
    }

    let depth = 0;
    let inString = false;
    let escaped = false;

    for (let i = objectStart; i < value.length; i += 1) {
      const char = value[i];

      if (escaped) {
        escaped = false;
        continue;
      }

      if (char === '\\') {
        escaped = true;
        continue;
      }

      if (char === '"') {
        inString = !inString;
        continue;
      }

      if (inString) continue;

      if (char === '{') depth += 1;
      if (char === '}') {
        depth -= 1;
        if (depth === 0) {
          payloads.push({
            payload: value.slice(objectStart, i + 1),
            start: objectStart,
            end: i + 1,
          });
          searchIndex = i + 1;
          break;
        }
      }
    }

    if (searchIndex <= resultSetIndex) {
      searchIndex = resultSetIndex + '"resultSet"'.length;
    }
  }

  return payloads;
};

const extractEmbeddedResultSetPayload = (value: string) => {
  return extractEmbeddedResultSetPayloads(value)[0]?.payload || '';
};

type ParsedResultSet = {
  columns: string[];
  rows: any[];
};

const normalizeResultRows = (columns: string[], data: any[]): any[] => {
  if (!Array.isArray(data)) return [];
  return data.map((row) => {
    if (Array.isArray(row)) {
      return columns.reduce<Record<string, any>>((acc, column, index) => {
        acc[column] = row[index] ?? '';
        return acc;
      }, {});
    }
    return row;
  });
};

const parseResultSetData = (dataJson: string): ParsedResultSet | null => {
  try {
    const cleanJson = extractJsonPayload(dataJson);
    if (cleanJson.startsWith('[') && cleanJson.endsWith(']')) {
      const rows = JSON.parse(cleanJson);
      if (!Array.isArray(rows) || rows.length === 0) return null;
      const columns = Object.keys(rows[0] || {});
      return columns.length > 0 ? { columns, rows } : null;
    }

    if (!cleanJson.startsWith('{') || !cleanJson.endsWith('}')) return null;

    const parsedObj = JSON.parse(cleanJson);
    const resultSet = parsedObj?.resultSet || parsedObj;
    const rawData = Array.isArray(resultSet?.data) ? resultSet.data : [];
    const rawColumns = Array.isArray(resultSet?.columns) ? resultSet.columns : [];

    const columns = rawColumns.length > 0
      ? rawColumns.map(String)
      : rawData.length > 0 && !Array.isArray(rawData[0])
        ? Object.keys(rawData[0] || {})
        : [];
    const rows = normalizeResultRows(columns, rawData);

    return columns.length > 0 && rows.length > 0 ? { columns, rows } : null;
  } catch (e) {
    return null;
  }
};

const ResultSetTable: React.FC<{ dataJson: string }> = ({ dataJson }) => {
  const parsed = parseResultSetData(dataJson);
  const rows = parsed?.rows || [];
  const columns = parsed?.columns || [];

  if (rows.length === 0) {
    return (
      <div className="my-2 p-3 text-xs text-gray-400 font-semibold animate-pulse">
        加载数据结果集中...
      </div>
    );
  }

  return (
    <div className="my-1 overflow-hidden rounded-xl border border-gray-150 bg-white w-full select-text shadow-2xs">
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse text-[11px] leading-relaxed">
          <thead>
            <tr className="border-b border-gray-150 bg-gray-50/70 text-gray-500 font-bold uppercase select-none">
              {columns.map((col) => (
                <th key={col} className="px-3 py-2 border-r border-gray-100 last:border-r-0 whitespace-nowrap">{col}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 font-medium text-gray-700">
            {rows.map((row, rIdx) => (
              <tr key={rIdx} className="hover:bg-gray-50/50 transition-colors">
                {columns.map((col) => (
                  <td key={col} className="px-3 py-1.5 border-r border-gray-100 last:border-r-0 whitespace-nowrap">{row[col]}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

// ================= 辅助函数：从流式拼接的增量 JSON 中提取 reply 字段 =================
function extractReplyFromIncrementalJson(jsonStr: string): string {
  try {
    const clean = jsonStr.replace('$$$json', '').trim();
    if (clean.startsWith('{') && clean.endsWith('}')) {
      const parsed = JSON.parse(clean);
      return parsed.reply || '';
    }
  } catch (e) {
    // 捕获流式拼接期间 JSON 不完整导致的解析失败
  }
  
  // 使用正则提取，确保流式文字在未接收完时也能实时回显
  const match = jsonStr.match(/"reply"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"?/);
  if (match && match[1]) {
    return match[1].replace(/\\n/g, '\n').replace(/\\"/g, '"');
  }
  return '';
}

// ================= 子组件：闲聊/无关指令友好引导及数据添加面板 =================
interface SmalltalkDataPanelProps {
  reply: string;
  latestQuery: string;
  onConfirmData: (file: { id: string; name: string; size: string }) => void;
}

const SmalltalkDataPanel: React.FC<SmalltalkDataPanelProps> = ({ reply, latestQuery, onConfirmData }) => {
  const [isOpen, setIsOpen] = useState(true);
  const [addType, setAddType] = useState<'upload' | 'existing'>('upload');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [dbName, setDbName] = useState('内置_游戏数据');
  const [tableName, setTableName] = useState('内置_游戏数据.csv');
  const localFileRef = useRef<HTMLInputElement>(null);

  const isGreeting = /你好|您好|嗨|hello|hi/i.test(latestQuery);
  const guideText = isGreeting
    ? "请上传您的CSV或Excel文件，或者提供数据库连接信息，以便我开始为您进行智能分析。"
    : "为了能够为您提供精准的数据分析服务，请您上传CSV/Excel等格式的数据集，或者提供安全的数据库连接信息。";

  const handleLocalFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
    }
  };

  const handleConfirm = () => {
    if (addType === 'upload' && selectedFile) {
      const sizeStr = selectedFile.size > 1024 * 1024
        ? `${(selectedFile.size / (1024 * 1024)).toFixed(1)} MB`
        : `${(selectedFile.size / 1024).toFixed(1)} KB`;
      onConfirmData({
        id: `upload-${Date.now()}`,
        name: selectedFile.name,
        size: sizeStr
      });
    } else if (addType === 'existing') {
      onConfirmData({
        id: `existing-${Date.now()}`,
        name: tableName,
        size: '15.4 KB'
      });
    }
  };

  return (
    <div className="w-full flex flex-col items-start select-none">
      {/* 了解用户需求折叠面板 (官网风格) */}
      <div className="bg-white border border-gray-200/80 rounded-xl shadow-3xs my-2 overflow-hidden transition-all duration-200 w-full max-w-[620px]">
        <button 
          onClick={() => setIsOpen(!isOpen)}
          type="button"
          className="w-full flex items-center justify-between px-4 py-3 bg-[#FAFAFC] cursor-pointer hover:bg-gray-150/40 transition-colors border-none outline-none"
        >
          <div className="flex items-center gap-2.5">
            <span className="shrink-0 flex items-center justify-center size-5 rounded-md bg-indigo-50 border border-indigo-100">
              <Check className="size-3 text-indigo-600 stroke-[3.5]" />
            </span>
            <span className="text-xs font-bold text-gray-800">了解用户需求</span>
          </div>
          <ChevronRight className={clsx("w-4 h-4 text-gray-400 transition-transform duration-200", isOpen && "rotate-90")} />
        </button>
        {isOpen && (
          <div className="px-4 pb-4 pt-2.5 bg-white border-t border-gray-100 text-[13px] text-gray-700 leading-relaxed font-normal whitespace-pre-wrap animate-in fade-in duration-150 select-text">
            {reply || "正在思考..."}
          </div>
        )}
      </div>

      {/* 引导上传提示 */}
      {reply && (
        <div className="text-[13px] text-gray-700 font-normal leading-relaxed my-2.5 animate-in fade-in duration-300">
          {guideText}
        </div>
      )}

      {/* 添加数据表单 */}
      {reply && (
        <div className="bg-[#FAFAFC] border border-gray-200/60 rounded-xl p-5 shadow-3xs w-full max-w-[620px] my-1 flex flex-col gap-4 text-xs animate-in fade-in duration-400">
          {/* 添加方式 Radio */}
          <div className="flex flex-col gap-2">
            <span className="text-gray-600 font-bold flex items-center">
              <span className="text-red-500 mr-1">*</span> 添加方式
            </span>
            <div className="flex items-center gap-6 mt-0.5">
              {/* 本地上传 */}
              <button 
                type="button"
                onClick={() => setAddType('upload')}
                className="flex items-center gap-2 text-gray-700 hover:text-indigo-600 transition-all outline-none border-none bg-transparent cursor-pointer font-semibold text-[12px]"
              >
                <span className={clsx(
                  "size-4 rounded-full border flex items-center justify-center transition-all bg-white",
                  addType === 'upload' ? "border-indigo-600 ring-1 ring-indigo-600" : "border-gray-300"
                )}>
                  {addType === 'upload' && <span className="size-2 rounded-full bg-indigo-600 animate-in zoom-in-50 duration-150"></span>}
                </span>
                <span>本地上传</span>
              </button>

              {/* 选择已有数据 */}
              <button 
                type="button"
                onClick={() => setAddType('existing')}
                className="flex items-center gap-2 text-gray-700 hover:text-indigo-600 transition-all outline-none border-none bg-transparent cursor-pointer font-semibold text-[12px]"
              >
                <span className={clsx(
                  "size-4 rounded-full border flex items-center justify-center transition-all bg-white",
                  addType === 'existing' ? "border-indigo-600 ring-1 ring-indigo-600" : "border-gray-300"
                )}>
                  {addType === 'existing' && <span className="size-2 rounded-full bg-indigo-600 animate-in zoom-in-50 duration-150"></span>}
                </span>
                <span>选择已有数据</span>
              </button>
            </div>
          </div>

          {/* 表单主体 */}
          {addType === 'upload' ? (
            <div className="flex flex-col gap-2 animate-in fade-in duration-200">
              <span className="text-gray-600 font-bold">
                <span className="text-red-500 mr-1">*</span> 上传文件
              </span>
              <input 
                type="file" 
                ref={localFileRef} 
                onChange={handleLocalFileChange} 
                className="hidden" 
                accept=".csv,.xlsx,.xls"
              />
              <div 
                onClick={() => localFileRef.current?.click()}
                className="border border-dashed border-gray-300 hover:border-indigo-400 hover:bg-indigo-50/5 rounded-xl py-6 px-4 flex flex-col items-center justify-center gap-2 cursor-pointer transition-all bg-white"
              >
                <Upload className="size-5.5 text-gray-400 shrink-0" />
                {selectedFile ? (
                  <span className="text-indigo-600 font-bold truncate max-w-[280px]">
                    已选择：{selectedFile.name}
                  </span>
                ) : (
                  <div className="text-center space-y-1">
                    <span className="text-gray-600 font-bold block">点击或将文件拖拽至此上传</span>
                    <span className="text-gray-400 text-[10px] block font-medium">支持xlsx、xls、csv格式，文件最大200MB</span>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="flex flex-col gap-4 animate-in fade-in duration-200">
              <div className="flex flex-col gap-2">
                <span className="text-gray-600 font-bold">
                  <span className="text-red-500 mr-1">*</span> 数据库/文件
                </span>
                <div className="relative w-full">
                  <select 
                    value={dbName} 
                    onChange={(e) => {
                      setDbName(e.target.value);
                      if (e.target.value === '内置_游戏数据') {
                        setTableName('内置_游戏数据.csv');
                      } else {
                        setTableName('内置_餐厅数据.csv');
                      }
                    }}
                    className="w-full appearance-none border border-gray-200 bg-white rounded-lg h-9 px-3.5 pr-10 outline-none font-semibold focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 text-gray-700 text-[12px] transition-all cursor-pointer shadow-3xs"
                  >
                    <option value="内置_游戏数据">内置_游戏数据</option>
                    <option value="内置_餐厅数据">内置_餐厅数据</option>
                  </select>
                  <ChevronDown className="absolute right-3.5 top-2.5 size-4 text-gray-400 pointer-events-none" />
                </div>
              </div>

              <div className="flex flex-col gap-2">
                <span className="text-gray-600 font-bold">
                  <span className="text-red-500 mr-1">*</span> 表/文件
                </span>
                <div className="relative w-full">
                  <select 
                    value={tableName} 
                    onChange={(e) => setTableName(e.target.value)}
                    className="w-full appearance-none border border-gray-200 bg-white rounded-lg h-9 px-3.5 pr-10 outline-none font-semibold focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 text-gray-700 text-[12px] transition-all cursor-pointer shadow-3xs"
                  >
                    {dbName === '内置_游戏数据' ? (
                      <option value="内置_游戏数据.csv">内置_游戏数据.csv</option>
                    ) : (
                      <option value="内置_餐厅数据.csv">内置_餐厅数据.csv</option>
                    )}
                  </select>
                  <ChevronDown className="absolute right-3.5 top-2.5 size-4 text-gray-400 pointer-events-none" />
                </div>
              </div>
            </div>
          )}

          {/* 确认按钮 */}
          <div className="flex justify-end mt-1">
            <button 
              onClick={handleConfirm}
              className="px-4 py-2 rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 font-bold tracking-wide transition-all shadow-sm active:scale-95 cursor-pointer"
            >
              确认添加
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

const isBackendProgressText = (text: string) => {
  const trimmed = text.trim();
  if (!trimmed) return false;
  if (trimmed.includes('您好')) return false;
  if (trimmed.includes('$$$')) return false;
  return (
    trimmed.includes('完成') ||
    trimmed.includes('开始') ||
    trimmed.includes('进行中') ||
    trimmed.includes('召回') ||
    trimmed.includes('获取数据') ||
    trimmed.includes('正在') ||
    trimmed.includes('通过') ||
    trimmed.includes('即将执行') ||
    trimmed.includes('未找到任何证据') ||
    trimmed.includes('重写后查询') ||
    trimmed.includes('执行成功') ||
    trimmed.includes('[系统]')
  );
};

const extractWorkflowLogLines = (text: string) => {
  return text
    .split(/[\n，。！!;；\.]/)
    .map(line => line.trim())
    .filter(line => line.length > 2 && isBackendProgressText(line));
};

const collectWorkflowLogs = (blocks?: ContentBlock[] | MessageBlock[]) => {
  const seen = new Set<string>();
  return (blocks || [])
    .filter(block => block.type === 'text')
    .flatMap(block => extractWorkflowLogLines(block.content))
    .filter((line) => {
      const normalized = line.replace(/\s+/g, ' ');
      if (seen.has(normalized)) return false;
      seen.add(normalized);
      return true;
    });
};

type WorkflowNodeStatus = 'done' | 'active' | 'pending';

interface WorkflowNodeCardProps {
  title: string;
  summary?: string;
  status?: WorkflowNodeStatus;
  icon?: React.ReactNode;
  defaultOpen?: boolean;
  children?: React.ReactNode;
}

const WorkflowNodeCard: React.FC<WorkflowNodeCardProps> = ({
  title,
  summary,
  status = 'done',
  icon,
  defaultOpen = false,
  children,
}) => {
  const [isOpen, setIsOpen] = useState(defaultOpen);
  const hasBody = Boolean(children);

  useEffect(() => {
    setIsOpen(defaultOpen);
  }, [defaultOpen]);

  return (
    <div className="w-full overflow-hidden rounded-xl border border-gray-200 bg-white shadow-xs transition-shadow duration-200 ease-out select-none">
      <button
        type="button"
        aria-expanded={hasBody ? isOpen : undefined}
        onClick={() => hasBody && setIsOpen(prev => !prev)}
        className="flex min-h-12 w-full items-center gap-3 border-0 bg-white px-5 py-3 text-left transition-colors hover:bg-gray-50/70 cursor-pointer"
      >
        <ChevronDown className={clsx('size-4 shrink-0 text-gray-500 transition-transform duration-200 ease-out', isOpen && 'rotate-180', !hasBody && 'opacity-40')} />
        <span className="grid size-6 shrink-0 place-items-center rounded-lg bg-gray-50 text-gray-500">
          {icon || <Clock className="size-3.5" />}
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 items-center gap-2">
            <span className="truncate text-[15px] font-semibold text-gray-850">{title}</span>
            <span className={clsx(
              'shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold',
              status === 'done' && 'bg-emerald-50 text-emerald-600',
              status === 'active' && 'bg-[#EEEAFE] text-[#5B55FF]',
              status === 'pending' && 'bg-gray-100 text-gray-400',
            )}>
              {status === 'done' ? '已完成' : status === 'active' ? '执行中' : '待执行'}
            </span>
          </div>
          {summary && (
            <p className={clsx(
              'm-0 overflow-hidden truncate text-[13px] leading-5 text-gray-500 transition-[max-height,opacity,margin] duration-200 ease-out motion-reduce:transition-none',
              isOpen ? 'mt-0 max-h-0 opacity-0' : 'mt-1 max-h-5 opacity-100',
            )}>
              {summary}
            </p>
          )}
        </div>
      </button>
      {hasBody && (
        <div
          aria-hidden={!isOpen}
          className={clsx(
            'grid transition-[grid-template-rows,opacity] duration-200 ease-out motion-reduce:transition-none',
            isOpen ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0 pointer-events-none',
          )}
        >
          <div className={clsx(
            'min-h-0 overflow-hidden border-t border-gray-100 bg-[#FAFAFC] px-5 select-text transition-[padding] duration-200 ease-out motion-reduce:transition-none',
            isOpen ? 'py-4' : 'py-0',
          )}>
            {children}
          </div>
        </div>
      )}
    </div>
  );
};

const getBlockByType = (blocks: ContentBlock[] | MessageBlock[] | undefined, type: ContentBlock['type']) => {
  return blocks?.find(block => block.type === type);
};

const getResultSetBlock = (blocks: ContentBlock[] | MessageBlock[] | undefined): ContentBlock | MessageBlock | undefined => {
  const resultBlocks = blocks?.filter(block => block.type === 'result_set') || [];
  const parseableResultBlock = resultBlocks.find((block) => parseResultSetData(block.content));
  if (parseableResultBlock) return parseableResultBlock;

  const embeddedPayload = blocks
    ?.filter(block => block.type === 'text')
    .map(block => extractEmbeddedResultSetPayload(block.content))
    .find((payload) => Boolean(payload && parseResultSetData(payload)));

  return embeddedPayload ? { type: 'result_set', content: embeddedPayload } : resultBlocks[0];
};

const getWorkflowPlanBlock = (blocks?: ContentBlock[] | MessageBlock[]) => {
  return blocks?.find(block => block.type === 'json' && (block.content.includes('"execution_plan"') || block.content.includes('"thought_process"')));
};

const getLinesByKeywords = (logs: string[], keywords: string[]) => {
  return logs.filter(log => keywords.some(keyword => log.includes(keyword)));
};

const summarizeLines = (lines: string[], fallback: string) => {
  return lines.length > 0 ? lines[lines.length - 1] : fallback;
};

interface WorkflowNodeStackProps {
  blocks?: ContentBlock[] | MessageBlock[];
  logs: string[];
  isComplete: boolean;
  currentBlockCount: number;
  onOpenReport: (content: string) => void;
}

const WorkflowLoadingDots: React.FC = () => (
  <div className="mt-3 flex h-5 items-center gap-1.5 text-gray-400" aria-label="节点执行中">
    <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-gray-400" />
    <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-gray-400 delay-100" />
    <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-gray-400 delay-200" />
  </div>
);

const WorkflowLogList: React.FC<{ lines: string[] }> = ({ lines }) => {
  if (lines.length === 0) return null;
  return (
    <div className="space-y-1.5 text-[13px] leading-6 text-gray-600">
      {lines.map((line, index) => (
        <p key={`${line}-${index}`} className="m-0">{line}</p>
      ))}
    </div>
  );
};

const getToolLabel = (tool?: string) => {
  const normalized = (tool || '').trim().toLowerCase();
  if (normalized === 'sql_generate') return 'SQL 查询';
  if (normalized.includes('python')) return 'Python 分析';
  if (normalized === 'report_generator') return '报告生成';
  return normalized || '工作流步骤';
};

const ExecutionPlanDetails: React.FC<{ planJson: string }> = ({ planJson }) => {
  const plan = extractExecutionPlanView(planJson);

  if (!plan) {
    return (
      <div className="space-y-3">
        <div className="h-3 w-3/4 animate-pulse rounded bg-gray-100" />
        <div className="h-3 w-1/2 animate-pulse rounded bg-gray-100" />
      </div>
    );
  }

  const steps = plan.steps || [];

  return (
    <div className="space-y-3">
      {plan.thoughtProcess && (
        <p className="m-0 text-[13px] leading-6 text-gray-650">
          {plan.thoughtProcess}
        </p>
      )}
      <ol className="m-0 space-y-2.5 p-0">
        {steps.map((step, index) => {
          return (
            <li key={`${step.tool}-${step.step}-${index}`} className="flex gap-3 rounded-lg border border-gray-150 bg-white px-3.5 py-3">
              <div className="pt-0.5">
                <span className="grid size-5 shrink-0 place-items-center rounded-full bg-[#EEEAFE] text-[11px] font-bold text-[#5B55FF]">
                  {step.step || index + 1}
                </span>
              </div>
              <div className="min-w-0 flex-1">
                <div className="mb-1 flex items-center gap-2">
                  <span className="min-w-0 truncate text-[13px] font-bold text-gray-850">
                    {step.tool.trim().toUpperCase()}
                  </span>
                  <span className="ml-auto shrink-0 rounded-full bg-gray-100 px-2 py-0.5 text-[10px] font-semibold text-gray-500">
                    {getToolLabel(step.tool)}
                  </span>
                </div>
                <span className="block text-[12.5px] leading-6 text-gray-600">
                  {step.instruction}
                </span>
              </div>
            </li>
          );
        })}
      </ol>
    </div>
  );
};

const WorkflowNodeStack: React.FC<WorkflowNodeStackProps> = ({
  blocks = [],
  logs,
  isComplete,
  onOpenReport,
}) => {
  const planBlock = getWorkflowPlanBlock(blocks);
  const sqlBlock = getBlockByType(blocks, 'sql');
  const pythonBlock = getBlockByType(blocks, 'python');
  const resultSetBlock = getResultSetBlock(blocks);
  const reportBlock = getBlockByType(blocks, 'markdown-report');

  const intentLogs = getLinesByKeywords(logs, ['意图识别', '了解用户', '理解用户']);
  const evidenceLogs = getLinesByKeywords(logs, ['知识库', '证据', '查询重写', '召回证据', '正在获取证据']);
  const queryEnhanceLogs = getLinesByKeywords(logs, ['问题增强']);
  const schemaLogs = getLinesByKeywords(logs, ['Schema', '数据结构', '表关系', '候选集', '召回成功']);
  const feasibilityLogs = getLinesByKeywords(logs, ['可执行', '评估', '计划校验']);
  const planLogs = getLinesByKeywords(logs, ['生成执行计划', '执行计划生成完成', '固定执行计划', '人工复核', '人工审核']);
  const sqlLogs = getLinesByKeywords(logs, ['SQL', '语义一致性', '正在修复SQL']);
  const resultLogs = getLinesByKeywords(logs, ['结果集', '渲染展示图表', 'SQL 执行成功']);
  const pythonLogs = getLinesByKeywords(logs, ['Python', '分析引擎', '运行结果']);
  const reportLogs = getLinesByKeywords(logs, ['报告', '分析报告', '开始整理']);
  const pythonOutputBlock = getPythonExecutionOutputBlock(blocks);

  const hasAnyWorkflowNode =
    intentLogs.length > 0 ||
    evidenceLogs.length > 0 ||
    queryEnhanceLogs.length > 0 ||
    schemaLogs.length > 0 ||
    feasibilityLogs.length > 0 ||
    planLogs.length > 0 ||
    planBlock ||
    sqlBlock ||
    sqlLogs.length > 0 ||
    pythonBlock ||
    pythonOutputBlock ||
    resultSetBlock ||
    resultLogs.length > 0 ||
    pythonLogs.length > 0 ||
    reportBlock;

  if (!hasAnyWorkflowNode) return null;

  const hasCompletedLog = (lines: string[]) => lines.some(line => (
    line.includes('完成') ||
    line.includes('成功') ||
    line.includes('通过') ||
    line.includes('完毕')
  ));

  const statusFrom = (done: boolean): WorkflowNodeStatus => done ? 'done' : 'active';
  const hasPlanOutput = Boolean(planBlock);
  const hasSqlOutput = Boolean(sqlBlock);
  const hasResultOutput = Boolean(resultSetBlock);
  const hasPythonOutput = Boolean(pythonBlock || pythonOutputBlock);
  const hasPythonExecutionOutput = Boolean(pythonOutputBlock);
  const hasReportOutput = Boolean(reportBlock);
  const hasPlanningStage = planLogs.length > 0 || hasPlanOutput;
  const planStageShouldOpen = hasPlanningStage && !hasSqlOutput && !hasResultOutput && !hasPythonOutput && !hasReportOutput;
  const sqlStageShouldOpen = (Boolean(sqlBlock) || sqlLogs.length > 0) && !hasResultOutput && !hasPythonOutput && !hasReportOutput;
  const resultStageShouldOpen = (Boolean(resultSetBlock) || resultLogs.length > 0) && !hasPythonOutput && !hasReportOutput;
  const pythonStageShouldOpen = (Boolean(pythonBlock) || hasPythonExecutionOutput || pythonLogs.length > 0) && !hasReportOutput;

  return (
    <div className="my-3 flex w-full max-w-[680px] flex-col gap-2.5">
      {intentLogs.length > 0 && (
        <WorkflowNodeCard
          title="理解用户需求"
          summary={summarizeLines(intentLogs, '识别用户意图并确认分析目标。')}
          status={statusFrom(hasCompletedLog(intentLogs) || evidenceLogs.length > 0 || queryEnhanceLogs.length > 0 || schemaLogs.length > 0 || hasPlanOutput)}
          icon={<Search className="size-3.5" />}
          defaultOpen={!hasCompletedLog(intentLogs) && !hasPlanOutput}
        >
          <WorkflowLogList lines={intentLogs} />
          {!hasCompletedLog(intentLogs) && <WorkflowLoadingDots />}
        </WorkflowNodeCard>
      )}

      {evidenceLogs.length > 0 && (
        <WorkflowNodeCard
          title="检索知识库"
          summary={summarizeLines(evidenceLogs, '检索知识库与历史上下文，整理分析口径。')}
          status={statusFrom(hasCompletedLog(evidenceLogs) || queryEnhanceLogs.length > 0 || schemaLogs.length > 0 || hasPlanOutput)}
          icon={<BookOpen className="size-3.5" />}
          defaultOpen={!hasCompletedLog(evidenceLogs) && !hasPlanOutput}
        >
          <WorkflowLogList lines={evidenceLogs} />
          {!hasCompletedLog(evidenceLogs) && <WorkflowLoadingDots />}
        </WorkflowNodeCard>
      )}

      {queryEnhanceLogs.length > 0 && (
        <WorkflowNodeCard
          title="增强分析问题"
          summary={summarizeLines(queryEnhanceLogs, '规范化用户问题并扩展后续数据召回查询。')}
          status={statusFrom(hasCompletedLog(queryEnhanceLogs) || schemaLogs.length > 0 || hasPlanOutput)}
          icon={<Sparkles className="size-3.5" />}
          defaultOpen={!hasCompletedLog(queryEnhanceLogs) && !hasPlanOutput}
        >
          <WorkflowLogList lines={queryEnhanceLogs} />
          {!hasCompletedLog(queryEnhanceLogs) && <WorkflowLoadingDots />}
        </WorkflowNodeCard>
      )}

      {schemaLogs.length > 0 && (
        <WorkflowNodeCard
          title="定位可用数据结构"
          summary={summarizeLines(schemaLogs, '召回 Schema，筛选本次分析所需表和字段。')}
          status={statusFrom(hasCompletedLog(schemaLogs) || feasibilityLogs.length > 0 || hasPlanOutput)}
          icon={<Database className="size-3.5" />}
          defaultOpen={!hasCompletedLog(schemaLogs) && !hasPlanOutput}
        >
          <WorkflowLogList lines={schemaLogs} />
          {!hasCompletedLog(schemaLogs) && <WorkflowLoadingDots />}
        </WorkflowNodeCard>
      )}

      {feasibilityLogs.length > 0 && (
        <WorkflowNodeCard
          title="评估任务可执行性"
          summary={summarizeLines(feasibilityLogs, '判断当前问题是否能继续进入工作流执行。')}
          status={statusFrom(hasCompletedLog(feasibilityLogs) || hasPlanOutput)}
          icon={<CircleHelp className="size-3.5" />}
          defaultOpen={!hasCompletedLog(feasibilityLogs) && !hasPlanOutput}
        >
          <WorkflowLogList lines={feasibilityLogs} />
          {!hasCompletedLog(feasibilityLogs) && <WorkflowLoadingDots />}
        </WorkflowNodeCard>
      )}

      {hasPlanningStage && (
        <WorkflowNodeCard
          title="生成执行计划"
          summary={summarizeLines(planLogs, '已分析需求并生成工作流执行计划。')}
          status={statusFrom(hasCompletedLog(planLogs) || hasSqlOutput || hasResultOutput || isComplete)}
          icon={<GitBranch className="size-3.5" />}
          defaultOpen={planStageShouldOpen}
        >
          {planBlock ? (
            <ExecutionPlanDetails planJson={planBlock.content} />
          ) : (
            <>
              <WorkflowLogList lines={planLogs} />
              <WorkflowLoadingDots />
            </>
          )}
        </WorkflowNodeCard>
      )}

      {(sqlBlock || sqlLogs.length > 0) && (
        <WorkflowNodeCard
          title="生成 SQL"
          summary={summarizeLines(sqlLogs, '已生成 SQL，并进入语义一致性检查与执行。')}
          status={statusFrom(hasSqlOutput && (hasCompletedLog(sqlLogs) || hasResultOutput || isComplete))}
          icon={<LineChart className="size-3.5" />}
          defaultOpen={sqlStageShouldOpen}
        >
          {sqlBlock ? <CodeBlock language="sql" code={sqlBlock.content} /> : <WorkflowLoadingDots />}
          <div className={clsx(sqlBlock && sqlLogs.length > 0 && 'mt-3')}>
            <WorkflowLogList lines={sqlLogs} />
          </div>
        </WorkflowNodeCard>
      )}

      {(resultSetBlock || resultLogs.length > 0) && (
        <WorkflowNodeCard
          title="执行 SQL 并返回结果集"
          summary={summarizeLines(resultLogs, 'SQL 查询结果已返回。')}
          status={statusFrom(hasResultOutput && (hasPythonOutput || hasReportOutput || isComplete))}
          icon={<Database className="size-3.5" />}
          defaultOpen={resultStageShouldOpen}
        >
          {resultSetBlock ? <ResultSetTable dataJson={resultSetBlock.content} /> : <WorkflowLoadingDots />}
          <div className={clsx(resultSetBlock && resultLogs.length > 0 && 'mt-3')}>
            <WorkflowLogList lines={resultLogs} />
          </div>
        </WorkflowNodeCard>
      )}

      {(pythonBlock || pythonOutputBlock || pythonLogs.length > 0) && (
        <WorkflowNodeCard
          title="运行分析建模"
          summary={summarizeLines(pythonLogs, '已生成并执行 Python 分析代码。')}
          status={statusFrom(hasPythonOutput && (hasReportOutput || isComplete))}
          icon={<Code2 className="size-3.5" />}
          defaultOpen={pythonStageShouldOpen}
        >
          {pythonBlock ? <CodeBlock language="python" code={pythonBlock.content} /> : (!pythonOutputBlock && <WorkflowLoadingDots />)}
          {pythonOutputBlock && <StructuredAnalysisOutputBlock content={pythonOutputBlock.content} />}
          <div className={clsx((pythonBlock || pythonOutputBlock) && pythonLogs.length > 0 && 'mt-3')}>
            <WorkflowLogList lines={pythonLogs} />
          </div>
        </WorkflowNodeCard>
      )}

      {reportBlock && (
        <WorkflowNodeCard
          title="生成最终报告"
          summary={summarizeLines(reportLogs, 'Markdown 报告已生成，并在右侧报告栏打开。')}
          status={isComplete ? 'done' : 'active'}
          icon={<FileText className="size-3.5" />}
          defaultOpen={hasReportOutput}
        >
          <div className="flex items-center justify-between gap-3 rounded-xl border border-emerald-100 bg-emerald-50/60 px-4 py-3">
            <div className="min-w-0">
              <div className="text-[13px] font-bold text-gray-850">本次分析报告已生成</div>
              <div className="mt-0.5 truncate text-[11px] font-medium text-gray-500">右侧报告栏会展示本次对话生成的 Markdown 内容。</div>
            </div>
            <button
              type="button"
              onClick={() => onOpenReport(getMarkdownReportBlock([reportBlock]))}
              className="flex-none rounded-lg border border-emerald-200 bg-white px-3 py-1.5 text-xs font-bold text-emerald-700 transition-colors hover:bg-emerald-50 cursor-pointer"
            >
              查看报告
            </button>
          </div>
        </WorkflowNodeCard>
      )}
    </div>
  );
};

const shouldShowAnalysisTimeLine = (query: string, blocks?: ContentBlock[] | MessageBlock[]) => {
  const isDemoNarrative =
    query.includes('游戏') ||
    query.includes('营销') ||
    query.includes('销量') ||
    query.includes('Strategy') ||
    query.includes('SLG');
  return isDemoNarrative && Boolean(blocks?.some(block => block.type === 'markdown-report'));
};

const isStructuredWorkflowResidue = (text: string) => {
  const trimmed = text.trim();
  if (!trimmed) return false;

  const hasSqlLifecycleText =
    trimmed.includes('SQL生成完成') ||
    trimmed.includes('正在进行语义一致性检查') ||
    trimmed.includes('通过语义一致性检查完成') ||
    trimmed.includes('SQL 执行成功') ||
    trimmed.includes('正在渲染展示图表');

  const hasResultPayload =
    trimmed.includes('"resultSet"') ||
    trimmed.includes('"displayStyle"') ||
    trimmed.includes('$$$result_set');

  if (hasSqlLifecycleText && hasResultPayload) return true;
  if (hasResultPayload && trimmed.includes('$$$')) return true;
  if (trimmed.startsWith('{"resultSet"')) return true;

  return false;
};

const ProcessedTextBlock: React.FC<{ text: string; isComplete?: boolean; query?: string }> = ({ text, isComplete = false, query = "" }) => {
  const trimmed = text.trim();
  if (!trimmed) return null;
  if (trimmed.includes('等待人工复核反馈')) return null;
  if (isStructuredWorkflowResidue(trimmed)) return null;
  if (isPythonExecutionResidue(trimmed)) return null;
  if (isStructuredAnalysisOutput(trimmed)) {
    return (
      <TaskToolCard
        title="分析引擎输出"
        summary="Python 数据分析代码已返回结构化结果。"
        defaultOpen={false}
      >
        <StructuredAnalysisOutputBlock content={trimmed} />
      </TaskToolCard>
    );
  }

  // 0. 异常防御：捕获后端节点执行抛出的错误堆栈或 Exception 信息
  const lowerText = trimmed.toLowerCase();
  const isException = lowerText.includes('exception') || lowerText.includes('error:') || lowerText.includes('报错') || lowerText.includes('failed');
  if (isException) {
    const handleCopyError = () => {
      navigator.clipboard.writeText(trimmed);
    };
    return (
      <div className="my-2.5 border border-red-150 bg-red-50/30 rounded-xl p-4 flex items-start gap-3 shadow-3xs w-full max-w-[620px] select-text animate-in fade-in duration-200">
        <div className="shrink-0 flex items-center justify-center size-5 rounded-md bg-red-50 border border-red-200">
          <span className="text-red-600 font-bold text-[11px] select-none">!</span>
        </div>
        <div className="space-y-1 flex-1 min-w-0">
          <div className="flex items-center justify-between select-none">
            <span className="text-[12px] font-bold text-red-800">后台工作流执行异常</span>
            <button 
              onClick={handleCopyError}
              className="text-[9.5px] text-red-700 hover:text-red-900 border border-red-200 hover:bg-red-50 px-1.5 py-0.5 rounded transition-all font-bold cursor-pointer"
            >
              复制错误堆栈
            </button>
          </div>
          <span className="text-[10.5px] text-red-600/90 leading-relaxed font-mono block whitespace-pre-wrap select-all max-h-36 overflow-y-auto pr-1">
            {trimmed}
          </span>
        </div>
      </div>
    );
  }

  // 1. 去重美化后台细碎进度日志的显示，防止文本无序堆叠
  // 特征：包含各种开始、完成、召回，但长度适中且没有闲聊或交互报告的特征
  const isWorkflowLog = isBackendProgressText(trimmed);

  // 后台执行日志由消息级节点折叠栈聚合展示，避免主界面被碎日志撑散。
  if (isWorkflowLog || (isComplete && isBackendProgressText(trimmed))) return null;

  // 将后端中常见的连带输出进行去重处理
  let display = trimmed;
  if (trimmed.includes('查询重写完成')) {
    display = '查询重写完成，已优化查询意图。';
  } else if (trimmed.includes('未找到证据')) {
    display = '知识库未检索到直接匹配条目，将直接依据数据库执行。';
  }

  const isGameQuery = query ? (
    query.includes('游戏') || query.includes('营销') || query.includes('销量') || query.includes('Strategy') || query.includes('SLG') || text.includes('游戏销量') || text.includes('Game_Sales') || text.includes('Strategy_Sales')
  ) : false;

  // 2. 导入数据可折叠 (TaskToolCard 高保真样式)
  if (trimmed.includes('导入数据') || trimmed.includes('成功加载本地') || trimmed.includes('加载数据源')) {
    return (
      <TaskToolCard 
        title="导入数据" 
        summary={isGameQuery ? "数据规模：共 16719 行，16 列，覆盖 11563 种游戏名称，31 种平台，13 种游戏类型。" : "成功加载并读取关联数据源。"}
        defaultOpen={true}
      >
        {isGameQuery ? (
          <DataUnderstanding />
        ) : (
          <div className="text-[12.5px] text-gray-700 space-y-2 leading-relaxed font-mono whitespace-pre-wrap select-all">
            {trimmed}
          </div>
        )}
      </TaskToolCard>
    );
  }

  // 3. 检索知识库可折叠 (TaskToolCard 高保真样式)
  if (trimmed.includes('检索知识库') || trimmed.includes('正在检索知识库') || trimmed.includes('证据召回') || trimmed.includes('知识检索')) {
    return (
      <TaskToolCard 
        title="正在检索知识库" 
        summary={isGameQuery ? "综上，厂商需具备玩法创新（SLG+X）、长线运营（联盟生态）、精准营销（分渠道素材）及口碑管理（评分维护）四大核心能力，方能在深水竞争中胜出。" : "已检索检索库并召回背景信息与证据数据。"}
        defaultOpen={false}
      >
        {isGameQuery ? (
          <div className="text-[12.5px] text-gray-700 space-y-3 leading-[1.625rem]">
            <p>2024–2026年全球策略类游戏市场呈现“精品化+融合化+全球化”特征。2024年该品类全球收入达175亿美元，占手游总收入近10%，稳居首位；2025年海外头部产品数量与流水占比均居第一，但新品突围难度大（仅占6%），长线运营成为竞争核心。</p>
            <p>玩法上，“SLG+X”融合模式成主流，通过模拟经营、三消、RTS等轻量化副玩法降低门槛，吸引泛用户；题材从传统三国奇幻向冰雪末日、文明IP等多元化拓展。</p>
            <p>爆款案例显示，成功关键在于“轻素材引流+重社交留存”。如《Whiteout Survival》以模拟经营素材买量，累计收入超22.5亿美元；《Last War》依托短视频高强度投放。共性策略包括：使用小游戏式高转化率广告素材；分层运营KOL与社区（硬核玩家靠Discord/Reddit，泛用户靠TikTok）；以及“出口转内销”路径，先验证海外再回流国内。</p>
            <p>平台差异显著：移动端贡献90%以上收入，用户25–45岁、高付费、强社交，营销侧重Meta/TikTok买量及Facebook/Discord社群；PC端聚焦核心玩家，依赖媒体评测、Steam愿望单及硬核主播；主机端占比小，侧重叙事型策略，依靠平台推荐及直播曝光。</p>
            <p>评分机制方面，媒体评分显著影响PC/主机首发销量（Metacritic&gt;80分可提升首周销量20–30%），对移动端影响较弱；用户评分直接决定移动端自然下载转化率与留存，App Store/Google Play评分低于4.0将严重阻碍增长，高于4.5则延长生命周期并支撑买量ROI。</p>
          </div>
        ) : (
          <div className="text-[12.5px] text-gray-700 space-y-2 leading-relaxed whitespace-pre-wrap select-all">
            {trimmed}
          </div>
        )}
      </TaskToolCard>
    );
  }

  // 4. 联网搜索可折叠 (TaskToolCard 高保真样式)
  if (trimmed.includes('联网搜索') || trimmed.includes('联网搜索中')) {
    return (
      <TaskToolCard 
        title="联网搜索中" 
        summary={isGameQuery ? "综上，厂商需具备玩法创新（SLG+X）、长线运营（联盟生态）、精准营销（分渠道素材）及口碑管理（评分维护）四大核心能力，方能在深水竞争中胜出。" : "执行实时网络检索以完善专业数据支撑。"}
        defaultOpen={false}
      >
        {isGameQuery ? (
          <div className="text-[12.5px] text-gray-700 space-y-4">
            <div>
              <h4 className="font-bold text-gray-800 mb-1 text-[13px]">搜索方向</h4>
              <p className="text-gray-600">2024-2026年全球策略类游戏（Strategy Games，含SLG/RTS/4X等）的市场趋势、头部爆款案例及其核心营销打法有哪些？不同平台用户画像差异有何不同？媒体评分对策略类游戏销量的影响机制是什么？</p>
            </div>
            <div className="border-t border-gray-100 pt-3">
              <h4 className="font-bold text-gray-800 mb-1 text-[13px]">搜索结果</h4>
              <p className="text-gray-600">2024–2026年全球策略类游戏市场呈现“精品化+融合化+全球化”特征。玩法上，“SLG+X”融合模式成主流，题材从传统三国奇幻向冰雪末日、文明IP等多元化拓展。爆款成功关键在于“轻素材引流+重社交留存”。用户评分直接决定移动端自然下载转化率与留存，高于4.5则延长生命周期并支撑买量ROI。</p>
            </div>
          </div>
        ) : (
          <div className="text-[12.5px] text-gray-700 space-y-2 leading-relaxed whitespace-pre-wrap select-all">
            {trimmed}
          </div>
        )}
      </TaskToolCard>
    );
  }

  // 5. 重写等中间状态提示
  if (trimmed.includes('查询重写') || trimmed.includes('重写后查询') || trimmed.includes('未找到证据')) {
    return (
      <div className="inline-flex items-center gap-1.5 bg-gray-50 border border-gray-150 rounded-lg px-2.5 py-1 text-[11px] text-gray-500 font-semibold my-1 select-none">
        <Sparkles className="size-3 text-indigo-500 animate-pulse" />
        <span>{display}</span>
      </div>
    );
  }

  return (
    <div className="font-normal whitespace-pre-wrap text-[13px] text-gray-750 leading-relaxed my-1">
      {display}
    </div>
  );
};

interface ContentBlock {
  type: 'text' | 'python' | 'sql' | 'markdown-report' | 'result_set' | 'json';
  content: string;
}

const cleanTextBlockContent = (content: string) => {
  return content
    .split('\n')
    .filter((line) => line.trim() !== '$$$')
    .join('\n');
};

function parseRawContent(raw: string): ContentBlock[] {
  const blocks: ContentBlock[] = [];
  let index = 0;

  const markers = [
    { sign: '$$$json', type: 'json' as const },
    { sign: '$$$python', type: 'python' as const },
    { sign: '$$$sql', type: 'sql' as const },
    { sign: '$$$markdown-report', type: 'markdown-report' as const },
    { sign: '$$$result_set', type: 'result_set' as const },
  ];

  const appendTextAndEmbeddedResultSets = (content: string) => {
    const text = cleanTextBlockContent(content);
    if (!text) return;

    const resultSetPayloads = extractEmbeddedResultSetPayloads(text)
      .filter((payload) => parseResultSetData(payload.payload));

    if (resultSetPayloads.length === 0) {
      blocks.push({ type: 'text', content: text });
      return;
    }

    let cursor = 0;
    resultSetPayloads.forEach((payload) => {
      const before = cleanTextBlockContent(text.slice(cursor, payload.start));
      if (before) {
        blocks.push({ type: 'text', content: before });
      }
      blocks.push({ type: 'result_set', content: payload.payload });
      cursor = payload.end;
    });

    const after = cleanTextBlockContent(text.slice(cursor));
    if (after) {
      blocks.push({ type: 'text', content: after });
    }
  };

  while (index < raw.length) {
    let closestMarker: any = null;
    let closestPos = Infinity;

    for (const m of markers) {
      const pos = raw.indexOf(m.sign, index);
      if (pos !== -1 && pos < closestPos) {
        closestPos = pos;
        closestMarker = m;
      }
    }

    if (closestMarker === null) {
      appendTextAndEmbeddedResultSets(raw.substring(index));
      break;
    }

    if (closestPos > index) {
      appendTextAndEmbeddedResultSets(raw.substring(index, closestPos));
    }

    const endSign = closestMarker.type === 'markdown-report' ? '$$$/markdown-report' : '$$$';
    const startOfContent = closestPos + closestMarker.sign.length;
    const endPos = raw.indexOf(endSign, startOfContent);

    if (endPos === -1) {
      const content = raw.substring(startOfContent);
      if (closestMarker.type === 'result_set' && !parseResultSetData(content)) {
        appendTextAndEmbeddedResultSets(content);
      } else {
        blocks.push({ type: closestMarker.type, content: content });
      }
      break;
    }

    const content = raw.substring(startOfContent, endPos);
    if (closestMarker.type === 'result_set' && !parseResultSetData(content)) {
      appendTextAndEmbeddedResultSets(content);
    } else {
      blocks.push({ type: closestMarker.type, content: content });
    }
    const nextMarkerStartsHere = closestMarker.type !== 'markdown-report' && markers.some((marker) => raw.startsWith(marker.sign, endPos));
    index = nextMarkerStartsHere ? endPos : endPos + endSign.length;
  }

  return blocks;
}

const getPlanBlock = (blocks?: ContentBlock[] | MessageBlock[]) => {
  return blocks?.find((block) => block.type === 'json' && block.content.includes('execution_plan'))?.content || '';
};

const getMarkdownReportBlock = (blocks?: ContentBlock[] | MessageBlock[]) => {
  return blocks?.find((block) => block.type === 'markdown-report')?.content.replace('$$$/markdown-report', '').trim() || '';
};

const getLatestMarkdownReportState = (messages: Message[]) => {
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    const reportBlock = messages[i].blocks?.find((block) => block.type === 'markdown-report');
    if (reportBlock) {
      return {
        exists: true,
        content: reportBlock.content.replace('$$$/markdown-report', '').trim(),
      };
    }
  }
  return { exists: false, content: '' };
};

const createSseDataParser = (onData: (content: string) => void, onEvent?: (event: WorkflowEvent) => void) => {
  let dataLines: string[] = [];

  const flush = () => {
    if (dataLines.length === 0) return;
    const { visibleContent, events } = splitWorkflowEvents(dataLines.join('\n'));
    events.forEach(event => onEvent?.(event));
    if (visibleContent.trim()) {
      onData(visibleContent);
    }
    dataLines = [];
  };

  const processLine = (line: string) => {
    const normalized = line.endsWith('\r') ? line.slice(0, -1) : line;
    if (normalized === '') {
      flush();
      return;
    }
    if (!normalized.startsWith('data:')) return;

    const rawData = normalized.slice(5);
    dataLines.push(rawData.startsWith(' ') ? rawData.slice(1) : rawData);
  };

  return { processLine, flush };
};

const getMemoryCandidateKey = (candidateId?: number | string | null) =>
  candidateId === undefined || candidateId === null ? 'missing' : String(candidateId);

const isValidMemoryCandidateId = (candidateId?: number | string | null) => {
  if (candidateId === undefined || candidateId === null || String(candidateId).trim() === '') return false;
  const numericId = Number(candidateId);
  return Number.isFinite(numericId) && numericId > 0;
};

const hasPendingHumanApproval = (blocks?: ContentBlock[] | MessageBlock[]) => {
  const text = blocks
    ?.filter((block) => block.type === 'text')
    .map((block) => block.content)
    .join('\n') || '';
  const hasReviewSignal =
    text.includes('等待人工复核反馈') ||
    text.includes('已开启人工复核') ||
    text.includes('人工反馈节点') ||
    text.includes('人工审核节点');
  return hasReviewSignal && Boolean(getPlanBlock(blocks));
};

interface HumanApprovalPanelProps {
  planJson: string;
  disabled?: boolean;
  approveDisabled?: boolean;
  onApprove: () => void;
  onEdit: (planPreview: string) => void;
}

const HumanApprovalPanel: React.FC<HumanApprovalPanelProps> = ({ planJson, disabled = false, approveDisabled = false, onApprove, onEdit }) => {
  const plan = parsePlanJson(planJson);
  const steps = plan?.execution_plan || [];
  const estimatedMinutes = Math.max(1, Math.ceil(steps.length * 1.8));
  const planPreview = buildHumanFeedbackPlanPreview(planJson);

  return (
    <div className="my-3 w-full max-w-[640px] select-none">
      <div className="overflow-hidden rounded-[10px] border border-gray-200 bg-white">
        <div className="flex min-h-[45px] flex-wrap items-center justify-between gap-3 px-4 py-2">
          <div className="flex min-w-0 flex-wrap items-center gap-2 text-[14px] leading-7 text-[#0A0A0B]">
            <span className="font-normal">计划完成，需确认是否执行</span>
            <span className="rounded-full bg-[#EEEAFE] px-3 py-1 text-[12px] font-medium leading-5 text-[#5B55FF]">
              预计{estimatedMinutes}分钟
            </span>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={disabled}
              onClick={() => onEdit(planPreview)}
              className="inline-flex h-8 min-w-14 items-center justify-center rounded-[10px] border border-gray-200 bg-white px-3 text-[14px] leading-5 font-medium text-gray-700 transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60 cursor-pointer"
            >
              修改
            </button>
            <button
              type="button"
              disabled={approveDisabled}
              onClick={onApprove}
              className="inline-flex h-8 min-w-14 items-center justify-center rounded-[10px] border border-[#151517] bg-[#151517] px-3 text-[14px] leading-5 font-medium text-[#FAFAFA] transition-colors hover:bg-[#202227] hover:border-[#202227] disabled:cursor-not-allowed disabled:border-gray-200 disabled:bg-gray-200 disabled:text-gray-500 cursor-pointer"
            >
              确认
            </button>
          </div>
        </div>
      </div>

      <div className="mt-3 rounded-xl border border-[#B8C5FF] bg-[#F7F8FF] px-5 py-4 text-[14px] leading-7 text-[#53679A] shadow-xs">
        <div className="mb-1 flex items-center gap-2 font-semibold text-[#3158B8]">
          <CircleHelp className="size-4" />
          <span>提示信息</span>
        </div>
        <p className="m-0">
          系统已生成执行计划，请确认统计口径、数据范围和输出方式是否符合预期。确认后会继续执行；如果需要调整，请点击修改并填写意见。
        </p>
      </div>
    </div>
  );
};



const hasRenderableAssistantPayload = (message?: Message) => {
  if (!message || message.role !== 'assistant') {
    return false;
  }
  return Boolean(
    (message.content || '').trim() ||
    (message.blocks?.length ?? 0) > 0 ||
    (message.workflowEvents?.length ?? 0) > 0
  );
};

// ================= 主视图组件 Home =================
const Home: React.FC = () => {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const {
    activeSessionTitle,
    isSidebarCollapsed,
    isSidebarVisible,
    openSidebarPreview,
    queueSidebarPreviewClose,
    collapseSidebar,
    expandSidebar,
  } = useOutletContext<LayoutOutletContext>();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const composerTextareaRef = useRef<HTMLTextAreaElement>(null);
  const chatScrollRef = useRef<HTMLDivElement>(null);
  const chatBottomRef = useRef<HTMLDivElement>(null);
  const activeStreamAbortRef = useRef<AbortController | null>(null);
  
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);
  const [showSkillBanner, setShowSkillBanner] = useState(true);
  const [attachedFiles, setAttachedFiles] = useState<any[]>([]);
  const [fullscreenPreviewFile, setFullscreenPreviewFile] = useState<any | null>(null);
  const [selectedKbs, setSelectedKbs] = useState<string[]>([]);
  const [kbSearchQuery, setKbSearchQuery] = useState('');
  const [selectedMcps, setSelectedMcps] = useState<string[]>([]);
  const [mcpSearchQuery, setMcpSearchQuery] = useState('');
  const [feedbackSubmitting, setFeedbackSubmitting] = useState(false);
  const [memoryCandidateActions, setMemoryCandidateActions] = useState<Record<string, MemoryCandidateActionStatus>>({});
  const [interruptedRun, setInterruptedRun] = useState<WorkflowRunState | null>(null);
  const [activeHumanFeedbackPlanPreview, setActiveHumanFeedbackPlanPreview] = useState('');
  
  const agentId = useCurrentAgentStore((state) => state.agentId);
  const agentName = useCurrentAgentStore((state) => state.agentName);
  const setCurrentAgent = useCurrentAgentStore((state) => state.setCurrentAgent);
  const effectiveAgentId = agentId && agentId !== 'default' ? agentId : '1';
  const [isAgentSwitcherOpen, setIsAgentSwitcherOpen] = useState(false);
  const [agentSearchQuery, setAgentSearchQuery] = useState('');
  const [agentOptions, setAgentOptions] = useState<ChatAgentOption[]>([DEFAULT_CHAT_AGENT_OPTION]);

  const hasPendingHumanReviewNotice = useMemo(() => {
    return messages.some((msg, idx) => {
      if (msg.role !== 'assistant' || !hasRenderableAssistantPayload(msg)) {
        return false;
      }

      const feedbackAlreadyHandled = messages.slice(idx + 1).some((nextMsg) =>
        nextMsg.role === 'assistant' &&
        nextMsg.blocks?.some((block) => block.type === 'text' && block.content.includes('人工审核已'))
      );

      return hasPendingHumanApproval(msg.blocks) && !feedbackAlreadyHandled;
    });
  }, [messages]);

  const handleStartHumanFeedbackEdit = (planPreview: string) => {
    setActiveHumanFeedbackPlanPreview(planPreview);
    setInputValue('');
    window.setTimeout(() => composerTextareaRef.current?.focus(), 0);
  };

  const handleCancelHumanFeedbackEdit = () => {
    setActiveHumanFeedbackPlanPreview('');
    setInputValue('');
  };

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const queryAgentId = params.get('agentId');
    if (queryAgentId && queryAgentId !== 'default') {
      setCurrentAgent({ agentId: queryAgentId, agentName: '自定义智能体' });
      fetch(`/api/agent/${queryAgentId}`)
        .then(res => res.json())
        .then(data => {
          if (data.success && data.data) {
            setCurrentAgent({
              agentId: queryAgentId,
              agentName: data.data.name || '自定义智能体',
            });
          }
        })
        .catch(() => setCurrentAgent({ agentId: queryAgentId, agentName: '自定义智能体' }));

      params.delete('agentId');
      const nextSearch = params.toString();
      navigate(
        {
          pathname: location.pathname,
          search: nextSearch ? `?${nextSearch}` : '',
          hash: location.hash,
        },
        { replace: true, state: location.state }
      );
    } else if (!agentId || agentId === 'default') {
      // 异步获取第一个已配置智能体，避免使用 'default' 导致接口 500/报错
      fetch('/api/agent/list')
        .then(res => res.json())
        .then(data => {
          if (data.success && Array.isArray(data.data) && data.data.length > 0) {
            const firstAgent = data.data[0];
            setCurrentAgent({
              agentId: firstAgent.id.toString(),
              agentName: firstAgent.name || 'Data Agent',
            });
          } else {
            setCurrentAgent({ agentId: '1', agentName: 'Data Agent' });
          }
        })
        .catch(() => {
          setCurrentAgent({ agentId: '1', agentName: 'Data Agent' });
        });
    }
  }, [agentId, location.hash, location.pathname, location.search, location.state, navigate, setCurrentAgent]);

  useEffect(() => {
    if (!isAgentSwitcherOpen) return;

    fetch('/api/agent/list')
      .then(res => res.json())
      .then(data => {
        if (data.success && Array.isArray(data.data)) {
          const options = data.data.map((agent: any) => ({
            id: String(agent.id),
            name: agent.name || '未命名Agent',
            description: agent.description || '暂无描述信息',
          }));
          setAgentOptions(options.length > 0 ? options : [DEFAULT_CHAT_AGENT_OPTION]);
        }
      })
      .catch(error => {
        console.error('加载智能体下拉列表失败', error);
      });
  }, [isAgentSwitcherOpen]);

  const selectedAgentOption = useMemo(() => {
    return agentOptions.find(agent => agent.id === effectiveAgentId) || {
      id: effectiveAgentId,
      name: agentName,
      description: agentName === DEFAULT_CHAT_AGENT_OPTION.name ? DEFAULT_CHAT_AGENT_OPTION.description : '暂无描述信息',
    };
  }, [agentName, agentOptions, effectiveAgentId]);

  const filteredAgentOptions = useMemo(() => {
    const keyword = agentSearchQuery.trim().toLowerCase();
    if (!keyword) {
      return agentOptions;
    }
    return agentOptions.filter(agent =>
      agent.name.toLowerCase().includes(keyword) ||
      agent.description.toLowerCase().includes(keyword)
    );
  }, [agentOptions, agentSearchQuery]);

  const handleSelectAgent = (agent: ChatAgentOption) => {
    setCurrentAgent({ agentId: agent.id, agentName: agent.name });
    setIsAgentSwitcherOpen(false);
    navigate('/chat');
  };

  // 后端模式：仅 NL2SQL 或人工审核
  const [chatMode, setChatMode] = useState<ChatMode>(null);
  const [isReportOpen, setIsReportOpen] = useState(false);
  const [reportContent, setReportContent] = useState('');
  const [isReportManuallyCollapsed, setIsReportManuallyCollapsed] = useState(false);
  const [reportPanelWidth, setReportPanelWidth] = useState(REPORT_PANEL_DEFAULT_WIDTH);
  const [isReportResizing, setIsReportResizing] = useState(false);

  // 指定库表进行分析的弹窗状态
  const [isSelectDataOpen, setIsSelectDataOpen] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<'file' | 'db'>('file');
  const [modalSearchQuery, setModalSearchQuery] = useState('');
  const [selectedFilesInModal, setSelectedFilesInModal] = useState<any[]>([]);

  // 路由挂载的 Chat 状态检查
  const isChatState = !!sessionId;

  useEffect(() => {
    const latestReport = getLatestMarkdownReportState(messages);
    if (!latestReport.exists) {
      setReportContent('');
      setIsReportOpen(false);
      setIsReportManuallyCollapsed(false);
      return;
    }
    if (latestReport.content !== reportContent) {
      setReportContent(latestReport.content);
    }
    const nextState = getNextReportPanelState({
      hasReport: latestReport.exists,
      previousContent: reportContent,
      nextContent: latestReport.content,
      isManuallyCollapsed: isReportManuallyCollapsed,
    });
    setIsReportOpen(nextState.isOpen);
    setIsReportManuallyCollapsed(nextState.isManuallyCollapsed);
  }, [messages, reportContent, isReportManuallyCollapsed]);

  useEffect(() => {
    if (!isChatState || !chatScrollRef.current) return;

    const rafId = window.requestAnimationFrame(() => {
      chatBottomRef.current?.scrollIntoView({ block: 'end' });
      chatScrollRef.current!.scrollTop = chatScrollRef.current!.scrollHeight;
    });

    return () => window.cancelAnimationFrame(rafId);
  }, [isChatState, isReportOpen, messages]);

  const handleCollapseReport = () => {
    setIsReportOpen(false);
    setIsReportManuallyCollapsed(true);
  };

  const handleExpandReport = () => {
    setIsReportOpen(true);
    setIsReportManuallyCollapsed(false);
  };

  const handleReportPanelWidthChange = (nextWidth: number) => {
    setReportPanelWidth(clampReportPanelWidth(nextWidth));
  };

  const beginStreamRequest = () => {
    const abortController = new AbortController();
    activeStreamAbortRef.current = abortController;
    setIsGenerating(true);
    return abortController;
  };

  const finishStreamRequest = (abortController: AbortController) => {
    if (activeStreamAbortRef.current === abortController) {
      activeStreamAbortRef.current = null;
      setIsGenerating(false);
    }
  };

  const isAbortError = (error: unknown) => {
    return error instanceof Error && error.name === 'AbortError';
  };

  const markLastAssistantComplete = () => {
    setMessages(prev => {
      const nextMessages = [...prev];
      const lastIdx = nextMessages.length - 1;
      if (lastIdx >= 0 && nextMessages[lastIdx].role === 'assistant') {
        nextMessages[lastIdx] = {
          ...nextMessages[lastIdx],
          isComplete: true,
        };
      }
      return nextMessages;
    });
  };

  const handleStopGenerating = () => {
    activeStreamAbortRef.current?.abort();
    activeStreamAbortRef.current = null;
    setIsGenerating(false);
    setIsTyping(false);
    setFeedbackSubmitting(false);
    markLastAssistantComplete();
  };

  // 挂载时如果 state 含有文件，自动关联
  useEffect(() => {
    const state = location.state as any;
    if (state && state.analyzeFile) {
      const files = Array.isArray(state.analyzeFile) ? state.analyzeFile : [state.analyzeFile];
      setAttachedFiles(files);
      setInputValue(state.initialQuery || '');
      window.history.replaceState(null, '');
    }
  }, [location]);

  // 监听 Esc 按键退出全屏预览
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setFullscreenPreviewFile(null);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // 新建会话标识，用于防止路由变更时加载空消息覆盖发送中的流式状态
  const isCreatingSessionRef = useRef(false);

  // 监听 sessionId 变化以加载会话历史消息
  useEffect(() => {
    // 如果是由发送首条消息触发的会话创建，则跳过消息重新拉取，防止覆盖流式对话状态
    if (isCreatingSessionRef.current) {
      isCreatingSessionRef.current = false;
      return;
    }

    if (!sessionId || sessionId.startsWith('session_')) {
      // 临时会话或者没有 sessionId 时，清空消息
      setReportContent('');
      setIsReportOpen(false);
      setIsReportManuallyCollapsed(false);
      setMessages([]);
      setInterruptedRun(null);
      setActiveHumanFeedbackPlanPreview('');
      return;
    }

    // 调用后端接口拉取历史消息
    fetch(`/api/sessions/${sessionId}/messages`)
      .then(res => res.json())
      .then(data => {
        if (data.success && Array.isArray(data.data)) {
          const loadedMessages = data.data.map((msg: any) => {
            if (msg.role === 'user') {
              return {
                role: 'user',
                content: msg.content,
              };
            } else {
              const { visibleContent, events } = splitWorkflowEvents(msg.content || '');
              return {
                role: 'assistant',
                content: '',
                blocks: parseRawContent(visibleContent),
                workflowEvents: events,
                isComplete: true,
              };
            }
          });
          setMessages(loadedMessages);
        } else {
          setMessages([]);
        }
      })
      .catch(err => {
        console.error("加载历史消息失败", err);
        setMessages([]);
      });
    fetch(`/api/sessions/${sessionId}/workflow-run`)
      .then(res => res.json())
      .then(data => {
        const run = data.success ? data.data : null;
        setInterruptedRun(run?.resumable ? run : null);
      })
      .catch(err => {
        console.error("加载工作流运行状态失败", err);
        setInterruptedRun(null);
      });
  }, [sessionId]);

  const demos = [
    { type: '企业', title: '餐厅销售分析', tagColor: 'bg-blue-50 text-blue-600', img: 'https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/card-1.svg', prompt: '帮我做一份餐厅销售情况 analysis' },
    { type: '游戏', title: '发行游戏分析', tagColor: 'bg-indigo-50 text-indigo-600', img: 'https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/card-2.svg', prompt: '请帮我结合这份游戏数据，洞察并产出策略类游戏的营销策略' },
    { type: '电商', title: '电商分析', tagColor: 'bg-purple-50 text-purple-600', img: 'https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/card-3.svg', prompt: '请帮我生成一份本季度的电商运营分析' },
    { type: '金融', title: '信用卡反欺诈分析', tagColor: 'bg-blue-50 text-blue-600', img: 'https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/card-4.svg', prompt: '帮我分析信用卡的异常交易和欺诈风险' },
    { type: '教育', title: '学生成绩分析', tagColor: 'bg-green-50 text-green-600', img: 'https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/card-5.svg', prompt: '请帮我分析本学期期末考试的学生成绩分布' },
    { type: '企业', title: '员工薪资分析', tagColor: 'bg-indigo-50 text-indigo-600', img: 'https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/card-6.svg', prompt: '帮我看一下去年员工的薪资水平 and 分布' },
  ];

  // ================= 核心流式发送逻辑 =================

  const appendWorkflowEvent = (event: WorkflowEvent) => {
    setMessages(prev => {
      const nextMessages = [...prev];
      const lastIdx = nextMessages.length - 1;
      if (lastIdx < 0 || nextMessages[lastIdx].role !== 'assistant') {
        return nextMessages;
      }
      nextMessages[lastIdx] = {
        ...nextMessages[lastIdx],
        workflowEvents: [...(nextMessages[lastIdx].workflowEvents || []), event],
      };
      return nextMessages;
    });
  };

  const handleSend = async (text: string) => {
    if (isGenerating) {
      handleStopGenerating();
      return;
    }

    if (!text.trim()) return;
    const abortController = beginStreamRequest();

    if (activeHumanFeedbackPlanPreview) {
      const userMsg: Message = {
        role: 'user',
        content: text,
        humanFeedbackPlanPreview: activeHumanFeedbackPlanPreview,
      };
      setMessages(prev => [...prev, userMsg]);
      setActiveHumanFeedbackPlanPreview('');
      setInputValue('');
      finishStreamRequest(abortController);
      await handleHumanFeedback(false, text.trim());
      return;
    }

    // 先立即更新界面，提升用户体验 (UX)
    const userMsg: Message = { role: 'user', content: text };
    setMessages(prev => [...prev, userMsg]);
    setInputValue('');
    setAttachedFiles([]);
    setSelectedKbs([]);
    setSelectedMcps([]);
    setIsTyping(true);
    setReportContent('');
    setIsReportOpen(false);
    setIsReportManuallyCollapsed(false);

    // 预填空白 AI message，让解析器在后续直接更新此 message 的 blocks
    const assistantMsg: Message = { 
      role: 'assistant', 
      blocks: [], 
      isComplete: false 
    };
    setMessages(prev => [...prev, assistantMsg]);

    let currentSessionId = sessionId;
    // 如果没有 sessionId，或者 sessionId 是临时生成的 'session_...' 格式，则先去后端创建一个真实会话
    if (!currentSessionId || currentSessionId.startsWith('session_')) {
      isCreatingSessionRef.current = true;
      try {
        const createRes = await fetch(`/api/agent/${effectiveAgentId}/sessions`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ title: text.slice(0, 30) })
        }).then(res => res.json());

        if (createRes.success && createRes.data) {
          currentSessionId = createRes.data.id;
          // 用 replace: true 更新路由，避免在历史记录中留下 /chat 路由
          navigate(`/chat/${currentSessionId}`, { replace: true });
        } else {
          // 备用方案，防止后端接口挂了导致无法聊天
          currentSessionId = 'session_' + Date.now();
          navigate(`/chat/${currentSessionId}`, { replace: true });
        }
      } catch (error) {
        console.error("创建会话失败，使用临时会话ID", error);
        currentSessionId = 'session_' + Date.now();
        navigate(`/chat/${currentSessionId}`, { replace: true });
      }
    }

    let accumulatedRaw = '';

    const updateBlocks = (rawStr: string) => {
      const parsedBlocks = parseRawContent(rawStr);
      setMessages(prev => {
        const nextMessages = [...prev];
        const lastIdx = nextMessages.length - 1;
        nextMessages[lastIdx] = {
          ...nextMessages[lastIdx],
          blocks: parsedBlocks
        };
        return nextMessages;
      });
    };

    const markStreamComplete = () => {
      setIsTyping(false);
      setMessages(prev => {
        const nextMessages = [...prev];
        const lastIdx = nextMessages.length - 1;
        nextMessages[lastIdx] = {
          ...nextMessages[lastIdx],
          isComplete: true
        };
        return nextMessages;
      });
    };

    try {
      const response = await fetch('/api/v1/graph/chat', {
        method: 'POST',
        signal: abortController.signal,
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          agentId: effectiveAgentId,
          threadId: currentSessionId,
          query: text,
          humanFeedback: chatMode === 'humanReview',
          nl2sqlOnly: chatMode === 'nl2sqlOnly',
        }),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      if (!response.body) {
        throw new Error("No response body");
      }

      setIsTyping(false);
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let streamBuffer = '';
      const sseParser = createSseDataParser((content) => {
        accumulatedRaw += content;
        updateBlocks(accumulatedRaw);
      }, appendWorkflowEvent);

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        const rawText = decoder.decode(value, { stream: true });
        streamBuffer += rawText;

        const lines = streamBuffer.split('\n');
        streamBuffer = lines.pop() || '';

        for (const line of lines) {
          sseParser.processLine(line);
        }
      }
      if (streamBuffer) {
        sseParser.processLine(streamBuffer);
      }
      sseParser.flush();
      markStreamComplete();

    } catch (e) {
      if (isAbortError(e)) {
        markStreamComplete();
        return;
      }
      console.error("对话流请求失败", e);
      setReportContent('');
      setIsReportOpen(false);
      accumulatedRaw = `系统繁忙，请稍后再试（${e instanceof Error ? e.message : '对话请求失败'}）`;
      updateBlocks(accumulatedRaw);
      markStreamComplete();
    } finally {
      finishStreamRequest(abortController);
    }
  };

  const handleHumanFeedback = async (approved: boolean, feedbackContent?: string) => {
    const currentSessionId = sessionId;
    if (!currentSessionId || currentSessionId.startsWith('session_') || feedbackSubmitting) return;
    const abortController = beginStreamRequest();

    setFeedbackSubmitting(true);
    setIsTyping(true);
    setReportContent('');
    setIsReportOpen(false);
    setIsReportManuallyCollapsed(false);
    setMessages(prev => [
      ...prev,
      ...(approved ? [{
        role: 'user' as const,
        content: '开始任务',
      }] : []),
      {
        role: 'assistant',
        blocks: [],
        isComplete: false,
      }
    ]);

    let accumulatedRaw = '';

    const updateBlocks = (rawStr: string) => {
      const parsedBlocks = parseRawContent(rawStr);
      setMessages(prev => {
        const nextMessages = [...prev];
        const lastIdx = nextMessages.length - 1;
        nextMessages[lastIdx] = {
          ...nextMessages[lastIdx],
          blocks: parsedBlocks
        };
        return nextMessages;
      });
    };

    const markStreamComplete = () => {
      setIsTyping(false);
      setMessages(prev => {
        const nextMessages = [...prev];
        const lastIdx = nextMessages.length - 1;
        nextMessages[lastIdx] = {
          ...nextMessages[lastIdx],
          isComplete: true
        };
        return nextMessages;
      });
    };

    try {
      const response = await fetch('/api/v1/graph/chat', {
        method: 'POST',
        signal: abortController.signal,
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          agentId: effectiveAgentId,
          threadId: currentSessionId,
          query: '',
          interactionType: 'HUMAN_PLAN_FEEDBACK',
          humanFeedbackContent: approved ? '确认执行' : (feedbackContent || '请根据人工意见修改执行计划'),
          rejectedPlan: !approved,
          nl2sqlOnly: false,
        }),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      if (!response.body) {
        throw new Error('No response body');
      }

      setIsTyping(false);
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let streamBuffer = '';
      const sseParser = createSseDataParser((content) => {
        accumulatedRaw += content;
        updateBlocks(accumulatedRaw);
      }, appendWorkflowEvent);

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        const rawText = decoder.decode(value, { stream: true });
        streamBuffer += rawText;

        const lines = streamBuffer.split('\n');
        streamBuffer = lines.pop() || '';

        for (const line of lines) {
          sseParser.processLine(line);
        }
      }
      if (streamBuffer) {
        sseParser.processLine(streamBuffer);
      }
      sseParser.flush();

      markStreamComplete();
    } catch (error) {
      if (isAbortError(error)) {
        markStreamComplete();
        return;
      }
      accumulatedRaw += `系统繁忙，请稍后再试（${error instanceof Error ? error.message : '人工反馈提交失败'}）`;
      updateBlocks(accumulatedRaw);
      markStreamComplete();
    } finally {
      finishStreamRequest(abortController);
      setFeedbackSubmitting(false);
    }
  };

  const handleContinueAnalysis = async () => {
    const currentSessionId = sessionId;
    if (!currentSessionId || currentSessionId.startsWith('session_') || feedbackSubmitting) return;
    const abortController = beginStreamRequest();

    setInterruptedRun(null);
    setFeedbackSubmitting(true);
    setIsTyping(true);
    setMessages(prev => [
      ...prev,
      { role: 'assistant', blocks: [], isComplete: false, workflowEvents: [] },
    ]);

    let accumulatedRaw = '';
    const updateBlocks = (rawStr: string) => {
      const parsedBlocks = parseRawContent(rawStr);
      setMessages(prev => {
        const nextMessages = [...prev];
        const lastIdx = nextMessages.length - 1;
        if (lastIdx >= 0 && nextMessages[lastIdx].role === 'assistant') {
          nextMessages[lastIdx] = {
            ...nextMessages[lastIdx],
            blocks: parsedBlocks,
          };
        }
        return nextMessages;
      });
    };

    try {
      const response = await fetch('/api/v1/graph/chat', {
        method: 'POST',
        signal: abortController.signal,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          agentId: effectiveAgentId,
          threadId: currentSessionId,
          query: '',
          interactionType: 'CONTINUE_ANALYSIS',
          nl2sqlOnly: false,
        }),
      });
      if (!response.ok || !response.body) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      setIsTyping(false);
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let streamBuffer = '';
      const sseParser = createSseDataParser((chunk) => {
        accumulatedRaw += chunk;
        updateBlocks(accumulatedRaw);
      }, appendWorkflowEvent);

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        streamBuffer += decoder.decode(value, { stream: true });
        const lines = streamBuffer.split('\n');
        streamBuffer = lines.pop() || '';
        for (const line of lines) {
          sseParser.processLine(line);
        }
      }
      if (streamBuffer) {
        sseParser.processLine(streamBuffer);
      }
      sseParser.flush();
    } catch (error) {
      if (isAbortError(error)) {
        return;
      }
      accumulatedRaw += `系统繁忙，请稍后再试：${error instanceof Error ? error.message : '继续分析失败'}`;
      updateBlocks(accumulatedRaw);
    } finally {
      finishStreamRequest(abortController);
      setMessages(prev => {
        const nextMessages = [...prev];
        const lastIdx = nextMessages.length - 1;
        if (lastIdx >= 0 && nextMessages[lastIdx].role === 'assistant') {
          nextMessages[lastIdx] = { ...nextMessages[lastIdx], isComplete: true };
        }
        return nextMessages;
      });
      setFeedbackSubmitting(false);
      setIsTyping(false);
    }
  };

  const handleClarificationInteraction = async (
    interactionType: 'CLARIFICATION_ANSWER' | 'CLARIFICATION_CONFIRM',
    content: string
  ) => {
    const currentSessionId = sessionId;
    if (!currentSessionId || currentSessionId.startsWith('session_') || feedbackSubmitting) return;
    const abortController = beginStreamRequest();

    setFeedbackSubmitting(true);
    setIsTyping(true);
    setMessages(prev => [
      ...prev,
      { role: 'user', content },
      { role: 'assistant', blocks: [], isComplete: false, workflowEvents: [] },
    ]);

    let accumulatedRaw = '';
    const updateBlocks = (rawStr: string) => {
      const parsedBlocks = parseRawContent(rawStr);
      setMessages(prev => {
        const nextMessages = [...prev];
        const lastIdx = nextMessages.length - 1;
        nextMessages[lastIdx] = {
          ...nextMessages[lastIdx],
          blocks: parsedBlocks,
        };
        return nextMessages;
      });
    };

    try {
      const response = await fetch('/api/v1/graph/chat', {
        method: 'POST',
        signal: abortController.signal,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          agentId: effectiveAgentId,
          threadId: currentSessionId,
          query: '',
          interactionType,
          interactionContent: content,
          nl2sqlOnly: false,
        }),
      });
      if (!response.ok || !response.body) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      setIsTyping(false);
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let streamBuffer = '';
      const sseParser = createSseDataParser((chunk) => {
        accumulatedRaw += chunk;
        updateBlocks(accumulatedRaw);
      }, appendWorkflowEvent);

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        streamBuffer += decoder.decode(value, { stream: true });
        const lines = streamBuffer.split('\n');
        streamBuffer = lines.pop() || '';
        for (const line of lines) {
          sseParser.processLine(line);
        }
      }
      if (streamBuffer) {
        sseParser.processLine(streamBuffer);
      }
      sseParser.flush();
    } catch (error) {
      if (isAbortError(error)) {
        return;
      }
      accumulatedRaw += `系统繁忙，请稍后再试：${error instanceof Error ? error.message : '澄清提交失败'}`;
      updateBlocks(accumulatedRaw);
    } finally {
      finishStreamRequest(abortController);
      setMessages(prev => {
        const nextMessages = [...prev];
        const lastIdx = nextMessages.length - 1;
        if (lastIdx >= 0 && nextMessages[lastIdx].role === 'assistant') {
          nextMessages[lastIdx] = { ...nextMessages[lastIdx], isComplete: true };
        }
        return nextMessages;
      });
      setFeedbackSubmitting(false);
      setIsTyping(false);
    }
  };

  const handleMemoryCandidateAction = async (
    candidateId: number | string | undefined,
    action: 'submit' | 'publish' | 'ignore'
  ) => {
    const actionKey = getMemoryCandidateKey(candidateId);
    if (action === 'ignore') {
      setMemoryCandidateActions(prev => ({ ...prev, [actionKey]: 'ignored' }));
      return;
    }

    if (!isValidMemoryCandidateId(candidateId)) {
      setMemoryCandidateActions(prev => ({ ...prev, [actionKey]: 'error' }));
      return;
    }

    const url = action === 'submit'
      ? `/api/v1/knowledge-candidates/${candidateId}/submit`
      : `/api/v1/knowledge-candidates/${candidateId}/publish`;

    setMemoryCandidateActions(prev => ({ ...prev, [actionKey]: 'pending' }));
    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: action === 'publish' ? JSON.stringify({ targetType: 'BUSINESS_KNOWLEDGE' }) : undefined,
      });
      const result = await response.json().catch(() => null);
      if (!response.ok || result?.success === false) {
        throw new Error(result?.message || `HTTP error! status: ${response.status}`);
      }
      setMemoryCandidateActions(prev => ({
        ...prev,
        [actionKey]: action === 'submit' ? 'submitted' : 'published',
      }));
    } catch (error) {
      console.error('候选知识操作失败', error);
      setMemoryCandidateActions(prev => ({ ...prev, [actionKey]: 'error' }));
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const newFile = {
        id: `upload-${Date.now()}`,
        name: file.name,
        size: file.size > 1024 * 1024 
          ? `${(file.size / (1024 * 1024)).toFixed(1)} MB` 
          : `${(file.size / 1024).toFixed(1)} KB`
      };
      setAttachedFiles(prev => [...prev, newFile]);
      setInputValue(prev => prev || `请帮我结合这份游戏数据，洞察并产出策略类游戏的营销策略`);
      setIsSelectDataOpen(false);
    }
  };

  const filteredFiles = INITIAL_FILES.filter(file => 
    file.name.toLowerCase().includes(modalSearchQuery.toLowerCase())
  );

  return (
    <div 
      className={clsx(
        "relative flex flex-col items-center max-[680px]:items-start h-full w-full bg-[#F6F6F6] select-none",
        isChatState ? "overflow-x-hidden overflow-y-auto" : "overflow-hidden"
      )}
    >
      {/* 顶部导航栏 (landing-top-bar) */}
      <div data-animate="landing-top-bar" className="flex w-full items-center sticky top-0 z-30 flex-none h-12 px-3 bg-[#F6F6F6]/90 backdrop-blur-md border-b border-gray-100">
        <span data-testid="agent-selector" className="ml-2 flex-grow">
          {isChatState ? (
            <div className="flex h-7 items-center justify-between">
              {isSidebarCollapsed ? (
                <div className="flex min-w-0 items-center">
                  <button
                    type="button"
                    onMouseEnter={openSidebarPreview}
                    onMouseLeave={queueSidebarPreviewClose}
                    onFocus={openSidebarPreview}
                    onBlur={queueSidebarPreviewClose}
                    onClick={expandSidebar}
                    className={clsx(
                      'group inline-flex size-9 items-center justify-center rounded-xl border p-0 text-[#0A0A0B] transition-colors',
                      isSidebarVisible
                        ? 'border-gray-200 bg-white text-gray-900 shadow-sm'
                        : 'border-transparent bg-transparent hover:bg-gray-200/40'
                    )}
                    aria-label="展开边栏"
                  >
                    <span className="relative size-[18px]">
                      <Menu className={clsx(
                        'absolute inset-0 size-[18px] transition-opacity duration-150',
                        isSidebarVisible ? 'opacity-0' : 'opacity-100 group-hover:opacity-0 group-focus-visible:opacity-0'
                      )} />
                      <ChevronsRight className={clsx(
                        'absolute inset-0 size-[18px] transition-opacity duration-150',
                        isSidebarVisible ? 'opacity-100' : 'opacity-0 group-hover:opacity-100 group-focus-visible:opacity-100'
                      )} />
                    </span>
                  </button>
                  <span className="ml-3 truncate text-[14px] font-normal leading-[21px] text-[#0A0A0B]">
                    {activeSessionTitle}
                  </span>
                </div>
              ) : (
                <>
                  <span className="truncate text-[14px] font-normal leading-[21px] text-[#0A0A0B]">
                    {activeSessionTitle}
                  </span>
                  <button
                    type="button"
                    onClick={collapseSidebar}
                    className="inline-flex size-7 items-center justify-center rounded-md border-0 bg-transparent p-0 text-[#0A0A0B] transition-colors hover:bg-gray-200/40"
                    aria-label="收起边栏"
                  >
                    <ChevronsLeft className="size-[18px]" />
                  </button>
                </>
              )}
            </div>
          ) : (
            <DropdownMenu.Root
              open={isAgentSwitcherOpen}
              onOpenChange={(open) => {
                setIsAgentSwitcherOpen(open);
                if (open) {
                  setAgentSearchQuery('');
                }
              }}
            >
              <DropdownMenu.Trigger asChild>
                <button className="gap-2 whitespace-nowrap rounded-md py-2 justify-between h-7 w-auto border-0 bg-transparent px-2 text-sm font-normal text-gray-700 hover:bg-gray-200/50 flex items-center overflow-hidden" type="button">
                  <div className="flex flex-1 items-center gap-1 truncate text-gray-800">
                    <Atom className="w-4 h-4 text-gray-500" />
                    <span className="flex-1 truncate font-medium">{selectedAgentOption.name}</span>
                  </div>
                  <ChevronDown className={clsx('w-4 h-4 text-zinc-400 flex-none ml-1 transition-transform duration-150', isAgentSwitcherOpen && 'rotate-180')} />
                </button>
              </DropdownMenu.Trigger>

              <DropdownMenu.Portal>
                <DropdownMenu.Content
                  align="start"
                  sideOffset={8}
                  className="z-50 w-[400px] overflow-hidden rounded-lg border border-gray-200 bg-white p-0 font-sans shadow-[0_4px_6px_-1px_rgba(0,0,0,0.10),0_2px_4px_-2px_rgba(0,0,0,0.10)] outline-none animate-in fade-in zoom-in-95 duration-150"
                >
                  <div className="flex h-10 items-center gap-2 border-b border-gray-100 px-3">
                    <Search className="size-4 shrink-0 text-gray-500" />
                    <input
                      value={agentSearchQuery}
                      onChange={(event) => setAgentSearchQuery(event.target.value)}
                      onKeyDown={(event) => event.stopPropagation()}
                      placeholder="搜索..."
                      className="h-full min-w-0 flex-1 border-0 bg-transparent p-0 text-[14px] font-normal leading-[21px] text-[#0A0A0B] outline-none placeholder:text-gray-400"
                    />
                  </div>

                  <div className="max-h-[280px] overflow-y-auto p-1">
                    {filteredAgentOptions.map((agent) => {
                      const isSelected = agent.id === effectiveAgentId;
                      return (
                        <DropdownMenu.Item
                          key={agent.id}
                          onSelect={(event) => {
                            event.preventDefault();
                            handleSelectAgent(agent);
                          }}
                          className={clsx(
                            'flex h-[50px] cursor-pointer items-center justify-between rounded-md px-2 outline-none transition-colors',
                            isSelected ? 'bg-[#F3F3F5]' : 'hover:bg-[#F7F7F8] focus:bg-[#F7F7F8]'
                          )}
                        >
                          <div className="min-w-0 flex-1">
                            <div className="truncate text-[14px] font-normal leading-[21px] text-[#0A0A0B]">
                              {agent.name}
                            </div>
                            <div className="truncate text-[14px] font-normal leading-[21px] text-[#7B7F87]">
                              {agent.description}
                            </div>
                          </div>
                          {isSelected && <Check className="ml-3 size-4 shrink-0 text-black" />}
                        </DropdownMenu.Item>
                      );
                    })}
                  </div>

                  <DropdownMenu.Item
                    onSelect={() => {
                      setIsAgentSwitcherOpen(false);
                      navigate('/agent/create');
                    }}
                    className="flex h-9 cursor-pointer items-center gap-3 border-t border-gray-100 px-4 text-[14px] font-normal leading-[21px] text-[#0A0A0B] outline-none transition-colors hover:bg-[#F7F7F8] focus:bg-[#F7F7F8]"
                  >
                    <Plus className="size-4 text-[#0A0A0B]" />
                    <span>创建自定义Agent</span>
                  </DropdownMenu.Item>
                </DropdownMenu.Content>
              </DropdownMenu.Portal>
            </DropdownMenu.Root>
          )}
        </span>
        
        {/* 清除/新对话按钮 (仅在对话状态下显示) */}
        {isChatState && (
          <button 
            onClick={() => {
              setReportContent('');
              setIsReportOpen(false);
              setIsReportManuallyCollapsed(false);
              setMessages([]);
              navigate('/chat');
            }}
            className="text-xs text-gray-500 hover:text-gray-900 bg-white border border-gray-200 px-2.5 py-1 rounded-md shadow-sm mr-4 transition-colors font-medium cursor-pointer"
          >
            开启新对话
          </button>
        )}
      </div>
      
      {/* 主视口大包裹器，配置开启报告时的左右平滑分栏 */}
      <div className="flex flex-row w-full h-full relative overflow-hidden flex-1">
        <div
          className={clsx(
            "relative flex h-full min-w-0 flex-col items-center overflow-hidden",
            isReportResizing ? "transition-none" : "transition-[width] duration-300 ease-out"
          )}
          style={{ width: isChatState && isReportOpen ? `calc(100% - ${reportPanelWidth}px)` : '100%' }}
        >
          <div
            ref={chatScrollRef}
            className={clsx(
              "flex flex-col h-full w-full items-center overflow-y-auto relative min-w-0",
              isChatState ? "pb-36" : ""
            )}
          >
          
          {/* 🚀 消息流渲染 */}
          {isChatState ? (
            <div className="w-full max-w-[800px] flex flex-col gap-6 pt-10 pb-16 px-1 flex-1">
              {messages.map((msg, idx) => {
                if (msg.role === 'user') {
                  if (msg.humanFeedbackPlanPreview) {
                    return (
                      <div
                        key={idx}
                        className="group grid w-full auto-rows-auto grid-cols-[minmax(72px,1fr)_auto] gap-y-2 py-4 animate-in fade-in slide-in-from-bottom-2 duration-300 select-text"
                      >
                        <div className="col-start-2 flex max-w-[80%] justify-self-end items-start overflow-hidden rounded-xl p-2 text-gray-500">
                          <CornerDownRight className="mr-2 mt-0.5 size-4 shrink-0 text-gray-500" strokeWidth={2} />
                          <div className="mr-2 line-clamp-3 flex-1 text-xs leading-5">
                            {msg.humanFeedbackPlanPreview}
                          </div>
                        </div>
                        <div className="col-start-2 row-start-2 max-w-[80%] justify-self-end bg-[#F1F1FE] rounded-lg px-3 py-2 text-[#0A0A0B] break-words text-sm font-normal leading-6 shadow-3xs">
                          <p className="whitespace-pre-line m-0">{msg.content}</p>
                        </div>
                        <div className="col-start-2 row-start-3 text-right opacity-0 transition-opacity duration-150 group-hover:opacity-100 select-none">
                          <span className="text-[11px] text-gray-400 font-medium">
                            {new Date().toLocaleDateString('zh-CN')} {new Date().toLocaleTimeString('zh-CN', { hour12: false })}
                          </span>
                        </div>
                      </div>
                    );
                  }

                  return (
                    <div 
                      key={idx} 
                      className="group flex w-full flex-col items-end animate-in fade-in slide-in-from-bottom-2 duration-300 select-text"
                    >
                      <div className="bg-[#F1F1FE] max-w-[80%] rounded-lg px-3 py-2 text-[#0A0A0B] break-words text-sm font-normal leading-6 shadow-3xs">
                        <p className="whitespace-pre-line m-0">{msg.content}</p>
                      </div>
                      <div className="mt-1 flex items-center gap-2 opacity-0 transition-opacity duration-150 group-hover:opacity-100 select-none">
                        <span className="text-[11px] text-gray-400 font-medium">
                          {new Date().toLocaleDateString('zh-CN')} {new Date().toLocaleTimeString('zh-CN', { hour12: false })}
                        </span>
                      </div>
                    </div>
                  );
                }

                // msg.role === 'assistant' 渲染（官网三行无气泡排版样式）
                const userQuery = messages[idx - 1]?.content || "";
                if (!hasRenderableAssistantPayload(msg)) {
                  return null;
                }

                const feedbackAlreadyHandled = messages.slice(idx + 1).some((nextMsg) =>
                  nextMsg.role === 'assistant' &&
                  nextMsg.blocks?.some((block) => block.type === 'text' && block.content.includes('人工审核已'))
                );
                const pendingApproval = hasPendingHumanApproval(msg.blocks) && !feedbackAlreadyHandled;
                const pendingClarification = (msg.workflowEvents || []).some((event) =>
                  event.eventType === 'clarification_request' || event.eventType === 'clarification_confirmation'
                );
                const waitingForUserInput = pendingApproval || pendingClarification;
                const approvalPlanJson = getPlanBlock(msg.blocks);
                const workflowLogs = collectWorkflowLogs(msg.blocks);
                const effectiveComplete = Boolean(msg.isComplete && !waitingForUserInput);
                return (
                  <div 
                    key={idx} 
                    className="group relative flex flex-col w-full py-4 animate-in fade-in slide-in-from-bottom-2 duration-300"
                  >
                    {/* 第一行：头像与名称 */}
                    <div className="flex h-[26px] items-center mb-2 select-none">
                      <span className="relative shrink-0 rounded-full flex h-[26px] w-[26px] items-center overflow-hidden">
                        <img className="h-full w-full aspect-auto" alt="data-agent" src="https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/logo-black.svg" />
                      </span>
                      <span className="font-dm-sans text-[14px] font-semibold text-gray-800 ml-3">{agentName}</span>
                    </div>

                    {/* 第二行：消息内容主体（去掉白底卡片气泡，普通文字直接平铺，Blocks 自身带卡片） */}
                    <div className="pl-[38px] w-full flex flex-col items-start gap-2 break-words leading-7 text-gray-800 text-[14px]">
                      {/* 普通消息内容 */}
                      {msg.content && <div className="font-normal whitespace-pre-wrap">{msg.content}</div>}

                        <WorkflowNodeStack
                          logs={workflowLogs}
                          blocks={msg.blocks || []}
                          isComplete={effectiveComplete}
                          currentBlockCount={msg.blocks?.length || 0}
                        onOpenReport={(content) => {
                          setReportContent(content);
                          handleExpandReport();
                        }}
                      />

                      {/* 流式 Blocks 精细化可折叠卡片渲染 */}
                        {msg.blocks && msg.blocks.map((block, bIdx) => {
                          if (block.type === 'text') {
                            return <ProcessedTextBlock key={bIdx} text={block.content} isComplete={effectiveComplete} query={userQuery} />;
                          }
                        if (block.type === 'json') {
                          // 判断是否为闲聊意图 JSON (匹配 "classification" 或 "reply")
                          const isSmalltalk = block.content.includes('"classification"') || block.content.includes('"reply"');
                          if (isSmalltalk) {
                            const replyText = extractReplyFromIncrementalJson(block.content);
                            if (!replyText || replyText === 'null') {
                              return null;
                            }
                            return (
                              <SmalltalkDataPanel 
                                key={bIdx}
                                reply={replyText} 
                                latestQuery={userQuery} 
                                onConfirmData={(file) => {
                                  setAttachedFiles(prev => [...prev, file]);
                                  setInputValue(prev => prev || `请帮我结合这份数据，进行智能数据分析`);
                                }}
                              />
                            );
                          }

                          // 过滤知识库检索过程的中间 JSON
                          if (block.content.includes('"standalone_query"')) {
                            return null;
                          }

                          // 只有当确定含有工作流计划核心结构时才渲染为工作流执行计划卡片
                          const isWorkflowPlan = block.content.includes('"execution_plan"') || block.content.includes('"thought_process"');
                          if (isWorkflowPlan) {
                            return null;
                          }
                          return null;
                        }
                        if (block.type === 'sql') {
                          return null;
                        }
                        if (block.type === 'python') {
                          return null;
                        }
                        if (block.type === 'result_set') {
                          return null;
                        }
                        if (block.type === 'markdown-report') {
                          return null;
                        }
                        return null;
                      })}

                      {pendingApproval && approvalPlanJson && (
                        <HumanApprovalPanel
                          planJson={approvalPlanJson}
                          disabled={feedbackSubmitting}
                          approveDisabled={feedbackSubmitting || Boolean(activeHumanFeedbackPlanPreview)}
                          onApprove={() => handleHumanFeedback(true)}
                          onEdit={handleStartHumanFeedbackEdit}
                        />
                      )}

                      {/* 如果分析已经彻底完成，渲染耗时时间轴进度图 */}
                      {msg.workflowEvents?.map((event, eventIdx) => {
                        if (event.eventType === 'clarification_request') {
                          const payload = event.payload as ClarificationRequestPayload;
                          return (
                            <ClarificationCard
                              key={`${event.eventType}-${eventIdx}`}
                              mode="answer"
                              question={payload.question || '请补充业务口径后继续分析。'}
                              onSubmit={(value) => handleClarificationInteraction('CLARIFICATION_ANSWER', value)}
                            />
                          );
                        }
                        if (event.eventType === 'clarification_confirmation') {
                          const payload = event.payload as ClarificationConfirmationPayload;
                          return (
                            <ClarificationCard
                              key={`${event.eventType}-${eventIdx}`}
                              mode="confirm"
                              question={payload.confirmationText || '请确认归纳后的业务口径是否正确。'}
                              defaultValue="正确，继续。"
                              onSubmit={(value) => handleClarificationInteraction('CLARIFICATION_CONFIRM', value)}
                            />
                          );
                        }
                        if (event.eventType === 'memory_candidate') {
                          const payload = event.payload as MemoryCandidatePayload;
                          const candidateActionKey = getMemoryCandidateKey(payload.candidateId);
                          const candidateActionStatus = memoryCandidateActions[candidateActionKey] || 'idle';
                          return (
                            <MemoryCandidateCard
                              key={`${event.eventType}-${eventIdx}`}
                              title={payload.title || '候选业务知识'}
                              content={payload.normalizedContent || ''}
                              persistDisabled={!isValidMemoryCandidateId(payload.candidateId)}
                              actionStatus={candidateActionStatus}
                              onIgnore={() => handleMemoryCandidateAction(payload.candidateId, 'ignore')}
                              onSave={() => handleMemoryCandidateAction(payload.candidateId, 'submit')}
                              onPublish={() => handleMemoryCandidateAction(payload.candidateId, 'publish')}
                            />
                          );
                        }
                        return null;
                      })}

                      {effectiveComplete && shouldShowAnalysisTimeLine(userQuery, msg.blocks || []) && (
                        <AnalysisTimeLine query={userQuery} blocks={msg.blocks || []} />
                      )}

                      {/* 流式完成且包含报告，渲染报告入口卡片 */}
                      {effectiveComplete && msg.blocks?.some(b => b.type === 'markdown-report') && (
                        <div className="my-3 w-full max-w-[640px] select-none animate-in fade-in slide-in-from-top-1 duration-200">
                          <div className="flex min-h-[45px] flex-wrap items-center justify-between gap-3 rounded-[10px] border border-gray-200 bg-white px-4 py-2">
                            <div className="flex min-w-0 items-center gap-2 text-[14px] leading-7 text-[#0A0A0B]">
                              <Compass className="size-4 flex-none text-gray-700" />
                              <span className="truncate font-normal">以交互式网页报告分享 Data Agent 的分析</span>
                          </div>
                          <div className="flex items-center gap-2 flex-none">
                            <button 
                              type="button"
                              onClick={() => alert('已取消')}
                              className="inline-flex h-8 min-w-14 items-center justify-center rounded-[10px] border border-gray-200 bg-white px-3 text-[14px] leading-5 font-medium text-gray-700 transition-colors hover:bg-gray-50 cursor-pointer"
                            >
                              取消
                            </button>
                            <button 
                              type="button"
                              onClick={handleExpandReport}
                              className="inline-flex h-8 min-w-14 items-center justify-center rounded-[10px] border border-[#151517] bg-[#151517] px-3 text-[14px] leading-5 font-medium text-[#FAFAFA] transition-colors hover:bg-[#202227] hover:border-[#202227] cursor-pointer"
                            >
                              绘制网页
                            </button>
                          </div>
                        </div>
                      </div>
                      )}
                    </div>

                    {/* 第三行：流式等待态 / 完成后工具栏 */}
                    {!waitingForUserInput && (effectiveComplete ? (
                      <div className="pl-[38px] flex items-center gap-2 mt-2 opacity-0 transition-opacity duration-150 group-hover:opacity-100 select-none">
                        <div className="flex items-center gap-1 transition-opacity duration-200">
                          <button className="inline-flex items-center justify-center p-1 text-gray-400 hover:text-gray-600 hover:bg-gray-200/50 rounded cursor-pointer size-6 transition-all border-none bg-transparent" aria-label="点赞">
                            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-thumbs-up"><path d="M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z"/><path d="M7 10v12"/></svg>
                          </button>
                          <button className="inline-flex items-center justify-center p-1 text-gray-400 hover:text-gray-600 hover:bg-gray-200/50 rounded cursor-pointer size-6 transition-all border-none bg-transparent" aria-label="点踩">
                            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-thumbs-down"><path d="M9 18.12 10 14H4.17a2 2 0 0 1-1.92-2.56l2.33-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.76a2 2 0 0 0-1.79 1.11L12 22a3.13 3.13 0 0 1-3-3.88Z"/><path d="M17 14V2"/></svg>
                          </button>
                        </div>
                        <span className="text-[11px] text-gray-400 font-medium">
                          {new Date().toLocaleDateString('zh-CN')} {new Date().toLocaleTimeString('zh-CN', { hour12: false })}
                        </span>
                      </div>
                    ) : (
                      <div className="pl-[38px] mt-2 flex h-6 items-center gap-1 text-gray-400 select-none" aria-label="模型仍在回答中">
                        <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-gray-400" />
                        <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-gray-400 delay-100" />
                        <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-gray-400 delay-200" />
                      </div>
                    ))}
                  </div>
                );
              })}

              {isTyping && !hasRenderableAssistantPayload([...messages].reverse().find(msg => msg.role === 'assistant')) && (
                <div className="group relative flex flex-col w-full py-4 animate-pulse">
                  {/* 第一行：头像与名称 */}
                  <div className="flex h-[26px] items-center mb-2 select-none">
                    <span className="relative shrink-0 rounded-full flex h-[26px] w-[26px] items-center overflow-hidden">
                      <img className="h-full w-full aspect-auto animate-spin" alt="data-agent" src="https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/logo-black.svg" />
                    </span>
                    <span className="font-dm-sans text-[14px] font-semibold text-gray-800 ml-3">{agentName}</span>
                  </div>
                  {/* 第二行：消息体内容 */}
                  <div className="pl-[38px] w-full flex items-center gap-1.5 text-gray-500 text-[13px] font-medium">
                    <span>{chatMode === 'nl2sqlOnly' ? '正在生成 SQL' : chatMode === 'humanReview' ? '正在等待人工审核流程' : '正在分析数据'}</span>
                    <span className="animate-bounce">.</span>
                    <span className="animate-bounce delay-100">.</span>
                    <span className="animate-bounce delay-200">.</span>
                  </div>
                </div>
              )}
            </div>
          ) : (
            /* 🏡 落地页状态下的主视口 (包含标题) */
            <div className="group/view-port flex w-[680px] min-w-[680px] flex-col items-center justify-start gap-4 px-0 py-6 flex-none mt-12">
              <div className="group/header mb-10 flex w-full flex-none flex-shrink-0 flex-col items-start justify-center gap-2 overflow-hidden select-none">
                <h1 className="font-dm-sans w-full text-4xl font-semibold text-[#0A0A0B] tracking-tight leading-none">
                  Hola, I'm <span className="bg-clip-text text-transparent bg-gradient-to-r from-[#6b73ff] to-[#000dff] font-bold opacity-90">{agentName}</span>
                </h1>
                <p className="text-gray-500 w-full overflow-hidden text-2xl font-light">
                  从数据到洞察，Agent驱动的数据价值放大器
                </p>
              </div>
            </div>
          )}

          {/* 底部输入框外壳 */}
          </div>
          <div 
            className={clsx(
              "z-20 flex flex-col items-center",
              isReportResizing ? "transition-none" : "transition-[background-color,transform,width] duration-300 ease-out",
              isChatState 
                ? (isReportOpen 
                    ? "absolute bottom-0 left-0 right-0 flex justify-center pb-6 pt-4 bg-[#F6F6F6]/95 backdrop-blur-xs px-4"
                    : "absolute bottom-0 left-0 right-0 flex justify-center pb-6 pt-4 bg-gradient-to-t from-[#F6F6F6] via-[#F6F6F6] to-transparent px-4"
                  )
                : "w-[680px] mb-6 mx-auto"
            )}
          >
            {isChatState && interruptedRun?.resumable && (
              <div className="mb-4 w-full max-w-[800px] select-none">
                <div className="overflow-hidden rounded-[10px] border border-gray-200 bg-white">
                  <div className="flex min-h-[45px] flex-wrap items-center justify-between gap-3 px-6 py-2">
                    <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2 text-[14px] leading-7 text-[#0A0A0B]">
                      <span className="font-normal">上次会话异常中断，是否继续分析</span>
                      {interruptedRun.interruptReason && (
                        <span className="max-w-full truncate rounded-full bg-[#F3F4F6] px-3 py-1 text-[12px] font-medium leading-5 text-[#6B7280]">
                          {interruptedRun.interruptReason}
                        </span>
                      )}
                    </div>
                    <button
                      type="button"
                      onClick={handleContinueAnalysis}
                      disabled={feedbackSubmitting || isTyping}
                      className="inline-flex items-center justify-center rounded-[10px] border border-[#151517] bg-[#151517] px-3 py-1 text-[14px] leading-5 font-medium text-[#FAFAFA] transition-colors hover:bg-[#202227] hover:border-[#202227] disabled:cursor-not-allowed disabled:opacity-60 cursor-pointer"
                    >
                      继续分析
                    </button>
                  </div>
                </div>
              </div>
            )}
            {isChatState && hasPendingHumanReviewNotice && (
              <div className="mb-4 flex w-full justify-center select-none">
                <div className="inline-flex h-9 items-center justify-center gap-2 rounded-[24px] border border-[#D9B54A] bg-white px-6 text-[14px] leading-5 font-normal text-[#3B3B3B]">
                  <ListTodo className="size-4 shrink-0 text-[#B88A00]" strokeWidth={2} />
                  <span>我将在你确认后继续</span>
                </div>
              </div>
            )}
            <div 
              className={clsx(
                "group/composer flex flex-col items-start justify-center bg-[#ECEEF6] rounded-3xl p-1 z-10 shadow-sm w-full",
                isReportResizing ? "transition-none" : "transition-[max-width] duration-300 ease-out"
              )}
              style={{ maxWidth: isChatState ? '807.33px' : '680px' }}
            >
              {!isChatState && (
                <div data-testid="quick-queries" className="my-2 flex w-full items-center gap-2 px-2 overflow-x-auto no-scrollbar">
                  <div 
                    onClick={() => handleSend('帮我看看2022年英国各餐厅的销售情况')}
                    className="flex h-7 items-center gap-1 overflow-hidden rounded-2xl border border-gray-200 bg-white px-3 text-xs text-gray-700 hover:text-black hover:bg-gray-50 cursor-pointer shadow-sm shrink-0 transition-colors"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-[#5569ff] w-3.5 h-3.5 shrink-0"><path d="M3 3v16a2 2 0 0 0 2 2h16"></path><path d="m19 9-5 5-4-4-3 3"></path></svg>
                    <span className="flex-1 truncate">帮我看看2022年英国各餐厅的销售情况</span>
                  </div>
                  <div 
                    onClick={() => handleSend('请帮我结合这份游戏数据，洞察并产出策略类游戏的营销策略')}
                    className="flex h-7 items-center gap-1 overflow-hidden rounded-2xl border border-gray-200 bg-white px-3 text-xs text-gray-700 hover:text-black hover:bg-gray-50 cursor-pointer shadow-sm shrink-0 transition-colors"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-[#5569ff] w-3.5 h-3.5 shrink-0"><path d="M21 12c.552 0 1.005-.449.95-.998a10 10 0 0 0-8.953-8.951c-.55-.055-.998.398-.998.95v8a1 1 0 0 0 1 1z"></path><path d="M21.21 15.89A10 10 0 1 1 8 2.83"></path></svg>
                    <span className="flex-1 truncate">请帮我结合这份游戏数据，洞察并产出策略类...</span>
                  </div>
                  <button className="inline-flex items-center justify-center p-1 text-gray-400 hover:text-gray-600 hover:bg-white/80 rounded-full transition-colors shrink-0 size-7">
                    <RefreshCcw className="w-4 h-4" />
                  </button>
                </div>
              )}

              <form 
                onSubmit={(e) => {
                  e.preventDefault();
                  handleSend(inputValue);
                }}
                className={clsx(
                  "border-border shadow-sm flex w-full flex-col rounded-3xl border border-gray-100 bg-white transition-colors ease-in focus-within:border-gray-200",
                  isChatState ? "px-5 pb-2 pt-4" : "p-5"
                )}
              >
                {activeHumanFeedbackPlanPreview && (
                  <div className="mb-4 flex items-start overflow-hidden rounded-xl bg-[#F5F5F5] p-2">
                    <CornerDownRight className="mr-2 mt-0.5 size-4 shrink-0 text-gray-400" strokeWidth={2} />
                    <div className="mr-2 max-h-[4.5rem] flex-1 overflow-hidden text-xs leading-6 text-gray-500">
                      {activeHumanFeedbackPlanPreview}
                    </div>
                    <button
                      type="button"
                      onClick={handleCancelHumanFeedbackEdit}
                      className="ml-auto inline-flex size-5 shrink-0 items-center justify-center rounded-md text-gray-500 transition-colors hover:bg-gray-200 hover:text-gray-800"
                      aria-label="取消修改计划"
                    >
                      <X className="size-4" />
                    </button>
                  </div>
                )}
                {attachedFiles.length > 0 && (
                  <div className="flex flex-wrap items-center gap-3 mb-2 animate-in fade-in slide-in-from-top-1 duration-200 select-none">
                    {attachedFiles.map((file, fileIdx) => {
                      const preview = getPreviewData(file.name);
                      return (
                        <HoverCard.Root key={file.id || fileIdx} openDelay={400} closeDelay={150}>
                          <HoverCard.Trigger asChild>
                            <div className="group/attachment relative cursor-pointer">
                              <div className="box-content h-9 min-w-44 max-w-60 px-3.5 py-1 border border-gray-200/80 bg-gray-50/60 flex items-center justify-center gap-2.5 rounded-xl transition-all hover:bg-gray-100 hover:border-gray-300 hover:shadow-xs">
                                <span className="shrink-0 overflow-hidden flex size-8 items-center justify-center rounded-lg bg-gray-100 text-gray-500 border border-gray-250/50 shadow-2xs">
                                  <Sheet className="size-4 text-gray-500" />
                                </span>
                                <div className="flex-grow basis-0 overflow-hidden text-left">
                                  <p className="m-0 line-clamp-1 truncate text-ellipsis break-all p-0 text-xs font-semibold text-gray-800">
                                    {file.name}
                                  </p>
                                  <p className="m-0 p-0 text-[10px] text-gray-400 font-medium">
                                    {file.size ? `${file.size}` : 'File'}
                                  </p>
                                </div>
                              </div>
                              <button 
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setAttachedFiles(prev => prev.filter((_, idx) => idx !== fileIdx));
                                }}
                                className="absolute -right-1.5 -top-1.5 size-5 rounded-full border border-gray-155 bg-white shadow-sm flex items-center justify-center text-gray-400 hover:text-gray-700 cursor-pointer active:scale-95 transition-all opacity-0 group-hover/attachment:opacity-100 z-10"
                                type="button"
                              >
                                <X className="size-3" />
                              </button>
                            </div>
                          </HoverCard.Trigger>
                          
                          <HoverCard.Portal>
                            <HoverCard.Content 
                              className="z-50 w-[550px] bg-white border border-gray-200 rounded-xl shadow-xl p-4.5 animate-in fade-in zoom-in-95 duration-200 outline-none"
                              side="top"
                              align="start"
                              sideOffset={10}
                            >
                              <div className="flex items-center gap-2 mb-3 text-gray-855 select-text">
                                <Maximize2 
                                  onClick={() => setFullscreenPreviewFile(file)}
                                  className="size-4 text-gray-405 shrink-0 cursor-pointer hover:text-gray-700 transition-colors" 
                                />
                                <span className="text-sm font-bold truncate">{file.name}</span>
                              </div>
                              
                              <div className="overflow-auto border border-gray-150 rounded-lg max-h-[220px] select-text">
                                <table className="w-full text-left border-collapse text-[11.5px] leading-relaxed">
                                  <thead className="sticky top-0 bg-gray-50/90 backdrop-blur-xs z-10 border-b border-gray-150 select-none">
                                    <tr>
                                      {preview.columns.map((col: string) => (
                                        <th key={col} className="px-3 py-2 font-bold text-gray-500 whitespace-nowrap border-r border-gray-100 last:border-r-0">
                                          {col}
                                        </th>
                                      ))}
                                    </tr>
                                  </thead>
                                  <tbody className="divide-y divide-gray-100">
                                    {preview.rows.map((row: any, rIdx: number) => (
                                      <tr key={rIdx} className="hover:bg-gray-50/50 transition-colors">
                                        {preview.columns.map((col: string) => (
                                          <td key={col} className="px-3 py-1.5 text-gray-700 font-medium whitespace-nowrap max-w-44 truncate border-r border-gray-100 last:border-r-0">
                                            {row[col]}
                                          </td>
                                        ))}
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              </div>
                            </HoverCard.Content>
                          </HoverCard.Portal>
                        </HoverCard.Root>
                );
              })}
              <div ref={chatBottomRef} className="h-px w-full" />
            </div>
                )}
                <textarea 
                  ref={composerTextareaRef}
                  placeholder={activeHumanFeedbackPlanPreview ? '写下你希望调整的统计口径、步骤或输出形式' : "通过下方指定一份数据并给我布置数据分析任务，'shift+enter'换行"}
                  value={inputValue}
                  onChange={(e) => setInputValue(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault();
                      handleSend(inputValue);
                    }
                  }}
                  className={clsx(
                    "max-h-120 flex-grow resize-none border-none bg-transparent outline-none placeholder:text-gray-400/80 w-full px-0 py-2 focus:outline-none focus:ring-0 focus-visible:ring-0 text-[14px] text-[#0A0A0B] leading-relaxed font-sans",
                    isChatState ? "min-h-[40px]" : "min-h-[76px]"
                  )}
                />
                <input 
                  type="file" 
                  ref={fileInputRef} 
                  onChange={handleFileChange} 
                  className="hidden" 
                  accept=".csv,.xlsx,.xls"
                />
                
                <div className={clsx("flex items-center gap-2 w-full", isChatState ? "mt-3.5" : "mt-6")}>
                  <Tooltip.Provider delayDuration={150}>
                    <DropdownMenu.Root>
                      <Tooltip.Root>
                        <Tooltip.Trigger asChild>
                          <DropdownMenu.Trigger asChild>
                            <button className="justify-center gap-2 whitespace-nowrap font-medium flex items-center text-gray-550 hover:text-gray-800 size-7 rounded-full p-0 hover:bg-gray-100 cursor-pointer active:scale-95 transition-all outline-none border-none bg-transparent" type="button">
                              <Plus className="w-4 h-4 flex-none" />
                            </button>
                          </DropdownMenu.Trigger>
                        </Tooltip.Trigger>
                        <Tooltip.Portal>
                          <Tooltip.Content 
                            className="z-50 border border-gray-200 bg-white px-3 py-1.5 text-xs text-gray-800 rounded-lg shadow-[0_4px_12px_rgba(0,0,0,0.06)] select-none font-medium leading-none animate-tooltip-in"
                            side="top"
                            align="center"
                            sideOffset={8}
                          >
                            添加数据
                          </Tooltip.Content>
                        </Tooltip.Portal>
                      </Tooltip.Root>

                      <DropdownMenu.Portal>
                        <DropdownMenu.Content 
                          className="z-50 border border-gray-200 bg-white min-w-56 rounded-2xl p-1.5 shadow-[0_10px_30px_rgba(0,0,0,0.08)] animate-tooltip-in outline-none flex flex-col gap-0.5"
                          side="bottom"
                          align="start"
                          sideOffset={8}
                        >
                          <DropdownMenu.Item 
                            onClick={() => fileInputRef.current?.click()}
                            className="flex cursor-pointer items-center gap-2 rounded-xl px-3.5 py-2.5 text-sm text-gray-700 hover:bg-gray-100 outline-none select-none font-medium transition-colors"
                          >
                            <Upload className="w-4 h-4 text-gray-500 shrink-0" />
                            <span className="flex-1 text-left text-[13px]">本地上传（CSV/Excel）</span>
                          </DropdownMenu.Item>

                          <DropdownMenu.Item 
                            onClick={() => {
                              setSelectedFilesInModal(attachedFiles);
                              setModalSearchQuery('');
                              setIsSelectDataOpen(true);
                            }}
                            className="flex cursor-pointer items-center gap-2 rounded-xl px-3.5 py-2.5 text-sm text-gray-700 hover:bg-gray-100 outline-none select-none font-medium transition-colors"
                          >
                            <Sheet className="w-4 h-4 text-gray-500 shrink-0" />
                            <span className="flex-1 text-left text-[13px]">选择已有数据</span>
                          </DropdownMenu.Item>

                          <DropdownMenu.Sub>
                            <DropdownMenu.SubTrigger className="flex cursor-pointer items-center gap-2 rounded-xl px-3.5 py-2.5 text-sm text-gray-700 hover:bg-gray-100 outline-none select-none font-medium transition-colors data-[state=open]:bg-gray-100">
                              <BookOpen className="w-4 h-4 text-gray-500 shrink-0" />
                              <span className="flex-1 text-left text-[13px]">知识库</span>
                              {selectedKbs.length > 0 && (
                                <span className="ml-auto mr-1.5 text-[11px] text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded-md min-w-[18px] text-center font-medium">
                                  {selectedKbs.length}
                                </span>
                              )}
                              <ChevronRight className="w-4 h-4 text-gray-400 shrink-0 ml-auto" />
                            </DropdownMenu.SubTrigger>
                            <DropdownMenu.Portal>
                              <DropdownMenu.SubContent 
                                className="z-50 border border-gray-200 bg-white min-w-[8rem] rounded-xl p-2 shadow-lg animate-in fade-in zoom-in-95 data-[side=right]:slide-in-from-left-2 duration-200 outline-none w-80"
                                sideOffset={6}
                                alignOffset={-4}
                              >
                                <div className="flex h-full flex-col max-h-72">
                                  <div className="flex items-center border-b border-gray-100 px-2 py-1.5 mb-1">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-search mr-2 h-4 w-4 shrink-0 opacity-50"><path d="m21 21-4.34-4.34"></path><circle cx="11" cy="11" r="8"></circle></svg>
                                    <input 
                                      className="flex w-full rounded-md bg-transparent text-sm outline-none placeholder:text-gray-400 h-8" 
                                      placeholder="搜索知识库..."
                                      value={kbSearchQuery}
                                      onChange={(e) => setKbSearchQuery(e.target.value)}
                                      onClick={(e) => e.stopPropagation()}
                                    />
                                  </div>
                                  
                                  <div className="overflow-x-hidden max-h-48 flex-1 overflow-y-auto py-1">
                                    {('我的知识库'.toLowerCase().includes(kbSearchQuery.toLowerCase())) ? (
                                      <div 
                                        onClick={(e) => {
                                          e.stopPropagation();
                                          const isSelected = selectedKbs.includes('我的知识库');
                                          if (isSelected) {
                                            setSelectedKbs(prev => prev.filter(k => k !== '我的知识库'));
                                          } else {
                                            setSelectedKbs(prev => [...prev, '我的知识库']);
                                          }
                                        }}
                                        className={clsx(
                                          "relative select-none text-sm outline-none mb-1 flex items-start gap-2 rounded-md px-2 py-2 last:mb-0 cursor-pointer transition-colors",
                                          selectedKbs.includes('我的知识库') 
                                            ? "bg-blue-50 text-blue-600 font-semibold" 
                                            : "text-gray-700 hover:bg-gray-100"
                                        )}
                                      >
                                        <BookOpen className="text-gray-400 mt-0.5 size-4 flex-none" />
                                        <div className="flex-1 overflow-hidden">
                                          <div className="truncate text-sm font-medium">我的知识库</div>
                                        </div>
                                        <div 
                                          className="text-gray-400 hover:text-gray-600 transition-colors mr-1 cursor-pointer" 
                                          title="查看详情"
                                          onClick={(e) => {
                                            e.stopPropagation();
                                            navigate('/knowledge');
                                          }}
                                        >
                                          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-eye size-3.5"><path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"></path><circle cx="12" cy="12" r="3"></circle></svg>
                                        </div>
                                      </div>
                                    ) : (
                                      <div className="text-center text-xs text-gray-400 py-6">无匹配的知识库</div>
                                    )}
                                  </div>
                                  
                                  <div className="border-t border-gray-100 pt-2 mt-1">
                                    <button 
                                      type="button"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        navigate('/knowledge');
                                      }}
                                      className="flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-sm text-gray-700 hover:bg-gray-100 transition-colors border-none bg-transparent cursor-pointer"
                                    >
                                      <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-eye text-gray-400 size-4"><path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"></path><circle cx="12" cy="12" r="3"></circle></svg>
                                      <span>管理知识库</span>
                                    </button>
                                  </div>
                                </div>
                              </DropdownMenu.SubContent>
                            </DropdownMenu.Portal>
                          </DropdownMenu.Sub>

                          <DropdownMenu.Sub>
                            <DropdownMenu.SubTrigger className="flex cursor-pointer items-center gap-2 rounded-xl px-3.5 py-2.5 text-sm text-gray-700 hover:bg-gray-100 outline-none select-none font-medium transition-colors data-[state=open]:bg-gray-100">
                              <Plug className="w-4 h-4 text-gray-500 shrink-0" />
                              <span className="flex-1 text-left text-[13px]">连接MCP</span>
                              {selectedMcps.length > 0 && (
                                <span className="ml-auto mr-1.5 text-[11px] text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded-md min-w-[18px] text-center font-medium">
                                  {selectedMcps.length}
                                </span>
                              )}
                              <ChevronRight className="w-4 h-4 text-gray-400 shrink-0 ml-auto" />
                            </DropdownMenu.SubTrigger>
                            <DropdownMenu.Portal>
                              <DropdownMenu.SubContent 
                                className="z-50 border border-gray-200 bg-white min-w-[8rem] rounded-xl p-2 shadow-lg animate-in fade-in zoom-in-95 data-[side=right]:slide-in-from-left-2 duration-200 outline-none w-80"
                                sideOffset={6}
                                alignOffset={-4}
                              >
                                <div className="flex h-full flex-col max-h-72">
                                  <div className="flex items-center border-b border-gray-100 px-2 py-1.5 mb-1">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-search mr-2 h-4 w-4 shrink-0 opacity-50"><path d="m21 21-4.34-4.34"></path><circle cx="11" cy="11" r="8"></circle></svg>
                                    <input 
                                      className="flex w-full rounded-md bg-transparent text-sm outline-none placeholder:text-gray-400 h-8" 
                                      placeholder="搜索..."
                                      value={mcpSearchQuery}
                                      onChange={(e) => setMcpSearchQuery(e.target.value)}
                                      onClick={(e) => e.stopPropagation()}
                                    />
                                  </div>
                                  
                                  <div className="overflow-x-hidden max-h-48 flex-1 overflow-y-auto py-1">
                                    {(() => {
                                      const mockMcpServices = ['SQLite服务', '谷歌搜索服务'];
                                      const filteredMcps = mockMcpServices.filter(mcp => 
                                        mcp.toLowerCase().includes(mcpSearchQuery.trim().toLowerCase())
                                      );
                                      
                                      if (filteredMcps.length === 0) {
                                        return <div className="py-2 text-center text-sm text-gray-400">暂无数据</div>;
                                      }
                                      
                                      return filteredMcps.map(mcp => {
                                        const isSelected = selectedMcps.includes(mcp);
                                        return (
                                          <div
                                            key={mcp}
                                            onClick={(e) => {
                                              e.stopPropagation();
                                              if (isSelected) {
                                                setSelectedMcps(prev => prev.filter(m => m !== mcp));
                                              } else {
                                                setSelectedMcps(prev => [...prev, mcp]);
                                              }
                                            }}
                                            className={clsx(
                                              "relative select-none text-sm outline-none mb-1 flex items-start gap-2 rounded-md px-2 py-2 last:mb-0 cursor-pointer transition-colors",
                                              isSelected 
                                                ? "bg-purple-50 text-purple-600 font-semibold" 
                                                : "text-gray-700 hover:bg-gray-100"
                                            )}
                                          >
                                            <Plug className="text-gray-400 mt-0.5 size-4 flex-none" />
                                            <div className="flex-1 overflow-hidden">
                                              <div className="truncate text-sm font-medium">{mcp}</div>
                                            </div>
                                          </div>
                                        );
                                      });
                                    })()}
                                  </div>
                                  
                                  <div className="border-t border-gray-100 pt-2 mt-1">
                                    <button 
                                      type="button"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        navigate('/mcp');
                                      }}
                                      className="flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-sm text-gray-700 hover:bg-gray-100 transition-colors border-none bg-transparent cursor-pointer"
                                    >
                                      <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-settings text-gray-400 size-4"><path d="M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915"></path><circle cx="12" cy="12" r="3"></circle></svg>
                                      <span>管理MCP服务</span>
                                    </button>
                                  </div>
                                </div>
                              </DropdownMenu.SubContent>
                            </DropdownMenu.Portal>
                          </DropdownMenu.Sub>
                        </DropdownMenu.Content>
                      </DropdownMenu.Portal>
                    </DropdownMenu.Root>

                    <Tooltip.Root>
                      <Tooltip.Trigger asChild>
                        <button className="justify-center gap-2 whitespace-nowrap font-medium flex items-center text-gray-550 hover:text-gray-800 size-7 rounded-full p-0 hover:bg-gray-100 cursor-pointer active:scale-95 transition-all outline-none border-none bg-transparent" type="button">
                          <Settings2 className="w-4 h-4 flex-none" />
                        </button>
                      </Tooltip.Trigger>
                      <Tooltip.Portal>
                        <Tooltip.Content 
                          className="z-50 border border-gray-200 bg-white px-3 py-1.5 text-xs text-gray-800 rounded-lg shadow-[0_4px_12px_rgba(0,0,0,0.06)] select-none font-medium leading-none animate-tooltip-in"
                          side="top"
                          align="center"
                          sideOffset={8}
                        >
                          搜索和工具
                        </Tooltip.Content>
                      </Tooltip.Portal>
                    </Tooltip.Root>
                  </Tooltip.Provider>

                  {/* 已选择的知识库标签 */}
                  {selectedKbs.map((kbName) => (
                    <button
                      key={kbName}
                      onClick={() => setSelectedKbs(prev => prev.filter(k => k !== kbName))}
                      className="flex items-center gap-1 h-7 rounded-2xl border border-blue-100 bg-blue-50 hover:bg-blue-100/60 text-blue-600 px-3 text-xs font-semibold cursor-pointer transition-colors active:scale-95 border-none"
                      type="button"
                    >
                      <BookOpen className="w-3.5 h-3.5 flex-none" />
                      <span className="max-w-[70px] truncate">{kbName}</span>
                    </button>
                  ))}

                  {/* 已选择的 MCP 标签 */}
                  {selectedMcps.map((mcpName) => (
                    <button
                      key={mcpName}
                      onClick={() => setSelectedMcps(prev => prev.filter(m => m !== mcpName))}
                      className="flex items-center gap-1 h-7 rounded-2xl border border-purple-100 bg-purple-50 hover:bg-purple-100/60 text-purple-600 px-3 text-xs font-semibold cursor-pointer transition-colors active:scale-95 border-none"
                      type="button"
                    >
                      <Plug className="w-3.5 h-3.5 flex-none" />
                      <span className="max-w-[70px] truncate">{mcpName}</span>
                    </button>
                  ))}

                  <div className="shrink-0 bg-gray-200 w-[1px] h-4"></div>
                  
                  {/* 后端模式选择 */}
                  <div className="flex h-7 items-center gap-2">
                    <button
                      onClick={() => setChatMode(prev => prev === 'nl2sqlOnly' ? null : 'nl2sqlOnly')}
                      className={clsx(
                        "flex h-7 items-center gap-1 rounded-2xl px-2.5 text-xs font-semibold transition-colors cursor-pointer border-none",
                        chatMode === 'nl2sqlOnly'
                          ? "bg-emerald-50 text-emerald-600"
                          : "bg-transparent text-stone-800 hover:bg-gray-50"
                      )}
                      type="button"
                      aria-pressed={chatMode === 'nl2sqlOnly'}
                      title="仅 NL2SQL"
                    >
                      <Database className="w-3.5 h-3.5 flex-none" />
                      <span>仅 NL2SQL</span>
                    </button>
                    <button
                      onClick={() => setChatMode(prev => prev === 'humanReview' ? null : 'humanReview')}
                      className={clsx(
                        "flex h-7 items-center gap-1 rounded-2xl px-2.5 text-xs font-semibold transition-colors cursor-pointer border-none",
                        chatMode === 'humanReview'
                          ? "bg-emerald-50 text-emerald-600"
                          : "bg-transparent text-stone-800 hover:bg-gray-50"
                      )}
                      type="button"
                      aria-pressed={chatMode === 'humanReview'}
                      title="人工审核"
                    >
                      <Check className="w-3.5 h-3.5 flex-none" />
                      <span>人工审核</span>
                    </button>
                  </div>

                  <div className="flex flex-1 items-center justify-end">
                    <button 
                      type={isGenerating ? 'button' : 'submit'}
                      onClick={isGenerating ? handleStopGenerating : undefined}
                      className="inline-flex items-center justify-center gap-2 whitespace-nowrap font-medium size-7 cursor-pointer rounded-full p-0 bg-gray-900 text-white hover:bg-gray-800 transition-colors"
                      aria-label={isGenerating ? '停止生成' : '发送消息'}
                    >
                      {isGenerating ? (
                        <Square className="w-3 h-3 fill-current stroke-[2.5]" />
                      ) : (
                        <ArrowUp className="w-4 h-4 stroke-[2.5]" />
                      )}
                    </button>
                  </div>
                </div>
              </form>

              {/* 对话状态下底盘底部的提示文字 */}
              {isChatState && (
                <div className="w-full text-center py-1.5 select-none">
                  <p className="m-0 text-gray-400 text-[11px] font-medium tracking-wide">
                    内容由人工智能生成合成
                  </p>
                </div>
              )}
            </div>

            {/* 🏡 底部下载提示条 (移到输入框灰色底座外面展示，防止撑大输入框) */}
            {!isChatState && showSkillBanner && (
              <div data-testid="landing-skill-banner" className="flex w-full items-center gap-3 px-4 py-2.5 mt-2">
                <BookOpen className="h-4 w-4 flex-none text-gray-500 opacity-70" />
                <span className="flex-1 select-text text-sm text-gray-600 opacity-80">下载安装 Data Agent Skill，为智能体增强数据洞察力</span>
                <a href="https://github.com/aliyun/data-agent-skill" target="_blank" rel="noopener noreferrer" className="text-[#5569ff] flex-none text-sm font-medium hover:underline">前往 GitHub ↗</a>
                <button 
                  onClick={() => setShowSkillBanner(false)}
                  type="button" 
                  className="flex-none rounded p-1 hover:bg-white/50 text-gray-400 hover:text-gray-600 cursor-pointer" 
                  aria-label="关闭"
                >
                  <X className="h-4 w-4 opacity-60" />
                </button>
              </div>
            )}
          </div>

          {/* 🏡 演示案例区 */}
          {!isChatState && (
            <div data-testid="landing-demo-cases" className="group/cases flex min-h-80 w-[680px] flex-shrink-0 flex-col items-center mb-8 flex-none px-1">
              <div className="w-full">
                <h2 className="text-[14px] text-gray-500 font-medium mb-4 pl-1">演示案例</h2>
                <div className="grid grid-cols-3 gap-4">
                  {demos.map((demo, idx) => (
                    <div 
                      key={idx} 
                      onClick={() => handleSend(demo.prompt)}
                      className="bg-white rounded-xl border border-gray-100 p-4 hover:shadow-lg hover:shadow-gray-200/40 transition-all cursor-pointer h-[140px] flex flex-col relative overflow-hidden group"
                    >
                      <div className="flex items-center gap-2 mb-2 z-10">
                        <span className={clsx("text-[10px] px-1.5 py-0.5 rounded-md font-medium", demo.tagColor)}>
                          {demo.type}
                        </span>
                        <span className="text-[13px] text-gray-700 font-medium">
                          {demo.title}
                        </span>
                      </div>
                      <div className="absolute inset-x-0 bottom-0 top-10 flex items-center justify-center opacity-80 group-hover:scale-105 group-hover:opacity-100 transition-all duration-500">
                        <img src={demo.img} alt={demo.title} className="w-[85%] h-auto object-contain" draggable={false} />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

        </div>

        {/* 右侧报告栏：当前渲染 Markdown，后续承载 HTML 报告 */}
        {!isReportOpen && reportContent.trim() && (
          <button
            type="button"
            onClick={handleExpandReport}
            className="absolute right-4 top-4 z-30 inline-flex h-10 items-center gap-2 rounded-xl border border-gray-200 bg-white px-4 text-sm font-semibold text-gray-700 shadow-sm transition-colors hover:bg-gray-50"
          >
            <ChevronRight className="size-4" />
            <span>展开报告</span>
          </button>
        )}
        <InteractiveReport
          isOpen={isReportOpen}
          width={reportPanelWidth}
          markdownContent={reportContent}
          onClose={handleCollapseReport}
          onWidthChange={handleReportPanelWidthChange}
          onResizeStateChange={setIsReportResizing}
        />
      </div>

      {/* 指定库表进行分析的弹窗 */}
      <Dialog.Root open={isSelectDataOpen} onOpenChange={setIsSelectDataOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-50 bg-black/40 backdrop-blur-[2px] data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0" />
          <Dialog.Content className="fixed left-[50%] top-[50%] z-50 w-full max-w-[780px] -translate-x-1/2 -translate-y-1/2 gap-4 border border-gray-200 bg-white p-6 shadow-xl duration-200 flex h-[640px] flex-col text-sm text-gray-800 rounded-2xl outline-none select-none">
            <div className="flex flex-col space-y-1.5 text-left">
              <Dialog.Title className="text-lg tracking-tight text-gray-900 m-0 font-bold leading-7">
                指定库表进行分析
              </Dialog.Title>
            </div>
            
            <div className="flex flex-1 flex-col overflow-hidden">
              <div className="flex h-full flex-col">
                <div className="mb-4 flex justify-start items-center">
                  <div className="mr-4">
                    <div className="bg-gray-100 flex h-9 items-center justify-center rounded-xl p-[3px] w-fit">
                      <button 
                        type="button"
                        onClick={() => setSelectedCategory('file')}
                        className={clsx(
                          "inline-flex items-center justify-center gap-2 whitespace-nowrap text-sm font-medium px-4 py-1.5 h-7 rounded-lg border-none transition-all cursor-pointer",
                          selectedCategory === 'file' 
                            ? "bg-white text-gray-900 shadow-sm font-semibold" 
                            : "text-gray-500 hover:bg-gray-200/50 hover:text-gray-900"
                        )}
                      >
                        本地上传
                      </button>
                      <button 
                        type="button"
                        onClick={() => setSelectedCategory('db')}
                        className={clsx(
                          "inline-flex items-center justify-center gap-2 whitespace-nowrap text-sm font-medium px-4 py-1.5 h-7 rounded-lg border-none transition-all cursor-pointer",
                          selectedCategory === 'db' 
                            ? "bg-white text-gray-900 shadow-sm font-semibold" 
                            : "text-gray-500 hover:bg-gray-200/50 hover:text-gray-900"
                        )}
                      >
                        数据源
                      </button>
                    </div>
                  </div>
                  
                  <div className="relative w-80">
                    <input 
                      type="text"
                      className="flex w-full rounded-xl border border-gray-200 bg-white px-3 py-2 text-sm placeholder:text-gray-400 focus-visible:outline-none focus:border-gray-300 h-9 pl-8 text-gray-800"
                      placeholder="搜索" 
                      value={modalSearchQuery}
                      onChange={(e) => setModalSearchQuery(e.target.value)}
                    />
                    <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-search absolute left-2.5 top-1/2 -translate-y-1/2 text-sm text-gray-400 size-4 pointer-events-none">
                      <circle cx="11" cy="11" r="8"></circle>
                      <path d="m21 21-4.3-4.3"></path>
                    </svg>
                  </div>
                  
                  <button 
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    className="inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-xl text-sm font-medium border border-gray-200 bg-white hover:bg-gray-50 px-4 py-2 ml-2 h-9 text-gray-700 cursor-pointer active:scale-[0.98] transition-all"
                  >
                    添加数据
                  </button>
                </div>

                <div className="relative flex flex-1 flex-col overflow-hidden">
                  <div className="max-h-full flex-1 overflow-auto rounded-xl border border-gray-200">
                    {selectedCategory === 'file' ? (
                      filteredFiles.length > 0 ? (
                        <table className="w-full text-sm border-collapse text-left">
                          <thead className="sticky top-0 bg-gray-50 border-b border-gray-200 z-10">
                            <tr>
                              <th className="font-semibold text-gray-500 h-9 px-4 text-xs">库/表名称</th>
                              <th className="font-semibold text-gray-500 h-9 px-4 text-xs">类型</th>
                              <th className="font-semibold text-gray-500 h-9 px-4 text-xs">创建时间</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-gray-100">
                            {filteredFiles.map((file) => {
                              const isSelected = selectedFilesInModal.some(f => f.id === file.id);
                              return (
                                <tr 
                                  key={file.id} 
                                  onClick={() => {
                                    if (isSelected) {
                                      setSelectedFilesInModal(prev => prev.filter(f => f.id !== file.id));
                                    } else {
                                      setSelectedFilesInModal(prev => [...prev, file]);
                                    }
                                  }}
                                  className={clsx(
                                    "border-b border-gray-150 transition-colors cursor-pointer text-gray-700 hover:bg-gray-50/80",
                                    isSelected && "bg-blue-50/60 hover:bg-blue-50/85"
                                  )}
                                >
                                  <td className="h-9 px-4">
                                    <div className="flex items-center gap-2">
                                      <Sheet className={clsx("size-4 shrink-0", isSelected ? "text-blue-500" : "text-gray-400")} />
                                      <span className={clsx("font-medium truncate max-w-[320px]", isSelected ? "text-blue-600" : "text-gray-800")}>
                                        {file.name}
                                      </span>
                                    </div>
                                  </td>
                                  <td className="h-9 px-4">
                                    <span className={clsx("font-medium text-xs px-2 py-0.5 rounded-md", isSelected ? "bg-blue-100/50 text-blue-600" : "bg-gray-100 text-gray-500")}>
                                      {file.type === 'CSV' ? 'CSV文件' : 'Excel文件'}
                                    </span>
                                  </td>
                                  <td className="h-9 px-4 text-gray-400 font-medium text-xs">{file.createdAt}</td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                      ) : (
                        <div className="flex flex-col items-center justify-center h-full text-gray-400 py-20 gap-2">
                          <Sheet className="size-8 text-gray-300" />
                          <span className="text-xs font-medium">无匹配的文件数据</span>
                        </div>
                      )
                    ) : (
                      <div className="flex flex-col items-center justify-center h-full text-gray-400 py-20 gap-2">
                        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-database size-8 text-gray-300">
                          <ellipse cx="12" cy="5" rx="9" ry="3"></ellipse>
                          <path d="M3 5V19A9 3 0 0 0 21 19V5"></path>
                          <path d="M3 12A9 3 0 0 0 21 12"></path>
                        </svg>
                        <span className="text-xs font-medium">暂未配置数据源，请添加</span>
                      </div>
                    )}
                  </div>
                </div>

                <div className="flex items-center justify-between mt-5 pt-3 border-t border-gray-100">
                  <div className="flex-1"></div>
                  <div className="flex gap-2">
                    <button 
                      type="button"
                      onClick={() => setIsSelectDataOpen(false)}
                      className="inline-flex items-center justify-center rounded-xl text-sm font-medium border border-gray-200 bg-white hover:bg-gray-50 h-9 px-4 cursor-pointer text-gray-700 transition-colors"
                    >
                      取消
                    </button>
                    <button 
                      type="button"
                      disabled={selectedCategory === 'file' && selectedFilesInModal.length === 0}
                      onClick={() => {
                        if (selectedCategory === 'file') {
                          setAttachedFiles(selectedFilesInModal);
                          if (selectedFilesInModal.length > 0) {
                            setInputValue(`请帮我结合这份游戏数据，洞察并产出策略类游戏的营销策略`);
                          }
                          setIsSelectDataOpen(false);
                        }
                      }}
                      className={clsx(
                        "inline-flex items-center justify-center rounded-xl text-sm font-medium h-9 px-5 min-w-16 cursor-pointer transition-colors",
                        (selectedCategory === 'file' && selectedFilesInModal.length > 0)
                          ? "bg-[#2D336B] text-white hover:bg-[#202550]"
                          : "bg-gray-100 text-gray-400 cursor-not-allowed"
                      )}
                    >
                      确认
                    </button>
                  </div>
                </div>

              </div>
            </div>
            
            <Dialog.Close asChild>
              <button 
                type="button" 
                className="absolute right-4 top-4 rounded-lg opacity-70 hover:opacity-100 text-gray-400 hover:bg-gray-100 p-1.5 cursor-pointer transition-all"
                aria-label="Close"
              >
                <X className="size-4" />
              </button>
            </Dialog.Close>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>

      {/* 全屏数据预览浮层 */}
      {fullscreenPreviewFile && (() => {
        const preview = getPreviewData(fullscreenPreviewFile.name);
        return (
          <div className="fixed inset-0 z-[60] bg-white flex flex-col p-6 overflow-hidden select-text animate-in fade-in duration-200">
            <div className="flex items-center justify-between mb-4 flex-none">
              <span className="text-sm text-gray-500 font-medium">{fullscreenPreviewFile.name}</span>
              <button 
                onClick={() => setFullscreenPreviewFile(null)}
                className="text-gray-400 hover:text-gray-600 hover:bg-gray-100 p-1.5 rounded-lg transition-colors cursor-pointer"
                type="button"
              >
                <X className="size-5" />
              </button>
            </div>
            
            <div className="flex-1 overflow-auto border border-gray-250/60 rounded-xl">
              <table className="w-full text-left border-collapse text-xs leading-relaxed">
                <thead className="sticky top-0 bg-gray-50/90 backdrop-blur-xs z-10 border-b border-gray-200 select-none">
                  <tr>
                    {preview.columns.map((col: string) => (
                      <th key={col} className="px-4 py-2.5 font-bold text-gray-500 whitespace-nowrap border-r border-gray-100 last:border-r-0">
                        {col}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 bg-white">
                  {preview.rows.map((row: any, rIdx: number) => (
                    <tr key={rIdx} className="hover:bg-gray-50/50 transition-colors">
                      {preview.columns.map((col: string) => (
                        <td key={col} className="px-4 py-2 text-gray-700 font-medium whitespace-nowrap border-r border-gray-100 last:border-r-0">
                          {row[col]}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        );
      })()}
    </div>
  );
};

export default Home;
