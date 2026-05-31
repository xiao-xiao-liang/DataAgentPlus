import React from 'react';
import { Sparkles } from 'lucide-react';
import clsx from 'clsx';
import type { KnowledgeCandidate, KnowledgeCandidateViewModel } from '../types';

interface KnowledgeCandidateCardProps {
  item: KnowledgeCandidate;
  viewModel: KnowledgeCandidateViewModel;
  isActive?: boolean;
  onClick?: () => void;
}

const STATUS_LABELS: Record<string, string> = {
  DRAFT: '待处理',
  PUBLISHED: '已发布',
  REJECTED: '已驳回',
};

const STATUS_STYLES: Record<string, string> = {
  DRAFT: 'bg-amber-50 text-amber-700 border border-amber-200',
  PUBLISHED: 'bg-emerald-50 text-emerald-700 border border-emerald-200',
  REJECTED: 'bg-rose-50 text-rose-700 border border-rose-200',
};

const TYPE_LABELS: Record<string, string> = {
  BUSINESS_KNOWLEDGE: '业务知识',
};



export const KnowledgeCandidateCard: React.FC<KnowledgeCandidateCardProps> = ({
  item,
  viewModel,
  isActive = false,
  onClick,
}) => {
  const statusLabel = STATUS_LABELS[item.status] || item.status;
  const statusStyle = STATUS_STYLES[item.status] || 'bg-gray-50 text-gray-600 border border-gray-200';
  const candidateTypeLabel = TYPE_LABELS[item.candidateType] || item.candidateType;

  return (
    <div
      onClick={onClick}
      className={clsx(
        "relative overflow-hidden rounded-xl border p-3.5 transition-all duration-200 cursor-pointer select-none",
        isActive 
          ? "border-indigo-300 bg-indigo-50/20 shadow-xs before:absolute before:left-0 before:top-0 before:bottom-0 before:w-1 before:bg-[#2D336B]" 
          : "border-gray-200 bg-white hover:border-gray-300 hover:bg-gray-50/40"
      )}
    >
      <div className="flex min-w-0 items-start gap-2.5">
        <div className={clsx(
          "mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-md text-xs transition-colors",
          isActive ? "bg-indigo-100 text-indigo-700" : "bg-gray-100 text-gray-500"
        )}>
          <Sparkles className="size-3.5" />
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1">
            <span className={clsx('inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-semibold', statusStyle)}>
              {statusLabel}
            </span>
            <span className="inline-flex items-center rounded-full border border-gray-200 bg-gray-50 px-1.5 py-0.5 text-[10px] font-medium text-gray-500">
              {candidateTypeLabel}
            </span>
            {item.confidenceScore && (
              <span className="inline-flex items-center rounded-full border border-indigo-100 bg-indigo-50/60 px-1.5 py-0.5 text-[10px] font-bold text-indigo-600">
                {Math.round(item.confidenceScore * 100)}%
              </span>
            )}
          </div>

          <h4 className={clsx(
            "mt-1.5 text-xs font-semibold leading-4 text-gray-900 truncate transition-colors",
            isActive && "text-indigo-950 font-bold"
          )}>
            {item.title}
          </h4>

          <p className="mt-1 text-[11px] leading-4 text-gray-500 line-clamp-1">
            {viewModel.description || '暂无摘要描述'}
          </p>
        </div>
      </div>
    </div>
  );
};
