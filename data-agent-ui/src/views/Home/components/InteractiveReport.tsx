import React, { useEffect, useRef, useState } from 'react';
import { X, Maximize2, Minimize2, Share2, FileText, Activity } from 'lucide-react';
import clsx from 'clsx';
import { MarkdownParser } from './MarkdownParser';

interface InteractiveReportProps {
  isOpen: boolean;
  markdownContent?: string;
  onClose: () => void;
}

export const InteractiveReport: React.FC<InteractiveReportProps> = ({ isOpen, markdownContent = '', onClose }) => {
  const [isFullscreen, setIsFullscreen] = useState(false);
  const scrollContainerRef = useRef<HTMLDivElement | null>(null);
  const hasMarkdownReport = Boolean(markdownContent.trim());

  useEffect(() => {
    if (!isOpen || !hasMarkdownReport || !scrollContainerRef.current) return;

    const container = scrollContainerRef.current;
    const rafId = window.requestAnimationFrame(() => {
      container.scrollTop = container.scrollHeight;
    });

    return () => window.cancelAnimationFrame(rafId);
  }, [isOpen, hasMarkdownReport, markdownContent]);

  if (!isOpen) return null;

  return (
    <div
      className={clsx(
        'bg-white/95 backdrop-blur-md border-l border-gray-200/80 shadow-2xl flex flex-col transition-all duration-300 ease-out z-[45] shrink-0',
        isFullscreen
          ? 'fixed inset-0 w-full h-full'
          : 'w-[640px] h-[calc(100vh-3rem)] sticky right-0 top-12'
      )}
    >
      <div className="flex items-center justify-between px-5 py-3 border-b border-gray-100 flex-none bg-[#FAFAFC]">
        <div className="flex min-w-0 items-center gap-3">
          <button
            type="button"
            className="grid size-7 place-items-center rounded-lg border-none bg-transparent text-gray-500 hover:bg-gray-100 cursor-pointer"
            title="收起报告栏"
            onClick={onClose}
          >
            <span className="text-lg leading-none">»</span>
          </button>
          <div className="flex items-center rounded-xl bg-gray-100 p-0.5">
            <button
              type="button"
              className="flex h-7 items-center gap-1.5 rounded-lg border-none bg-white px-3 text-xs font-bold text-gray-850 shadow-3xs cursor-default"
            >
              <FileText className="size-3.5" />
              <span>报告</span>
            </button>
            <button
              type="button"
              className="flex h-7 items-center gap-1.5 rounded-lg border-none bg-transparent px-3 text-xs font-semibold text-gray-400 cursor-not-allowed"
              title="后续接入执行过程视图"
            >
              <Activity className="size-3.5" />
              <span>执行过程</span>
            </button>
          </div>
          <h2 className="truncate text-sm font-bold text-gray-900">
            {hasMarkdownReport ? 'Markdown 分析报告' : '报告生成中'}
          </h2>
        </div>

        <div className="flex items-center gap-2 flex-none">
          <button
            onClick={() => {
              navigator.clipboard?.writeText(markdownContent || '');
            }}
            className="p-1.5 rounded-lg text-gray-500 hover:bg-gray-200/50 cursor-pointer active:scale-95 transition-all"
            title="复制报告内容"
            disabled={!hasMarkdownReport}
          >
            <Share2 className="w-4 h-4" />
          </button>
          <button
            onClick={() => setIsFullscreen(prev => !prev)}
            className="p-1.5 rounded-lg text-gray-500 hover:bg-gray-200/50 cursor-pointer active:scale-95 transition-all"
            title={isFullscreen ? '退出全屏' : '全屏查看'}
          >
            {isFullscreen ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
          </button>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-200/50 cursor-pointer active:scale-95 transition-all"
            title="关闭报告"
          >
            <X className="w-4.5 h-4.5" />
          </button>
        </div>
      </div>

      {hasMarkdownReport ? (
        <div ref={scrollContainerRef} className="flex-1 overflow-y-auto px-8 py-7 select-text">
          <div className="mx-auto max-w-[960px] text-gray-850">
            <MarkdownParser content={markdownContent} />
          </div>
        </div>
      ) : (
        <div className="flex-1 overflow-y-auto px-8 py-8">
          <div className="mx-auto max-w-[720px] rounded-xl border border-gray-150 bg-[#FAFAFC] px-5 py-5">
            <div className="flex items-center gap-3 text-[13px] font-semibold text-gray-600">
              <span>正在接收报告内容</span>
              <span className="flex items-center gap-1" aria-label="报告生成中">
                <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-gray-400" />
                <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-gray-400 delay-100" />
                <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-gray-400 delay-200" />
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
