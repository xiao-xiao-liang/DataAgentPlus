import React, { useMemo, useState } from 'react';
import { BarChart3, BookOpen, CheckCircle2, ChevronDown, Clipboard, Clock3, Code2, Database, FileText, Search } from 'lucide-react';
import clsx from 'clsx';
import { buildWorkflowPresentation, parseWorkflowPlan } from '../workflowPresentation';

export interface WorkflowPlanProps {
  planJson: string;
  currentBlockCount: number;
  isComplete?: boolean;
  query?: string;
}

const iconClassName = 'size-4 text-gray-500';

const getStepIcon = (source?: string) => {
  if (!source) return <Clock3 className={iconClassName} />;
  if (source.includes('intent')) return <Search className={iconClassName} />;
  if (source.includes('schema')) return <Database className={iconClassName} />;
  if (source.includes('sql')) return <BarChart3 className={iconClassName} />;
  if (source.includes('python')) return <Code2 className={iconClassName} />;
  if (source.includes('report')) return <FileText className={iconClassName} />;
  return <Clock3 className={iconClassName} />;
};

export const WorkflowPlan: React.FC<WorkflowPlanProps> = React.memo(({
  planJson,
  currentBlockCount,
  isComplete = false,
}) => {
  const [isOpen, setIsOpen] = useState(true);
  const plan = useMemo(() => parseWorkflowPlan(planJson), [planJson]);

  if (!plan) {
    return (
      <div className="my-3 w-full max-w-[680px] rounded-xl border border-gray-200 bg-white shadow-xs">
        <div className="flex h-14 items-center gap-3 px-5">
          <BookOpen className="size-4 text-[#5B55FF]" />
          <span className="text-[14px] font-semibold text-gray-800">生成执行计划</span>
        </div>
        <div className="border-t border-gray-100 px-5 py-4">
          <div className="h-3 w-3/4 animate-pulse rounded bg-gray-100" />
          <div className="mt-3 h-3 w-1/2 animate-pulse rounded bg-gray-100" />
        </div>
      </div>
    );
  }

  const presentation = buildWorkflowPresentation(plan);
  const visibleCount = isComplete
    ? presentation.steps.length
    : Math.min(presentation.steps.length, Math.max(1, currentBlockCount + 2));

  const handleCopy = () => {
    navigator.clipboard.writeText(JSON.stringify(plan, null, 2));
  };

  return (
    <div className="my-3 w-full max-w-[680px] overflow-hidden rounded-xl border border-gray-200 bg-white shadow-xs select-none">
      <div className="flex h-14 w-full items-center justify-between border-b border-gray-100 bg-white px-5">
        <button
          type="button"
          onClick={() => setIsOpen(prev => !prev)}
          className="flex min-w-0 flex-1 items-center gap-3 border-0 bg-transparent p-0 text-left"
        >
          <BookOpen className="size-4 text-[#5B55FF]" />
          <span className="text-[14px] font-semibold text-gray-800">执行计划</span>
          <span className={clsx(
            'rounded-full px-2 py-0.5 text-[11px] font-semibold',
            isComplete ? 'bg-emerald-50 text-emerald-600' : 'bg-[#EEEAFE] text-[#5B55FF]'
          )}>
            {isComplete ? '已完成' : '执行中'}
          </span>
        </button>
        <div className="flex items-center gap-2 text-gray-500">
          <button
            type="button"
            aria-label="复制执行计划"
            onClick={handleCopy}
            className="grid size-7 place-items-center rounded-md border-0 bg-transparent text-gray-500 hover:bg-gray-100 hover:text-gray-800"
          >
            <Clipboard className="size-4" />
          </button>
          <button
            type="button"
            aria-label={isOpen ? '收起执行计划' : '展开执行计划'}
            onClick={() => setIsOpen(prev => !prev)}
            className="grid size-7 place-items-center rounded-md border-0 bg-transparent text-gray-500 hover:bg-gray-100 hover:text-gray-800"
          >
            <ChevronDown className={clsx('size-4 transition-transform', isOpen && 'rotate-180')} />
          </button>
        </div>
      </div>

      {isOpen && (
        <div className="px-6 py-5">
          <div className="mb-5 rounded-lg border border-gray-100 bg-[#FAFAFC] px-4 py-3 text-[13px] leading-6 text-gray-600 select-text">
            <span className="font-semibold text-gray-800">分析思路：</span>
            {presentation.thought}
          </div>

          <div className="relative space-y-6">
            <div className="absolute left-[13px] top-4 bottom-4 w-px bg-gray-200" />
            {presentation.steps.map((step, index) => {
              const done = index < visibleCount;
              const current = index === visibleCount && !isComplete;
              return (
                <div key={step.key} className="relative grid grid-cols-[28px_1fr] gap-4">
                  <div className={clsx(
                    'z-10 grid size-7 place-items-center rounded-full border bg-white',
                    done ? 'border-gray-200 bg-gray-50' : current ? 'border-[#5B55FF] bg-[#F3F1FF]' : 'border-gray-200 bg-white'
                  )}>
                    {done ? <CheckCircle2 className="size-4 text-emerald-500" /> : getStepIcon(step.source)}
                  </div>
                  <div className="min-w-0 select-text">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="m-0 text-[15px] font-medium leading-6 text-gray-800">{step.title}</h3>
                      {step.source && (
                        <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[10px] font-medium text-gray-400">
                          {step.source}
                        </span>
                      )}
                    </div>
                    <p className="m-0 mt-1 whitespace-pre-line text-[13px] leading-6 text-gray-500">
                      {step.description}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
});

WorkflowPlan.displayName = 'WorkflowPlan';
