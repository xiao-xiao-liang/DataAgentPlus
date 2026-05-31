import React, { useEffect, useRef, useState } from 'react';
import { X, Maximize2, Minimize2, Share2, FileText, Activity, ChevronsRight } from 'lucide-react';
import clsx from 'clsx';
import { MarkdownParser } from './MarkdownParser';
import { REPORT_PANEL_DEFAULT_WIDTH, getReportPanelWidthFromDrag } from '../reportLayoutState';

interface InteractiveReportProps {
  isOpen: boolean;
  width?: number;
  markdownContent?: string;
  onClose: () => void;
  onWidthChange?: (width: number) => void;
  onResizeStateChange?: (isResizing: boolean) => void;
}

export const InteractiveReport: React.FC<InteractiveReportProps> = ({
  isOpen,
  width = REPORT_PANEL_DEFAULT_WIDTH,
  markdownContent = '',
  onClose,
  onWidthChange = () => {},
  onResizeStateChange = () => {},
}) => {
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [isResizing, setIsResizing] = useState(false);
  const panelRef = useRef<HTMLDivElement | null>(null);
  const scrollContainerRef = useRef<HTMLDivElement | null>(null);
  const hasMarkdownReport = Boolean(markdownContent.trim());
  const shouldRender = isOpen || hasMarkdownReport;

  const handleResizePointerDown = (event: React.PointerEvent<HTMLButtonElement>) => {
    if (isFullscreen) return;

    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    setIsResizing(true);
    onResizeStateChange(true);

    const getRightEdge = () => panelRef.current?.getBoundingClientRect().right || window.innerWidth;
    const updateWidth = (clientX: number) => {
      onWidthChange(getReportPanelWidthFromDrag({
        viewportWidth: getRightEdge(),
        pointerClientX: clientX,
      }));
    };
    updateWidth(event.clientX);

    const handlePointerMove = (moveEvent: PointerEvent) => {
      updateWidth(moveEvent.clientX);
    };

    const handlePointerUp = () => {
      setIsResizing(false);
      onResizeStateChange(false);
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerup', handlePointerUp);
      window.removeEventListener('pointercancel', handlePointerUp);
    };

    window.addEventListener('pointermove', handlePointerMove);
    window.addEventListener('pointerup', handlePointerUp, { once: true });
    window.addEventListener('pointercancel', handlePointerUp, { once: true });
  };

  useEffect(() => {
    if (!isOpen || !hasMarkdownReport || !scrollContainerRef.current) return;

    const container = scrollContainerRef.current;
    const rafId = window.requestAnimationFrame(() => {
      container.scrollTop = container.scrollHeight;
    });

    return () => window.cancelAnimationFrame(rafId);
  }, [isOpen, hasMarkdownReport, markdownContent]);

  if (!shouldRender) return null;

  return (
    <div
      ref={panelRef}
      className={clsx(
        'z-[45] flex shrink-0 flex-col border-l border-gray-200/80 bg-white/95 shadow-2xl backdrop-blur-md ease-out',
        isResizing ? 'transition-none select-none' : 'transition-all duration-300',
        isFullscreen ? 'fixed inset-0 h-full w-full' : 'absolute right-0 top-0 h-full',
        isOpen ? 'translate-x-0 opacity-100 pointer-events-auto' : 'translate-x-full opacity-0 pointer-events-none',
      )}
      style={isFullscreen ? undefined : { width }}
    >
      {!isFullscreen && (
        <button
          type="button"
          aria-label="调整报告宽度"
          title="调整报告宽度"
          onPointerDown={handleResizePointerDown}
          className="absolute left-0 top-0 z-10 h-full w-2 -translate-x-1 cursor-col-resize border-none bg-transparent p-0 outline-none transition-colors hover:bg-gray-300/50 active:bg-gray-400/60"
        />
      )}
      <div className="flex flex-none items-center justify-between border-b border-gray-100 bg-[#FAFAFC] px-5 py-3">
        <div className="flex min-w-0 items-center gap-3">
          <button
            type="button"
            className="grid size-7 place-items-center rounded-lg border-none bg-transparent text-gray-500 transition-colors hover:bg-gray-100"
            title="收起报告"
            onClick={onClose}
          >
            <ChevronsRight className="size-4" />
          </button>

          <div className="flex items-center rounded-xl bg-gray-100 p-0.5">
            <button
              type="button"
              className="flex h-7 cursor-default items-center gap-1.5 rounded-lg border-none bg-white px-3 text-xs font-bold text-gray-800 shadow-3xs"
            >
              <FileText className="size-3.5" />
              <span>报告</span>
            </button>
            <button
              type="button"
              className="flex h-7 cursor-not-allowed items-center gap-1.5 rounded-lg border-none bg-transparent px-3 text-xs font-semibold text-gray-400"
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

        <div className="flex flex-none items-center gap-2">
          <button
            type="button"
            onClick={() => {
              navigator.clipboard?.writeText(markdownContent || '');
            }}
            className="rounded-lg p-1.5 text-gray-500 transition-all hover:bg-gray-200/50 active:scale-95 disabled:cursor-not-allowed disabled:text-gray-300"
            title="复制报告内容"
            disabled={!hasMarkdownReport}
          >
            <Share2 className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => setIsFullscreen((prev) => !prev)}
            className="rounded-lg p-1.5 text-gray-500 transition-all hover:bg-gray-200/50 active:scale-95"
            title={isFullscreen ? '退出全屏' : '全屏查看'}
          >
            {isFullscreen ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-gray-400 transition-all hover:bg-gray-200/50 hover:text-gray-600 active:scale-95"
            title="关闭报告"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      </div>

      {hasMarkdownReport ? (
        <div ref={scrollContainerRef} className="flex-1 overflow-y-auto px-8 py-7 select-text">
          <div className="mx-auto max-w-[960px] text-gray-800">
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
                <span className="delay-100 h-1.5 w-1.5 animate-bounce rounded-full bg-gray-400" />
                <span className="delay-200 h-1.5 w-1.5 animate-bounce rounded-full bg-gray-400" />
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
