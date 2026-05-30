import React, { useMemo } from 'react';
import { BookmarkPlus, CheckCircle2, X } from 'lucide-react';

type MemoryCandidateActionStatus = 'idle' | 'pending' | 'submitted' | 'published' | 'ignored' | 'error';

interface NormalizedMemoryContent {
  businessTerm?: string;
  description?: string;
  calculationRule?: string;
  synonyms?: string[];
}

interface MemoryCandidateCardProps {
  title: string;
  content: string;
  disabled?: boolean;
  persistDisabled?: boolean;
  actionStatus?: MemoryCandidateActionStatus;
  onIgnore: () => void;
  onSave: () => void;
  onPublish?: () => void;
}

const parseNormalizedContent = (content: string): NormalizedMemoryContent | null => {
  const trimmed = content.trim();
  if (!trimmed.startsWith('{')) return null;

  try {
    const parsed = JSON.parse(trimmed);
    if (!parsed || typeof parsed !== 'object') return null;

    const synonyms = Array.isArray(parsed.synonyms)
      ? parsed.synonyms.filter((item: unknown): item is string => typeof item === 'string' && item.trim().length > 0)
      : [];

    return {
      businessTerm: typeof parsed.businessTerm === 'string' ? parsed.businessTerm : undefined,
      description: typeof parsed.description === 'string' ? parsed.description : undefined,
      calculationRule: typeof parsed.calculationRule === 'string' ? parsed.calculationRule : undefined,
      synonyms,
    };
  } catch {
    return null;
  }
};

export const MemoryCandidateCard: React.FC<MemoryCandidateCardProps> = ({
  title,
  content,
  disabled = false,
  persistDisabled = false,
  actionStatus = 'idle',
  onIgnore,
  onSave,
  onPublish,
}) => {
  const normalized = useMemo(() => parseNormalizedContent(content), [content]);
  const isPending = actionStatus === 'pending';
  const isSettled = actionStatus === 'submitted' || actionStatus === 'published' || actionStatus === 'ignored';
  const ignoreDisabled = disabled || isPending || isSettled;
  const persistActionDisabled = disabled || persistDisabled || isPending || isSettled;
  const statusText = {
    idle: '',
    pending: '处理中...',
    submitted: '已保存为候选知识',
    published: '已发布到知识库',
    ignored: '本次已跳过保存',
    error: '操作失败，请重试',
  }[actionStatus];

  return (
    <div className="my-3 w-full max-w-[680px] rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3">
      <div className="flex items-center gap-2 text-sm font-semibold text-emerald-950">
        <BookmarkPlus className="size-4" />
        {title}
      </div>
      <div className="mt-2 rounded-md bg-white px-3 py-2 text-sm leading-6 text-gray-700">
        {normalized ? (
          <div className="space-y-2">
            {normalized.businessTerm && (
              <div>
                <span className="text-xs font-semibold text-emerald-700">业务术语</span>
                <div className="mt-0.5 font-medium text-gray-900">{normalized.businessTerm}</div>
              </div>
            )}
            {normalized.description && (
              <div>
                <span className="text-xs font-semibold text-emerald-700">业务说明</span>
                <div className="mt-0.5 text-gray-700">{normalized.description}</div>
              </div>
            )}
            {normalized.calculationRule && (
              <div>
                <span className="text-xs font-semibold text-emerald-700">计算规则</span>
                <code className="mt-1 block whitespace-pre-wrap rounded bg-gray-50 px-2 py-1 font-mono text-xs text-gray-700">
                  {normalized.calculationRule}
                </code>
              </div>
            )}
            {normalized.synonyms && normalized.synonyms.length > 0 && (
              <div>
                <span className="text-xs font-semibold text-emerald-700">同义词</span>
                <div className="mt-1 flex flex-wrap gap-1.5">
                  {normalized.synonyms.map((synonym) => (
                    <span key={synonym} className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs text-emerald-800">
                      {synonym}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="whitespace-pre-wrap">{content}</div>
        )}
      </div>
      <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
        <span className="min-h-5 text-xs text-emerald-800">{statusText}</span>
        <div className="flex flex-wrap justify-end gap-2">
          <button
            type="button"
            disabled={ignoreDisabled}
            onClick={onIgnore}
            className="inline-flex h-9 items-center gap-2 rounded-md border border-gray-200 bg-white px-3 text-sm font-medium text-gray-600 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <X className="size-4" />
            仅本次使用
          </button>
          <button
            type="button"
            disabled={persistActionDisabled}
            onClick={onSave}
            className="inline-flex h-9 items-center gap-2 rounded-md bg-emerald-700 px-3 text-sm font-medium text-white hover:bg-emerald-600 disabled:cursor-not-allowed disabled:bg-gray-300"
          >
            <BookmarkPlus className="size-4" />
            保存为候选知识
          </button>
          {onPublish && (
            <button
              type="button"
              disabled={persistActionDisabled}
              onClick={onPublish}
              className="inline-flex h-9 items-center gap-2 rounded-md bg-gray-900 px-3 text-sm font-medium text-white hover:bg-gray-700 disabled:cursor-not-allowed disabled:bg-gray-300"
            >
              <CheckCircle2 className="size-4" />
              直接发布
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
