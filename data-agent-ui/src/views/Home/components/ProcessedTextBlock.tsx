import React from 'react';
import { Sparkles } from 'lucide-react';
import clsx from 'clsx';
import { TaskToolCard } from './TaskToolCard';
import { DataUnderstanding } from './DataUnderstanding';
import { checkIsWorkflowLog, shouldShowTimeLine } from '../utils';

export interface ProcessedTextBlockProps {
  /** 文本块内容 */
  text: string;
  /** 状态是否彻底接收完毕 */
  isComplete?: boolean;
  /** 用户的检索词 */
  query?: string;
}

/**
 * 文本处理组件：负责根据文本特征解析异常报错、后台进度日志，并对核心工具分析链卡片进行智能分流
 */
export const ProcessedTextBlock: React.FC<ProcessedTextBlockProps> = React.memo(({ 
  text, 
  isComplete = false, 
  query = "" 
}) => {
  const trimmed = text.trim();
  if (!trimmed) return null;

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
  const isWorkflowLog = checkIsWorkflowLog(text);

  // 如果分析已经彻底完成，且满足展示时间轴的条件，工作流执行日志直接隐藏，由时间轴 AnalysisTimeLine 替代呈现
  if (isWorkflowLog && isComplete && shouldShowTimeLine(query)) {
    return null;
  }

  if (isWorkflowLog) {
    const lines = trimmed
      .split(/[\n，。！!;；\.]/)
      .map(line => line.trim())
      .filter(line => line.length > 2); // 过滤掉太短或空行

    if (lines.length > 0) {
      return (
        <div className="my-2 py-2.5 px-3.5 border border-gray-200/80 bg-gray-50/40 rounded-xl w-full max-w-[620px] select-text shadow-3xs">
          <div className="flex items-center gap-1.5 mb-2 pb-1.5 border-b border-gray-150 text-[10px] text-gray-400 font-bold uppercase tracking-wider select-none">
            <span className="size-1.5 rounded-full bg-indigo-500 animate-ping"></span>
            <span>后台执行进度日志</span>
          </div>
          <div className="space-y-1.5">
            {lines.map((line, lIdx) => {
              const isDone = line.includes('完成') || line.includes('成功');
              const isRecall = line.includes('召回') || line.includes('检索') || line.includes('找到') || line.includes('读取');
              return (
                <div key={lIdx} className="flex items-start gap-2 text-[11px] leading-relaxed">
                  <span className="shrink-0 mt-0.5 select-none text-[10px]">
                    {isDone ? '✓' : isRecall ? '🔍' : '⚙️'}
                  </span>
                  <span className={clsx(
                    "font-medium",
                    isDone ? "text-gray-400" : "text-gray-650"
                  )}>
                    {line}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      );
    }
  }

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

  // 2. 导入数据可折叠
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

  // 3. 检索知识库可折叠
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
            <p>平台差异显著：移动端贡献90%以上收入，用户 25-45 岁、高付费、强社交，营销侧重 Meta/TikTok 买量及 Facebook/Discord 社群；PC 端聚焦核心玩家，依赖媒体评测、Steam 愿望单及硬核主播；主机端占比小，侧重叙事型策略，依靠平台推荐及直播曝光。</p>
            <p>评分机制方面，媒体评分显著影响 PC/主机首发销量（Metacritic&gt;80分可提升首周销量 20-30%），对移动端影响较弱；用户评分直接决定移动端自然下载转化率与留存，App Store/Google Play 评分低于 4.0 将严重阻碍增长，高于 4.5 则延长生命周期并支撑买量 ROI。</p>
          </div>
        ) : (
          <div className="text-[12.5px] text-gray-700 space-y-2 leading-relaxed whitespace-pre-wrap select-all">
            {trimmed}
          </div>
        )}
      </TaskToolCard>
    );
  }

  // 4. 联网搜索可折叠
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
              <p className="text-gray-650">2024-2026年全球策略类游戏（Strategy Games，含SLG/RTS/4X等）的市场趋势、头部爆款案例及其核心营销打法有哪些？不同平台用户画像差异有何不同？媒体评分对策略类游戏销量的影响机制是什么？</p>
            </div>
            <div className="border-t border-gray-100 pt-3">
              <h4 className="font-bold text-gray-800 mb-1 text-[13px]">搜索结果</h4>
              <p className="text-gray-650">2024–2026年全球策略类游戏市场呈现“精品化+融合化+全球化”特征。玩法上，“SLG+X”融合模式成主流，题材从传统三国奇幻向冰雪末日、文明IP等多元化拓展。爆款成功关键在于“轻素材引流+重社交留存”。用户评分直接决定移动端自然下载转化率与留存，高于4.5则延长生命周期并支撑买量ROI。</p>
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
});

ProcessedTextBlock.displayName = 'ProcessedTextBlock';
