import React, { useMemo, useState } from 'react';
import { AlertTriangle, ChevronRight, Copy } from 'lucide-react';
import clsx from 'clsx';

interface BackendErrorNoticeProps {
  content: string;
}

const buildErrorSummary = (content: string) => {
  const lines = content
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean);
  return lines.find(line => /exception|error|failed|报错|失败/i.test(line)) || lines[0] || '后台工作流执行异常';
};

export const BackendErrorNotice: React.FC<BackendErrorNoticeProps> = ({ content }) => {
  const [isOpen, setIsOpen] = useState(false);
  const summary = useMemo(() => buildErrorSummary(content), [content]);

  const handleCopyError = () => {
    navigator.clipboard.writeText(content);
  };

  return (
    <div className="my-2 w-full max-w-[620px] rounded-lg border border-red-200 bg-red-50/50 px-3 py-2.5 text-red-800 shadow-3xs select-text">
      <div className="flex items-center gap-2">
        <AlertTriangle className="size-4 shrink-0 text-red-600" />
        <button
          type="button"
          onClick={() => setIsOpen(!isOpen)}
          className="flex min-w-0 flex-1 items-center gap-1.5 border-none bg-transparent p-0 text-left cursor-pointer"
        >
          <ChevronRight className={clsx('size-3.5 shrink-0 text-red-500 transition-transform', isOpen && 'rotate-90')} />
          <span className="shrink-0 text-[12px] font-bold">后台工作流执行异常</span>
          <span className="min-w-0 truncate text-[11px] font-mono text-red-600/90">{summary}</span>
        </button>
        <button
          type="button"
          onClick={handleCopyError}
          className="inline-flex h-7 shrink-0 items-center gap-1 rounded-md border border-red-200 bg-white px-2 text-[11px] font-semibold text-red-700 transition-colors hover:bg-red-50 cursor-pointer"
        >
          <Copy className="size-3" />
          复制
        </button>
      </div>
      {isOpen && (
        <pre className="mt-2 max-h-36 overflow-y-auto whitespace-pre-wrap rounded-md bg-white/80 px-3 py-2 text-[10.5px] leading-5 text-red-600">
          {content}
        </pre>
      )}
    </div>
  );
};
