import React, { useState, useEffect, useRef } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Settings2, ArrowUp, RefreshCcw, X, Plus, BookOpen, Atom, ChevronDown, Sheet, Maximize2, Upload, Plug, ChevronRight, Check, Trophy, Sparkles, Search, Database } from 'lucide-react';
import clsx from 'clsx';
import * as HoverCard from '@radix-ui/react-hover-card';
import * as Tooltip from '@radix-ui/react-tooltip';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import * as Dialog from '@radix-ui/react-dialog';
import { MOCK_PREVIEW_DATA, INITIAL_FILES } from '../DataCenter/mockData';
import { InteractiveReport } from './components/InteractiveReport';

// 消息 Block 数据结构
interface MessageBlock {
  type: 'text' | 'json' | 'python' | 'sql' | 'markdown-report' | 'result_set';
  content: string;
}

interface Message {
  role: 'user' | 'assistant';
  content?: string;
  type?: 'text' | 'data';
  data?: any;
  blocks?: MessageBlock[];
  isComplete?: boolean;
}

const getPreviewData = (fileName: string) => {
  if (fileName.includes('游戏')) return MOCK_PREVIEW_DATA.game;
  if (fileName.includes('餐厅')) return MOCK_PREVIEW_DATA.restaurant;
  if (fileName.includes('信用卡')) return MOCK_PREVIEW_DATA.credit;
  return MOCK_PREVIEW_DATA.default;
};

// ================= 子组件：通用的可折叠卡片 (CollapsibleCard) =================
interface CollapsibleCardProps {
  title: string;
  icon?: React.ReactNode;
  status?: 'pending' | 'running' | 'success';
  defaultOpen?: boolean;
  children: React.ReactNode;
}

