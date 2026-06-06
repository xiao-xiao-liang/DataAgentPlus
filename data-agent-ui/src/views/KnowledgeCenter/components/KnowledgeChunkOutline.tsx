import React from 'react';
import { AlertCircle, CheckCircle2, Clock3, Loader2, Search } from 'lucide-react';
import clsx from 'clsx';
import type { KnowledgeChunk } from '../types';

interface KnowledgeChunkOutlineProps {
  chunks: KnowledgeChunk[];
  selectedId: string | null;
  keyword: string;
  statusFilter: string;
  isLoading: boolean;
  onKeywordChange: (value: string) => void;
  onStatusFilterChange: (value: string) => void;
  onSelect: (chunkId: string) => void;
}

const statusMeta = {
  SYNCED: { label: '已向量化', icon: CheckCircle2, className: 'text-emerald-600' },
  PROCESSING: { label: '向量化中', icon: Loader2, className: 'text-blue-600' },
  PENDING: { label: '等待处理', icon: Clock3, className: 'text-amber-600' },
  FAILED: { label: '处理失败', icon: AlertCircle, className: 'text-rose-600' },
} as const;

export const KnowledgeChunkOutline: React.FC<KnowledgeChunkOutlineProps> = ({
  chunks,
  selectedId,
  keyword,
  statusFilter,
  isLoading,
  onKeywordChange,
  onStatusFilterChange,
  onSelect,
}) => (
  <aside className="flex min-h-0 w-[19rem] shrink-0 flex-col border-r border-gray-200 bg-gray-50/60 max-lg:w-[16rem] max-md:w-full max-md:max-h-[17rem] max-md:border-b max-md:border-r-0">
    <div className="border-b border-gray-200 bg-white p-3">
      <div className="relative">
        <Search className="pointer-events-none absolute left-2.5 top-2.5 size-3.5 text-gray-400" />
        <input
          value={keyword}
          onChange={(event) => onKeywordChange(event.target.value)}
          placeholder="搜索名称或正文"
          className="h-8 w-full rounded-md border border-gray-200 bg-white pl-8 pr-3 text-xs font-medium text-gray-700 outline-none transition-colors placeholder:text-gray-400 focus:border-gray-400"
        />
      </div>
      <div className="mt-2 grid grid-cols-3 gap-1 rounded-md bg-gray-100 p-1">
        {[
          ['ALL', '全部'],
          ['PROCESSING', '处理中'],
          ['FAILED', '失败'],
        ].map(([value, label]) => (
          <button
            key={value}
            type="button"
            onClick={() => onStatusFilterChange(value)}
            className={clsx(
              'h-7 rounded px-2 text-[11px] font-semibold transition-colors',
              statusFilter === value ? 'bg-white text-gray-900 shadow-xs' : 'text-gray-500 hover:text-gray-800',
            )}
          >
            {label}
          </button>
        ))}
      </div>
    </div>

    <div className="flex min-h-0 flex-1 flex-col overflow-y-auto p-2 no-scrollbar">
      {isLoading ? (
        <div className="flex flex-1 items-center justify-center text-xs font-semibold text-gray-400">
          <Loader2 className="mr-2 size-4 animate-spin" />
          加载分块
        </div>
      ) : chunks.length === 0 ? (
        <div className="flex flex-1 items-center justify-center px-6 text-center text-xs font-semibold leading-5 text-gray-400">
          没有匹配的分块
        </div>
      ) : (
        <div className="space-y-1">
          {chunks.map((chunk) => {
            const meta = statusMeta[chunk.vectorStatus] || statusMeta.PENDING;
            const StatusIcon = meta.icon;
            return (
              <button
                key={chunk.id}
                type="button"
                onClick={() => onSelect(chunk.id)}
                className={clsx(
                  'group flex w-full items-start gap-2.5 rounded-md border px-2.5 py-2 text-left transition-colors',
                  selectedId === chunk.id
                    ? 'border-gray-300 bg-white shadow-xs'
                    : 'border-transparent bg-transparent hover:border-gray-200 hover:bg-white',
                )}
              >
                <span className="mt-0.5 w-6 shrink-0 font-mono text-[10px] font-bold text-gray-400">
                  #{chunk.seq}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-xs font-bold text-gray-800" title={chunk.name}>
                    {chunk.name || `分块 #${chunk.seq}`}
                  </span>
                  <span className="mt-1 flex items-center justify-between gap-2 text-[10px] font-medium text-gray-400">
                    <span className={clsx('flex min-w-0 items-center gap-1 truncate', meta.className)}>
                      <StatusIcon className={clsx('size-3 shrink-0', chunk.vectorStatus === 'PROCESSING' && 'animate-spin')} />
                      {meta.label}
                    </span>
                    <span className="shrink-0 font-mono">{chunk.length} 字符</span>
                  </span>
                </span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  </aside>
);
