import React from 'react';
import { Clock, LineChart, Network, Sheet, Code2, Check } from 'lucide-react';
import type { MessageBlock } from '../types';

export interface AnalysisTimeLineProps {
  /** 用户提问文本 */
  query: string;
  /** 消息包含的 blocks */
  blocks: MessageBlock[];
}

/**
 * 任务彻底结束后的耗时时间轴进度图，精细模拟人类分析流程的耗时感
 */
export const AnalysisTimeLine: React.FC<AnalysisTimeLineProps> = React.memo(({ query, blocks }) => {
  const isGame = query.includes('游戏') || query.includes('营销') || query.includes('销量') || query.includes('Strategy') || query.includes('SLG');
  
  // 尝试从 blocks 中寻找计划 JSON
  const jsonBlock = blocks?.find(b => b.type === 'json');
  let planObj: any = null;
  if (jsonBlock) {
    try {
      const clean = jsonBlock.content.replace('$$$json', '').trim();
      planObj = JSON.parse(clean);
    } catch (e) {
      // 捕获可能由于未闭合等引起的解析失败
    }
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
    // 兜底 Mock 逻辑
    nodes = isGame ? [
      { label: '策略类游戏的全球销量表现与区域市场偏好分析', duration: '3m35s', icon: <LineChart className="size-3.5 text-gray-500" /> },
      { label: '策略类游戏的评分-销量关系建模与口碑价值量化', duration: '3m29s', icon: <Network className="size-3.5 text-gray-500" /> },
      { label: '策略类游戏的成功要素拆解与发行商能力映射', duration: '1m49s', icon: <Clock className="size-3.5 text-gray-500" /> },
      { label: '策略类游戏平台生态与营销渠道适配性推断', duration: '1m34s', icon: <Sheet className="size-3.5 text-gray-500" /> },
      { label: '报告生成', duration: '2m2s', icon: <Code2 className="size-3.5 text-gray-500" /> },
    ] : [
      { label: '提取与汇总销量/业务数据', duration: '2m15s', icon: <LineChart className="size-3.5 text-gray-500" /> },
      { label: '进行数据关联性评估与数学建模', duration: '1m45s', icon: <Network className="size-3.5 text-gray-500" /> },
      { label: '报告生成', duration: '1m10s', icon: <Code2 className="size-3.5 text-gray-500" /> },
    ];
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
});

AnalysisTimeLine.displayName = 'AnalysisTimeLine';
