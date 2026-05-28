import React, { useMemo, useState } from 'react';
import { ChevronDown, TerminalSquare } from 'lucide-react';
import clsx from 'clsx';

export interface UnifiedProgressPanelProps {
  logs: string[];
  isComplete: boolean;
}

const getLogTone = (log: string) => {
  if (log.includes('完成') || log.includes('成功') || log.includes('通过')) {
    return 'done';
  }
  if (log.includes('失败') || log.includes('异常') || log.includes('报错')) {
    return 'error';
  }
  if (log.includes('即将') || log.includes('正在') || log.includes('开始')) {
    return 'active';
  }
  return 'idle';
};

export const UnifiedProgressPanel: React.FC<UnifiedProgressPanelProps> = React.memo(({ logs, isComplete }) => {
  const [isOpen, setIsOpen] = useState(false);
  const normalizedLogs = useMemo(() => logs.map(log => log.trim()).filter(Boolean), [logs]);

  if (normalizedLogs.length === 0) return null;

  return (
    <div className="my-3 w-full max-w-[680px] overflow-hidden rounded-xl border border-gray-200 bg-white shadow-xs select-none">
      <button
        type="button"
        onClick={() => setIsOpen(prev => !prev)}
        className="flex h-12 w-full items-center justify-between border-0 bg-white px-5 text-left transition-colors hover:bg-gray-50/70"
      >
        <div className="flex min-w-0 items-center gap-2">
          <TerminalSquare className="size-4 text-gray-400" />
          <span className="text-[13px] font-semibold text-gray-700">后台执行链日志</span>
          <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[10px] font-medium text-gray-400">
            {normalizedLogs.length} 条记录
          </span>
          <span className={clsx(
            'rounded-full px-2 py-0.5 text-[10px] font-semibold',
            isComplete ? 'bg-emerald-50 text-emerald-600' : 'bg-[#EEEAFE] text-[#5B55FF]'
          )}>
            {isComplete ? '已就绪' : '流式追加中'}
          </span>
        </div>
        <ChevronDown className={clsx('size-4 text-gray-400 transition-transform', isOpen && 'rotate-180')} />
      </button>

      {isOpen && (
        <div className="max-h-64 overflow-y-auto border-t border-gray-100 px-5 py-4">
          <div className="relative space-y-3 pl-4 before:absolute before:left-[3px] before:top-2 before:bottom-2 before:w-px before:bg-gray-200">
            {normalizedLogs.map((log, index) => {
              const tone = getLogTone(log);
              return (
                <div key={`${index}-${log}`} className="relative text-[12px] leading-5 text-gray-500">
                  <span className={clsx(
                    'absolute -left-[15px] top-1.5 size-2 rounded-full border border-white',
                    tone === 'done' && 'bg-emerald-500',
                    tone === 'error' && 'bg-red-500',
                    tone === 'active' && 'bg-[#5B55FF]',
                    tone === 'idle' && 'bg-gray-300'
                  )} />
                  <span className={clsx(
                    tone === 'done' && 'text-emerald-600',
                    tone === 'error' && 'text-red-600',
                    tone === 'active' && 'text-[#5B55FF]',
                  )}>
                    {log}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
});

UnifiedProgressPanel.displayName = 'UnifiedProgressPanel';