const CollapsibleCard: React.FC<CollapsibleCardProps> = ({ 
  title, 
  icon, 
  status = 'success', 
  defaultOpen = true, 
  children 
}) => {
  const [isOpen, setIsOpen] = useState(defaultOpen);

  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-2xs my-3 overflow-hidden transition-all duration-200 w-full max-w-[620px] select-none">
      <div 
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center justify-between px-4 py-3 bg-[#FAFAFC] cursor-pointer hover:bg-gray-100/40 transition-colors border-b border-gray-150"
      >
        <div className="flex items-center gap-2.5">
          {icon}
          <span className="text-xs font-bold text-gray-800">{title}</span>
        </div>
        <div className="flex items-center gap-3">
          {status === 'success' && (
            <span className="text-[10px] bg-green-50 text-green-600 font-bold border border-green-100 px-2 py-0.5 rounded-md flex items-center gap-1 select-none">
              <Check className="size-3 stroke-[3]" /> 已完成
            </span>
          )}
          {status === 'running' && (
            <span className="text-[10px] bg-indigo-50 text-indigo-600 font-bold border border-indigo-100 px-2 py-0.5 rounded-md flex items-center gap-1 select-none">
              <span className="size-1.5 rounded-full bg-indigo-600 animate-ping"></span> 执行中
            </span>
          )}
          <ChevronRight className={clsx("w-4 h-4 text-gray-400 transition-transform duration-200", isOpen && "rotate-90")} />
        </div>
      </div>
      {isOpen && (
        <div className="p-4 bg-white border-t border-gray-50 text-xs text-gray-600 animate-in fade-in slide-in-from-top-1 duration-150">
          {children}
        </div>
      )}
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

const WorkflowPlan: React.FC<{ planJson: string; currentBlockCount: number }> = ({ planJson, currentBlockCount }) => {
  let plan: Plan | null = null;
  try {
    const cleanJson = planJson.replace('$$$json', '').trim();
    if (cleanJson.startsWith('{') && cleanJson.endsWith('}')) {
      plan = JSON.parse(cleanJson);
    }
  } catch (e) {
    // 捕获正在流式拼接 JSON 时的解析失败
  }

  if (!plan || !Array.isArray(plan.execution_plan)) {
    return (
      <div className="bg-gray-50/50 border border-gray-150 rounded-xl p-4 animate-pulse text-xs text-gray-400 font-semibold w-full max-w-[620px]">
        正在构建工作流执行计划...
      </div>
    );
  }

  return (
    <div className="space-y-4 w-full">
      <p className="text-[11.5px] text-gray-500 mb-4 bg-gray-50/80 p-2.5 rounded-lg border border-gray-100 leading-normal">
        <strong>分析思路</strong>：{plan.thought_process}
      </p>
      
      <div className="space-y-4">
        {plan.execution_plan?.map((step, idx) => {
          let status: 'pending' | 'running' | 'success' = 'pending';
          if (currentBlockCount > 1) {
            if (idx === 0) {
              status = currentBlockCount > 3 ? 'success' : 'running';
            } else if (idx === 1) {
              status = currentBlockCount > 5 ? 'success' : (currentBlockCount > 3 ? 'running' : 'pending');
            } else if (idx === 2) {
              status = currentBlockCount > 6 ? 'success' : (currentBlockCount > 5 ? 'running' : 'pending');
            }
          } else {
            if (idx === 0) status = 'running';
          }

          return (
            <div key={idx} className="flex gap-3 relative">
              {idx < plan!.execution_plan.length - 1 && (
                <div className="absolute left-[9px] top-5 w-[2px] h-9 bg-gray-100"></div>
              )}
              <div className="shrink-0 size-5 rounded-full flex items-center justify-center border text-[10px] font-bold z-10 transition-colors">
                {status === 'success' ? (
                  <div className="size-full rounded-full bg-green-500 border border-green-600 flex items-center justify-center text-white">
                    <Check className="size-3 stroke-[3]" />
                  </div>
                ) : status === 'running' ? (
                  <div className="size-full rounded-full bg-indigo-50 border border-indigo-600 flex items-center justify-center text-indigo-600">
                    <span className="size-1.5 rounded-full bg-indigo-600 animate-ping"></span>
                  </div>
                ) : (
                  <div className="size-full rounded-full bg-white border-gray-250 text-gray-400 flex items-center justify-center">
                    {step.step}
                  </div>
                )}
              </div>
              <div className="space-y-0.5 select-text">
                <span className="text-[12.5px] font-bold text-gray-800 block">
                  {step.tool_to_use === 'sql_generate' && "SQL 数据抽取与汇总"}
                  {step.tool_to_use === 'python_generate' && "Python 数据清洗与统计建模"}
                  {step.tool_to_use === 'report_generator' && "整合营销报告输出"}
                  {!['sql_generate', 'python_generate', 'report_generator'].includes(step.tool_to_use) && step.tool_to_use}
                </span>
                <span className="text-[11px] text-gray-400 leading-normal block">
                  {step.tool_parameters?.instruction || step.tool_parameters?.summary_and_recommendations}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

// ================= 子组件：SQL/Python 代码框 =================
const CodeBlock: React.FC<{ language: 'sql' | 'python'; code: string }> = ({ language, code }) => {
  const [copied, setCopied] = useState(false);
  const handleCopy = () => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="my-1 rounded-xl border border-gray-150 bg-[#FAFAFC] overflow-hidden w-full font-mono text-[12px] leading-relaxed select-text shadow-2xs">
      <div className="flex items-center justify-between px-4 py-2 bg-gray-100/60 border-b border-gray-150 select-none">
        <span className="text-[10px] font-bold text-gray-500 uppercase tracking-wider">{language} 代码</span>
        <button 
          onClick={handleCopy}
          className="text-[10px] text-indigo-600 hover:text-indigo-800 font-bold border border-indigo-200 rounded px-2 py-0.5 hover:bg-indigo-50 cursor-pointer active:scale-95 transition-all"
        >
          {copied ? "已复制" : "复制"}
        </button>
      </div>
      <pre className="p-4 overflow-x-auto text-gray-700 whitespace-pre">
        <code>{code}</code>
      </pre>
    </div>
  );
};

// ================= 子组件：SQL 执行结果集表格 =================
const ResultSetTable: React.FC<{ dataJson: string }> = ({ dataJson }) => {
  let rows: any[] = [];
  let columns: string[] = [];
  try {
    const cleanJson = dataJson.replace('$$$result_set', '').trim();
    if (cleanJson.startsWith('[') && cleanJson.endsWith(']')) {
      rows = JSON.parse(cleanJson);
      if (rows.length > 0) {
        columns = Object.keys(rows[0]);
      }
    }
  } catch (e) {
    // 捕获流式拼装 JSON 时的解析报错
  }

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
              disabled={addType === 'upload' && !selectedFile}
              className={clsx(
                "h-8 px-4 font-bold rounded-lg transition-all active:scale-[0.98] cursor-pointer border-none text-[12px]",
                (addType === 'upload' && !selectedFile)
                  ? "bg-gray-250 text-gray-400 cursor-not-allowed"
                  : "bg-indigo-600 hover:bg-indigo-700 text-white shadow-2xs"
              )}
            >
              确认并添加数据
            </button>
          </div>
        </div>
      )}

      {/* 小黄提示条 (官网样式) */}
      {reply && (
        <div className="flex w-fit items-center rounded-2xl bg-[#FFF9E6] px-3.5 py-1.5 text-xs text-[#8A6D1C] font-semibold mt-3.5 shadow-3xs select-none animate-in fade-in duration-300">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="mr-1.5 size-3.5"><path d="M12 3v17a1 1 0 0 1-1 1H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v6a1 1 0 0 1-1 1H3"></path><path d="M16 19h6"></path><path d="M19 22v-6"></path></svg>
          我将在你反馈后继续
        </div>
      )}
    </div>
  );
};

// ================= 核心算法：累加流式文本全局解析器 =================
function parseRawContent(raw: string): MessageBlock[] {
  const blocks: MessageBlock[] = [];
  let index = 0;

  const markers = [
    { sign: '$$$json', type: 'json' as const },
    { sign: '$$$python', type: 'python' as const },
    { sign: '$$$sql', type: 'sql' as const },
    { sign: '$$$markdown-report', type: 'markdown-report' as const },
    { sign: '$$$result_set', type: 'result_set' as const },
  ];

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
      const rest = raw.substring(index);
      if (rest) {
        blocks.push({ type: 'text', content: rest });
      }
      break;
    }

    if (closestPos > index) {
      const text = raw.substring(index, closestPos);
      if (text) {
        blocks.push({ type: 'text', content: text });
      }
    }

    const endSign = closestMarker.type === 'markdown-report' ? '$$$/markdown-report' : '$$$';
    const startOfContent = closestPos + closestMarker.sign.length;
    const endPos = raw.indexOf(endSign, startOfContent);

    if (endPos === -1) {
      const content = raw.substring(startOfContent);
      blocks.push({ type: closestMarker.type, content: content });
      break;
    }

    const content = raw.substring(startOfContent, endPos);
    blocks.push({ type: closestMarker.type, content: content });
    index = endPos + endSign.length;
  }

  return blocks;
}

// ================= 核心算法：文本段的去重与美化组件 =================
const ProcessedTextBlock: React.FC<{ text: string }> = ({ text }) => {
  const trimmed = text.trim();
  if (!trimmed) return null;

  // 将后端中常见的连带输出进行去重处理
  let display = trimmed;
  if (trimmed.includes('查询重写完成')) {
    display = '查询重写完成，已优化查询意图。';
  } else if (trimmed.includes('未找到证据')) {
    display = '知识库未检索到直接匹配条目，将直接依据数据库执行。';
  }

  // 1. 导入数据可折叠
  if (trimmed.includes('导入数据') || trimmed.includes('成功加载本地')) {
    return (
      <CollapsibleCard 
        title="导入数据" 
        icon={<Sheet className="text-blue-500 size-4 shrink-0" />}
        status="success"
        defaultOpen={false}
      >
        <div className="text-[11.5px] leading-relaxed">已成功加载预设的游戏数据文件，建立临时宽表并解析元数据 schema 完毕。</div>
      </CollapsibleCard>
    );
  }

  // 2. 检索知识库可折叠
  if (trimmed.includes('检索知识库') || trimmed.includes('正在检索知识库')) {
    return (
      <CollapsibleCard 
        title="正在检索知识库" 
        icon={<BookOpen className="text-indigo-500 size-4 shrink-0" />}
        status="success"
        defaultOpen={false}
      >
        <div className="text-[11.5px] leading-relaxed">知识库检索完成。已召回相关策略游戏行业销量阈值、评分拐点与长尾口碑收益说明文档。</div>
      </CollapsibleCard>
    );
  }

  // 3. 联网搜索可折叠
  if (trimmed.includes('联网搜索') || trimmed.includes('联网搜索中')) {
    return (
      <CollapsibleCard 
        title="正在联网搜索" 
        icon={<Search className="text-purple-500 size-4 shrink-0" />}
        status="success"
        defaultOpen={false}
      >
        <div className="text-[11.5px] leading-relaxed">联网搜索匹配完毕。获取到近期全球策略游戏市场份额和各大平台的最新营销费用结构对比。</div>
      </CollapsibleCard>
    );
  }

  // 4. 重写等中间状态提示
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

// ================= 主视图组件 Home =================
const Home: React.FC = () => {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const fileInputRef = useRef<HTMLInputElement>(null);
  
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [showSkillBanner, setShowSkillBanner] = useState(true);
  const [attachedFiles, setAttachedFiles] = useState<any[]>([]);
  const [fullscreenPreviewFile, setFullscreenPreviewFile] = useState<any | null>(null);
  const [selectedKbs, setSelectedKbs] = useState<string[]>([]);
  const [kbSearchQuery, setKbSearchQuery] = useState('');
  const [selectedMcps, setSelectedMcps] = useState<string[]>([]);
  const [mcpSearchQuery, setMcpSearchQuery] = useState('');
  
  // 深度模式与报告抽屉状态
  const [isDepthMode, setIsDepthMode] = useState(true);
  const [isReportOpen, setIsReportOpen] = useState(false);

  // 指定库表进行分析的弹窗状态
  const [isSelectDataOpen, setIsSelectDataOpen] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<'file' | 'db'>('file');
  const [modalSearchQuery, setModalSearchQuery] = useState('');
  const [selectedFilesInModal, setSelectedFilesInModal] = useState<any[]>([]);

  // 路由挂载的 Chat 状态检查
  const isChatState = !!sessionId;

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

  const demos = [
    { type: '企业', title: '餐厅销售分析', tagColor: 'bg-blue-50 text-blue-600', img: 'https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/card-1.svg', prompt: '帮我做一份餐厅销售情况 analysis' },
    { type: '游戏', title: '发行游戏分析', tagColor: 'bg-indigo-50 text-indigo-600', img: 'https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/card-2.svg', prompt: '请帮我结合这份游戏数据，洞察并产出策略类游戏的营销策略' },
    { type: '电商', title: '电商分析', tagColor: 'bg-purple-50 text-purple-600', img: 'https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/card-3.svg', prompt: '请帮我生成一份本季度的电商运营分析' },
    { type: '金融', title: '信用卡反欺诈分析', tagColor: 'bg-blue-50 text-blue-600', img: 'https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/card-4.svg', prompt: '帮我分析信用卡的异常交易和欺诈风险' },
    { type: '教育', title: '学生成绩分析', tagColor: 'bg-green-50 text-green-600', img: 'https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/card-5.svg', prompt: '请帮我分析本学期期末考试的学生成绩分布' },
    { type: '企业', title: '员工薪资分析', tagColor: 'bg-indigo-50 text-indigo-600', img: 'https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/card-6.svg', prompt: '帮我看一下去年员工的薪资水平 and 分布' },
  ];

  // ================= 核心流式发送逻辑 =================
  const handleSend = async (text: string) => {
    if (!text.trim()) return;

    let currentSessionId = sessionId;
    if (!currentSessionId) {
      currentSessionId = 'session_' + Date.now();
      navigate(`/chat/${currentSessionId}`, { replace: true });
    }

    const userMsg: Message = { role: 'user', content: text };
    setMessages(prev => [...prev, userMsg]);
    setInputValue('');
    setAttachedFiles([]);
    setSelectedKbs([]);
    setSelectedMcps([]);
    setIsTyping(true);

    // 预填空白 AI message，让解析器在后续直接更新此 message 的 blocks
    const assistantMsg: Message = { 
      role: 'assistant', 
      blocks: [], 
      isComplete: false 
    };
    setMessages(prev => [...prev, assistantMsg]);

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
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          agentId: 'default',
          threadId: currentSessionId,
          query: text,
          nl2sqlOnly: !isDepthMode,
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

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        const rawText = decoder.decode(value, { stream: true });
        streamBuffer += rawText;

        const lines = streamBuffer.split('\n');
        streamBuffer = lines.pop() || '';

        for (const line of lines) {
          const trimmedLine = line.trim();
          if (!trimmedLine) continue;

          if (trimmedLine.startsWith('data:')) {
            const content = trimmedLine.substring(5);
            accumulatedRaw += content;
            updateBlocks(accumulatedRaw);
          }
        }
      }
      markStreamComplete();

    } catch (e) {
      console.warn("自动进入流式高保真 Mock 演示环境", e);
      // 优雅流式 Mock，提供极致的可视化演示
      setTimeout(() => {
        setIsTyping(false);
        
        // 1. 模拟导入数据
        accumulatedRaw += "正在导入数据...\n";
        updateBlocks(accumulatedRaw);

        setTimeout(() => {
          // 2. 模拟检索知识库与重写
          accumulatedRaw += "正在检索知识库...\n查询重写完成！\n";
          updateBlocks(accumulatedRaw);

          setTimeout(() => {
            // 3. 模拟构建计划
            accumulatedRaw += "$$$json\n" + JSON.stringify({
              thought_process: "根据用户的分析要求，本系统将规划3个步骤，首先查询该类型游戏在全球和主要市场的销量数据，之后使用Python对口碑评分和销量的关系进行模型评估，最后整合输出策略网页报告。",
              execution_plan: [
                { step: 1, tool_to_use: "sql_generate", tool_parameters: { instruction: "查询该游戏数据集中不同策略游戏的全球销量与主要区域分布占比" } },
                { step: 2, tool_to_use: "python_generate", tool_parameters: { instruction: "使用 Python 的回归模型对媒体分与销量进行相关性及拐点阈值量化" } },
                { step: 3, tool_to_use: "report_generator", tool_parameters: { summary_and_recommendations: "根据提取的销量占比与口碑阈值爆发模型，编写双阶段的整合营销策略网页报告" } }
              ]
            }, null, 2) + "\n$$$\n";
            updateBlocks(accumulatedRaw);

            setTimeout(() => {
              // 4. 模拟 SQL 块
              accumulatedRaw += "\n已经为您成功拆解工作流计划，正在进入 **第一步：SQL 数据提取与汇总**。\n$$$sql\nSELECT \n  genre, \n  sum(na_sales) AS na_sales_vol,\n  sum(eu_sales) AS eu_sales_vol,\n  sum(jp_sales) AS jp_sales_vol,\n  sum(global_sales) AS global_sales_vol\nFROM内置_游戏数据 \nWHERE genre = 'Strategy'\nGROUP BY 1;\n$$$\n";
              updateBlocks(accumulatedRaw);

              setTimeout(() => {
                // 5. 模拟结果集表格
                accumulatedRaw += "\n正在发送 SQL 至底层数据库执行，拉取结果集完毕：\n$$$result_set\n" + JSON.stringify([
                  { "区域": "北美市场(NA)", "销量(亿套)": "1.86", "销量占比": "48.5%" },
                  { "区域": "欧洲市场(EU)", "销量(亿套)": "1.12", "销量占比": "29.2%" },
                  { "区域": "日本市场(JP)", "销量(亿套)": "0.47", "销量占比": "12.3%" },
                  { "区域": "其他区域(Other)", "销量(亿套)": "0.39", "销量占比": "10.0%" }
                ], null, 2) + "\n$$$\n";
                updateBlocks(accumulatedRaw);

                setTimeout(() => {
                  // 6. 模拟 Python 块
                  accumulatedRaw += "\n第一步执行完毕。现在正在进入 **第二步：Python 评分-销量模型分析**，探究媒体分与最终销量的回归关系：\n$$$python\nimport matplotlib.pyplot as plt\nimport numpy as np\nx_scores = np.array([70, 75, 80, 85, 90, 95])\ny_sales = np.array([15, 25, 45, 120, 310, 580])\np = np.polyfit(x_scores, np.log(y_sales), 1)\nprint(f'口碑增长系数：{p[0]:.4f}，当评分突破85时单作销量爆发比达4.8倍。')\n$$$\n";
                  updateBlocks(accumulatedRaw);

                  setTimeout(() => {
                    // 7. 最终 Markdown 报告
                    accumulatedRaw += "\n第二步 Python 建模已完成。现在进入 **第三步：整合营销报告输出**。正在综合以上分析绘制大图景报告：\n$$$markdown-report\n# 策略类游戏营销策略洞察报告\n\n通过对数据集内策略类（Strategy）游戏的表现汇总，我们得到以下核心洞察与策略推荐：\n\n## 1. 区域市场定位\n* **北美（48.5%）** 与 **欧洲（29.2%）** 是销量基本盘，合占接近 **80%** 市场。营销预算应重点投放给欧美垂直策略博主。\n* **日本（12.3%）** 市场规模相对适中但玩家极度忠诚，适合走社群口碑发酵。\n\n## 2. 核心拐点阈值与口碑营销\n* 本研究在数据中验证了 **85分口碑红利壁垒**。当评分为 80 分时，单作销量处于温和状态；一旦评分超越 85 分，全球销量将呈现高达 **4.8 倍** 的指数级增长，长尾效用极其强大！\n* 推广预算中需配置足够比例进行先遣媒体沟通与打分质量护航。\n\n---\n您可以点击下方卡片的 **“绘制网页”**，通过更酷炫的交互式网页图表探索完整的营销策略大图景！\n$$$/markdown-report\n";
                    updateBlocks(accumulatedRaw);
                    markStreamComplete();
                  }, 1200);
                }, 1000);
              }, 1200);
            }, 800);
          }, 800);
        }, 800);
      }, 300);
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
          <button className="gap-2 whitespace-nowrap rounded-md py-2 justify-between h-7 w-auto border-0 bg-transparent px-2 text-sm font-normal text-gray-700 hover:bg-gray-200/50 flex items-center overflow-hidden" type="button">
            <div className="flex flex-1 items-center gap-1 truncate text-gray-800">
              <Atom className="w-4 h-4 text-gray-500" />
              <span className="flex-1 truncate font-medium">Data Agent</span>
            </div>
            <ChevronDown className="w-4 h-4 text-zinc-400 flex-none ml-1" />
          </button>
        </span>
        
        {/* 清除/新对话按钮 (仅在对话状态下显示) */}
        {isChatState && (
          <button 
            onClick={() => {
              setIsReportOpen(false);
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
      <div className={clsx("flex flex-row w-full h-full relative overflow-hidden flex-1", isChatState ? "pb-36" : "")}>
        <div className={clsx("flex-1 flex flex-col h-full items-center overflow-y-auto relative min-w-0 transition-all duration-300", isReportOpen ? "pr-[580px]" : "")}>
          
          {/* 🚀 消息流渲染 */}
          {isChatState ? (
            <div className="w-[680px] min-w-[680px] flex flex-col gap-6 pt-10 pb-16 px-1 flex-1">
              {messages.map((msg, idx) => {
                if (msg.role === 'user') {
                  return (
                    <div 
                      key={idx} 
                      className="flex w-full justify-end animate-in fade-in slide-in-from-bottom-2 duration-300 select-text"
                    >
                      <div className="bg-[#F1F1FE] max-w-[80%] rounded-lg px-3 py-2 text-[#0A0A0B] break-words text-sm font-normal leading-6 shadow-3xs">
                        <p className="whitespace-pre-line m-0">{msg.content}</p>
                      </div>
                    </div>
                  );
                }

                // msg.role === 'assistant' 渲染（官网三行无气泡排版样式）
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
                      <span className="font-dm-sans text-[14px] font-semibold text-gray-800 ml-3">Data Agent</span>
                    </div>

                    {/* 第二行：消息内容主体（去掉白底卡片气泡，普通文字直接平铺，Blocks 自身带卡片） */}
                    <div className="pl-[38px] w-full flex flex-col items-start gap-2 break-words leading-7 text-gray-800 text-[14px]">
                      {/* 普通消息内容 */}
                      {msg.content && <div className="font-normal whitespace-pre-wrap">{msg.content}</div>}

                      {/* 流式 Blocks 精细化可折叠卡片渲染 */}
                      {msg.blocks && msg.blocks.map((block, bIdx) => {
                        if (block.type === 'text') {
                          return <ProcessedTextBlock key={bIdx} text={block.content} />;
                        }
                        if (block.type === 'json') {
                          // 判断是否为闲聊意图 JSON (匹配 "classification" 或 "reply")
                          const isSmalltalk = block.content.includes('"classification"') || block.content.includes('"reply"');
                          if (isSmalltalk) {
                            const replyText = extractReplyFromIncrementalJson(block.content);
                            return (
                              <SmalltalkDataPanel 
                                key={bIdx}
                                reply={replyText} 
                                latestQuery={messages[idx - 1]?.content || ""} 
                                onConfirmData={(file) => {
                                  setAttachedFiles(prev => [...prev, file]);
                                  setInputValue(prev => prev || `请帮我结合这份数据，进行智能数据分析`);
                                }}
                              />
                            );
                          }

                          // 否则作为执行计划卡片渲染
                          return (
                            <CollapsibleCard 
                              key={bIdx}
                              title="工作流执行计划" 
                              icon={<BookOpen className="text-indigo-600 size-4 shrink-0" />}
                              status={msg.blocks && msg.blocks.length > 6 ? 'success' : 'running'}
                              defaultOpen={true}
                            >
                              <WorkflowPlan planJson={block.content} currentBlockCount={msg.blocks?.length || 0} />
                            </CollapsibleCard>
                          );
                        }
                        if (block.type === 'sql') {
                          return (
                            <CollapsibleCard
                              key={bIdx}
                              title="生成 SQL 数据查询"
                              icon={<Database className="text-blue-500 size-4 shrink-0" />}
                              status="success"
                              defaultOpen={false}
                            >
                              <CodeBlock language="sql" code={block.content} />
                            </CollapsibleCard>
                          );
                        }
                        if (block.type === 'python') {
                          return (
                            <CollapsibleCard
                              key={bIdx}
                              title="建立 Python 回归分析模型"
                              icon={<Atom className="text-purple-500 size-4 shrink-0 animate-spin-slow" />}
                              status="success"
                              defaultOpen={false}
                            >
                              <CodeBlock language="python" code={block.content} />
                            </CollapsibleCard>
                          );
                        }
                        if (block.type === 'result_set') {
                          return (
                            <CollapsibleCard
                              key={bIdx}
                              title="数据查询结果集"
                              icon={<Sheet className="text-indigo-500 size-4 shrink-0" />}
                              status="success"
                              defaultOpen={true}
                            >
                              <ResultSetTable dataJson={block.content} />
                            </CollapsibleCard>
                          );
                        }
                        if (block.type === 'markdown-report') {
                          const reportText = block.content.replace('$$$/markdown-report', '');
                          return (
                            <div key={bIdx} className="my-2 select-text w-full leading-relaxed border-l-2 border-indigo-500 pl-4 py-1 bg-gray-50/40 rounded-r-xl border border-gray-100 pr-3">
                              <h3 className="text-sm font-bold text-gray-800 mb-2 border-b border-gray-200/50 pb-1">报告生成</h3>
                              <div className="prose text-xs text-gray-600 space-y-2 whitespace-pre-wrap">
                                {reportText}
                              </div>
                            </div>
                          );
                        }
                        return null;
                      })}

                      {/* 流式完成且包含报告，渲染“绘制网页报告分享卡片” */}
                      {msg.isComplete && msg.blocks?.some(b => b.type === 'markdown-report') && (
                        <div className="my-4 border border-indigo-100 bg-indigo-50/40 rounded-xl p-4 flex items-center justify-between shadow-2xs w-full max-w-[620px] select-none animate-in fade-in slide-in-from-top-1 duration-200">
                          <div className="flex items-center gap-2.5">
                            <Trophy className="w-5 h-5 text-indigo-600 flex-none" />
                            <div className="space-y-0.5">
                              <span className="text-[13px] font-bold text-gray-800 block">以交互式网页报告分享 Data Agent 的分析</span>
                              <span className="text-[11px] text-gray-400 block font-medium">包含销量、口碑建模、平台生态的完整大图景报告</span>
                            </div>
                          </div>
                          <div className="flex items-center gap-2 flex-none">
                            <button 
                              onClick={() => alert('已取消')}
                              className="inline-flex items-center justify-center border border-gray-200 bg-white hover:bg-gray-50 px-3 py-1.5 text-xs font-semibold rounded-lg text-gray-600 transition-colors cursor-pointer active:scale-95"
                            >
                              取消
                            </button>
                            <button 
                              onClick={() => setIsReportOpen(true)}
                              className="inline-flex items-center justify-center bg-indigo-600 hover:bg-indigo-700 px-4.5 py-1.5 text-xs font-bold rounded-lg text-white shadow-sm transition-colors cursor-pointer active:scale-95"
                            >
                              绘制网页
                            </button>
                          </div>
                        </div>
                      )}
                    </div>

                    {/* 第三行：工具栏与时间 (赞、踩) */}
                    <div className="pl-[38px] flex items-center gap-2 mt-2 select-none">
                      <div className="flex items-center gap-1 transition-opacity duration-200">
                        <button className="inline-flex items-center justify-center p-1 text-gray-400 hover:text-gray-600 hover:bg-gray-200/50 rounded cursor-pointer size-6 transition-all border-none bg-transparent">
                          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-thumbs-up"><path d="M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z"/><path d="M7 10v12"/></svg>
                        </button>
                        <button className="inline-flex items-center justify-center p-1 text-gray-400 hover:text-gray-600 hover:bg-gray-200/50 rounded cursor-pointer size-6 transition-all border-none bg-transparent">
                          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="lucide lucide-thumbs-down"><path d="M9 18.12 10 14H4.17a2 2 0 0 1-1.92-2.56l2.33-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.76a2 2 0 0 0-1.79 1.11L12 22a3.13 3.13 0 0 1-3-3.88Z"/><path d="M17 14V2"/></svg>
                        </button>
                      </div>
                      <span className="text-[11px] text-gray-400 font-medium">
                        {new Date().toLocaleDateString('zh-CN')} {new Date().toLocaleTimeString('zh-CN', { hour12: false })}
                      </span>
                    </div>
                  </div>
                );
              })}

              {isTyping && (
                <div className="group relative flex flex-col w-full py-4 animate-pulse">
                  {/* 第一行：头像与名称 */}
                  <div className="flex h-[26px] items-center mb-2 select-none">
                    <span className="relative shrink-0 rounded-full flex h-[26px] w-[26px] items-center overflow-hidden">
                      <img className="h-full w-full aspect-auto animate-spin" alt="data-agent" src="https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/logo-black.svg" />
                    </span>
                    <span className="font-dm-sans text-[14px] font-semibold text-gray-800 ml-3">Data Agent</span>
                  </div>
                  {/* 第二行：消息体内容 */}
                  <div className="pl-[38px] w-full flex items-center gap-1.5 text-gray-500 text-[13px] font-medium">
                    <span>正在深度分析数据</span>
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
                  Hola, I'm <span className="bg-clip-text text-transparent bg-gradient-to-r from-[#6b73ff] to-[#000dff] font-bold opacity-90">Data Agent</span>
                </h1>
                <p className="text-gray-500 w-full overflow-hidden text-2xl font-light">
                  从数据到洞察，Agent驱动的数据价值放大器
                </p>
              </div>
            </div>
          )}

          {/* 底部输入框外壳 */}
          <div 
            className={clsx(
              "flex-none z-20 transition-all duration-300",
              isChatState 
                ? (isReportOpen 
                    ? "fixed bottom-0 left-[236px] w-[calc(100vw-236px-580px)] flex justify-center pb-6 pt-4 bg-[#F6F6F6]/95 backdrop-blur-xs px-4" 
                    : "fixed bottom-0 left-[236px] right-0 mx-auto w-[680px] pb-6 pt-4 bg-gradient-to-t from-[#F6F6F6] via-[#F6F6F6] to-transparent"
                  )
                : "w-[680px] mb-6"
            )}
          >
            <div className="group/composer flex w-full flex-col items-start justify-center bg-[#ECEEF6] rounded-3xl p-1 z-10 shadow-sm">
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
                className="border-border shadow-sm flex w-full flex-col rounded-3xl border border-gray-100 bg-white p-5 transition-colors ease-in focus-within:border-gray-200"
              >
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
                                      {preview.columns.map((col) => (
                                        <th key={col} className="px-3 py-2 font-bold text-gray-500 whitespace-nowrap border-r border-gray-100 last:border-r-0">
                                          {col}
                                        </th>
                                      ))}
                                    </tr>
                                  </thead>
                                  <tbody className="divide-y divide-gray-100">
                                    {preview.rows.map((row: any, rIdx: number) => (
                                      <tr key={rIdx} className="hover:bg-gray-50/50 transition-colors">
                                        {preview.columns.map((col) => (
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
                  </div>
                )}
                <textarea 
                  placeholder="通过下方指定一份数据并给我布置数据分析任务，'shift+enter'换行"
                  value={inputValue}
                  onChange={(e) => setInputValue(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault();
                      handleSend(inputValue);
                    }
                  }}
                  className="max-h-120 flex-grow resize-none border-none bg-transparent outline-none placeholder:text-gray-400/80 min-h-[76px] w-full px-0 py-2 focus:outline-none focus:ring-0 focus-visible:ring-0 text-[14px] text-[#0A0A0B] leading-relaxed font-sans"
                />
                <input 
                  type="file" 
                  ref={fileInputRef} 
                  onChange={handleFileChange} 
                  className="hidden" 
                  accept=".csv,.xlsx,.xls"
                />
                
                <div className="mt-6 flex items-center gap-2">
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
                  
                  {/* 深度模式开关 */}
                  <button 
                    onClick={() => setIsDepthMode(prev => !prev)}
                    className={clsx(
                      "justify-center whitespace-nowrap font-medium flex h-7 w-auto items-center gap-1 rounded-2xl px-2.5 py-1 text-xs transition-colors cursor-pointer active:scale-95 border-none",
                      isDepthMode 
                        ? "bg-indigo-50 border border-indigo-200 text-indigo-600 font-semibold" 
                        : "text-gray-600 hover:bg-gray-50"
                    )}
                    type="button"
                  >
                    <Atom className={clsx("w-4 h-4 flex-none text-indigo-500", isDepthMode && "animate-spin-slow")} />
                    <span>深度模式</span>
                  </button>

                  <div className="flex flex-1 items-center justify-end">
                    <button 
                      type="submit"
                      className="inline-flex items-center justify-center gap-2 whitespace-nowrap font-medium size-7 cursor-pointer rounded-full p-0 bg-gray-900 text-white hover:bg-gray-800 transition-colors"
                    >
                      <ArrowUp className="w-4 h-4 stroke-[2.5]" />
                    </button>
                  </div>
                </div>
              </form>

              {/* 🏡 底部下载提示条 */}
              {!isChatState && showSkillBanner && (
                <div data-testid="landing-skill-banner" className="flex w-full items-center gap-3 px-4 py-2.5">
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

        {/* 🚀 右侧分屏网页报告组件 */}
        <InteractiveReport isOpen={isReportOpen} onClose={() => setIsReportOpen(false)} />
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
                    {preview.columns.map((col) => (
                      <th key={col} className="px-4 py-2.5 font-bold text-gray-500 whitespace-nowrap border-r border-gray-100 last:border-r-0">
                        {col}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 bg-white">
                  {preview.rows.map((row: any, rIdx: number) => (
                    <tr key={rIdx} className="hover:bg-gray-50/50 transition-colors">
                      {preview.columns.map((col) => (
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
