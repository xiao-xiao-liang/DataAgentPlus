import React from 'react';
import { AlertCircle, CheckCircle2, Clock3, Loader2, Lock, RefreshCw, Save, Sparkles } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import clsx from 'clsx';
import type { KnowledgeChunk } from '../types';

interface KnowledgeChunkEditorProps {
  chunk: KnowledgeChunk | null;
  isLoading: boolean;
  isSaving: boolean;
  isRetrying: boolean;
  isGeneratingName: boolean;
  onSave: (name: string, content: string) => void;
  onRetry: () => void;
  onRecover: () => void;
  onGenerateName: () => void;
  onDirtyChange: (dirty: boolean) => void;
}

const vectorStatusMeta = {
  SYNCED: { label: '向量已同步', detail: '当前内容已可用于检索', icon: CheckCircle2, className: 'border-emerald-200 bg-emerald-50 text-emerald-700' },
  PROCESSING: { label: '正在重新向量化', detail: '旧向量仍然可用，完成后将自动切换', icon: Loader2, className: 'border-blue-200 bg-blue-50 text-blue-700' },
  PENDING: { label: '等待向量化', detail: '任务已进入异步处理队列', icon: Clock3, className: 'border-amber-200 bg-amber-50 text-amber-700' },
  FAILED: { label: '向量化失败', detail: '内容已保存，可手动重新提交任务', icon: AlertCircle, className: 'border-rose-200 bg-rose-50 text-rose-700' },
} as const;

