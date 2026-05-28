import React, { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import clsx from 'clsx';

export interface TaskToolCardProps {
  title: string;
  summary?: string;
  defaultOpen?: boolean;
  children: React.ReactNode;
}

export const TaskToolCard: React.FC<TaskToolCardProps> = React.memo(({
  title,
  summary,
  defaultOpen = true,
  children,
}) => {
  const [isOpen, setIsOpen] = useState(defaultOpen);

  return (
    <div className="my-3 w-full max-w-[680px] overflow-hidden rounded-xl border border-gray-200 bg-white shadow-xs select-none">
      <button
        type="button"
        onClick={() => setIsOpen(prev => !prev)}
        className="flex min-h-12 w-full items-center justify-between gap-3 border-0 bg-white px-5 py-3 text-left transition-colors hover:bg-gray-50/70"
      >
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <ChevronDown className={clsx('size-4 text-gray-400 transition-transform', isOpen && 'rotate-180')} />
            <span className="text-[13px] font-semibold text-gray-800">{title}</span>
          </div>
          {!isOpen && summary && (
            <p className="m-0 mt-1 truncate pl-6 text-[12px] leading-5 text-gray-400">
              {summary}
            </p>
          )}
        </div>
      </button>

      {isOpen && (
        <div className="border-t border-gray-100 bg-[#FAFAFC] px-5 py-4">
          <div className="max-h-72 overflow-y-auto text-sm leading-6 text-gray-600 select-text">
            {children}
          </div>
        </div>
      )}
    </div>
  );
});

TaskToolCard.displayName = 'TaskToolCard';