export const KnowledgeChunkEditor: React.FC<KnowledgeChunkEditorProps> = ({
  chunk,
  isLoading,
  isSaving,
  isRetrying,
  isGeneratingName,
  onSave,
  onRetry,
  onRecover,
  onGenerateName,
  onDirtyChange,
}) => {
  const [name, setName] = React.useState(chunk?.name || '');
  const [content, setContent] = React.useState(chunk?.content || '');
  const [mode, setMode] = React.useState<'edit' | 'preview'>('edit');

  const isDirty = Boolean(chunk && (name !== chunk.name || content !== chunk.content));
  const isContentDirty = Boolean(chunk && content !== chunk.content);
  const timeoutStartedAt = chunk?.vectorStatus === 'PROCESSING'
    ? chunk.vectorProcessingStartedAt
    : chunk?.updateTime;
  const isTimedOut = Boolean(
    (chunk?.vectorStatus === 'PENDING' || chunk?.vectorStatus === 'PROCESSING')
      && timeoutStartedAt
      && chunk.vectorTaskTimeoutSeconds
      && Date.now() - new Date(timeoutStartedAt).getTime() > chunk.vectorTaskTimeoutSeconds * 1000,
  );

  React.useEffect(() => {
    onDirtyChange(isDirty);
  }, [isDirty, onDirtyChange]);

  if (isLoading || !chunk) {
    return (
      <div className="flex min-h-0 flex-1 items-center justify-center bg-white text-xs font-semibold text-gray-400">
        {isLoading ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
        {isLoading ? '加载分块详情' : '从左侧选择一个分块开始编辑'}
      </div>
    );
  }

  const status = vectorStatusMeta[chunk.vectorStatus] || vectorStatusMeta.PENDING;
  const StatusIcon = status.icon;

  return (
    <section className="flex min-h-0 min-w-0 flex-1 flex-col bg-white">
      <div className="flex flex-none items-center justify-between gap-4 border-b border-gray-200 px-5 py-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="font-mono text-[11px] font-bold text-gray-400">#{chunk.seq}</span>
            {chunk.nameLocked && (
              <span className="inline-flex items-center gap-1 rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-semibold text-gray-500">
                <Lock className="size-2.5" />
                名称已锁定
              </span>
            )}
          </div>
          <div className="mt-1 flex items-center gap-3 text-[10px] font-medium text-gray-400">
            <span>{chunk.length} 字符</span>
            <span>内容版本 v{chunk.contentVersion}</span>
            <span>向量版本 v{chunk.vectorVersion || 0}</span>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <div className="flex rounded-md bg-gray-100 p-1">
            {[
              ['edit', '编辑'],
              ['preview', '预览'],
            ].map(([value, label]) => (
              <button
                key={value}
                type="button"
                onClick={() => setMode(value as 'edit' | 'preview')}
                className={clsx(
                  'h-7 rounded px-3 text-[11px] font-bold transition-colors',
                  mode === value ? 'bg-white text-gray-900 shadow-xs' : 'text-gray-500 hover:text-gray-800',
                )}
              >
                {label}
              </button>
            ))}
          </div>
          <button
            type="button"
            onClick={() => onSave(name, content)}
            disabled={!isDirty || isSaving || !name.trim() || !content.trim()}
            className="inline-flex h-9 items-center gap-1.5 rounded-md bg-gray-900 px-3.5 text-xs font-bold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-200 disabled:text-gray-400"
          >
            {isSaving ? <Loader2 className="size-3.5 animate-spin" /> : <Save className="size-3.5" />}
            {isSaving ? '保存中' : isContentDirty ? '保存并重新向量化' : '保存名称'}
          </button>
        </div>
      </div>

      <div className="flex min-h-0 flex-1 flex-col overflow-y-auto px-5 py-4 no-scrollbar">
        <div className={clsx('mb-4 flex items-center justify-between gap-3 rounded-md border px-3 py-2', status.className)}>
          <div className="flex min-w-0 items-center gap-2">
            <StatusIcon className={clsx('size-4 shrink-0', chunk.vectorStatus === 'PROCESSING' && 'animate-spin')} />
            <div className="min-w-0">
              <div className="text-xs font-bold">{status.label}</div>
              <div className="mt-0.5 truncate text-[10px] font-medium opacity-75">{chunk.errorMsg || status.detail}</div>
            </div>
          </div>
          {(chunk.vectorStatus === 'FAILED' || isTimedOut) && (
            <button
              type="button"
              onClick={chunk.vectorStatus === 'FAILED' ? onRetry : onRecover}
              disabled={isRetrying}
              className="inline-flex h-7 shrink-0 items-center gap-1 rounded border border-current/20 bg-white/60 px-2 text-[10px] font-bold hover:bg-white disabled:opacity-50"
            >
              <RefreshCw className={clsx('size-3', isRetrying && 'animate-spin')} />
              {chunk.vectorStatus === 'FAILED' ? '重新提交' : '恢复任务'}
            </button>
          )}
        </div>

        <label className="mb-1.5 text-[11px] font-bold text-gray-600" htmlFor="chunk-name">
          分块名称
        </label>
        <div className="mb-4 flex gap-2">
          <input
            id="chunk-name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            disabled={isSaving}
            className="h-9 min-w-0 flex-1 rounded-md border border-gray-200 px-3 text-sm font-bold text-gray-900 outline-none transition-colors focus:border-gray-400"
          />
          <button
            type="button"
            onClick={onGenerateName}
            disabled={isGeneratingName || isDirty}
            title={isDirty ? '请先保存当前修改' : '使用 AI 重新生成名称'}
            className="inline-flex h-9 items-center gap-1.5 rounded-md border border-gray-200 bg-white px-3 text-xs font-bold text-gray-600 transition-colors hover:bg-gray-50 hover:text-gray-900 disabled:opacity-50"
          >
            {isGeneratingName ? <Loader2 className="size-3.5 animate-spin" /> : <Sparkles className="size-3.5" />}
            AI 命名
          </button>
        </div>

        <div className="mb-1.5 flex items-center justify-between">
          <label className="text-[11px] font-bold text-gray-600" htmlFor="chunk-content">
            分块正文
          </label>
          <span className="font-mono text-[10px] font-medium text-gray-400">{content.length} 字符</span>
        </div>
        {mode === 'edit' ? (
          <textarea
            id="chunk-content"
            value={content}
            onChange={(event) => setContent(event.target.value)}
            disabled={isSaving}
            className="min-h-[30rem] flex-1 resize-none rounded-md border border-gray-200 bg-gray-50/30 p-4 font-mono text-[13px] leading-6 text-gray-800 outline-none transition-colors focus:border-gray-400 focus:bg-white"
          />
        ) : (
          <article className="min-h-[30rem] flex-1 select-text rounded-md border border-gray-200 bg-white p-5 text-sm leading-7 text-gray-700 [&_a]:text-blue-600 [&_blockquote]:border-l-2 [&_blockquote]:border-gray-300 [&_blockquote]:pl-3 [&_code]:rounded [&_code]:bg-gray-100 [&_code]:px-1 [&_h1]:mb-4 [&_h1]:text-xl [&_h1]:font-bold [&_h2]:mb-3 [&_h2]:mt-5 [&_h2]:text-lg [&_h2]:font-bold [&_h3]:mb-2 [&_h3]:mt-4 [&_h3]:font-bold [&_li]:ml-5 [&_ol]:list-decimal [&_p]:mb-3 [&_pre]:overflow-x-auto [&_pre]:rounded-md [&_pre]:bg-gray-950 [&_pre]:p-4 [&_pre]:text-gray-100 [&_table]:w-full [&_table]:border-collapse [&_td]:border [&_td]:border-gray-200 [&_td]:p-2 [&_th]:border [&_th]:border-gray-200 [&_th]:bg-gray-50 [&_th]:p-2 [&_ul]:list-disc">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
          </article>
        )}
      </div>
    </section>
  );
};
