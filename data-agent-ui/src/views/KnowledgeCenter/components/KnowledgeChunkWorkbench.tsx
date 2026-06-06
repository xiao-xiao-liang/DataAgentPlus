import React from 'react';
import { ArrowLeft, FileText, RefreshCw } from 'lucide-react';
import type { KnowledgeChunk, KnowledgeChunkUpdateResult } from '../types';
import { KnowledgeChunkOutline } from './KnowledgeChunkOutline';
import { KnowledgeChunkEditor } from './KnowledgeChunkEditor';

interface KnowledgeChunkWorkbenchProps {
  agentId: string;
  knowledgeId: number;
  fileName: string;
  knowledgeBaseName: string;
  onBack: () => void;
  showToast: (message: string, type?: 'success' | 'error') => void;
}

const readResult = async <T,>(response: Response): Promise<T> => {
  const result = await response.json();
  if (!response.ok || result.code !== '0') {
    throw new Error(result.message || '请求失败');
  }
  return result.data as T;
};

const POLL_INTERVAL_MS = 3000;

export const KnowledgeChunkWorkbench: React.FC<KnowledgeChunkWorkbenchProps> = ({
  agentId,
  knowledgeId,
  fileName,
  knowledgeBaseName,
  onBack,
  showToast,
}) => {
  const [chunks, setChunks] = React.useState<KnowledgeChunk[]>([]);
  const [selectedId, setSelectedId] = React.useState<string | null>(null);
  const [selectedChunk, setSelectedChunk] = React.useState<KnowledgeChunk | null>(null);
  const [keyword, setKeyword] = React.useState('');
  const [statusFilter, setStatusFilter] = React.useState('ALL');
  const [isLoadingList, setIsLoadingList] = React.useState(false);
  const [isLoadingDetail, setIsLoadingDetail] = React.useState(false);
  const [isSaving, setIsSaving] = React.useState(false);
  const [isRetrying, setIsRetrying] = React.useState(false);
  const [isGeneratingName, setIsGeneratingName] = React.useState(false);
  const [isDirty, setIsDirty] = React.useState(false);
  const selectedIdRef = React.useRef<string | null>(null);
  const detailAbortRef = React.useRef<AbortController | null>(null);

  React.useEffect(() => {
    selectedIdRef.current = selectedId;
  }, [selectedId]);

  const loadChunks = React.useCallback(async (silent = false) => {
    if (!silent) setIsLoadingList(true);
    try {
      const params = new URLSearchParams({ agentId });
      if (keyword.trim()) params.set('keyword', keyword.trim());
      if (statusFilter !== 'ALL') params.set('vectorStatus', statusFilter);
      const data = await readResult<KnowledgeChunk[]>(
        await fetch(`/api/v1/agent-knowledge/${knowledgeId}/chunks?${params.toString()}`),
      );
      setChunks(data);
      setSelectedId((current) => isDirty && current
        ? current
        : current && data.some((chunk) => chunk.id === current) ? current : data[0]?.id || null);
      setSelectedChunk((current) => {
        if (!current) return current;
        if (isDirty) return current;
        const outline = data.find((chunk) => chunk.id === current.id);
        return outline ? { ...current, ...outline } : current;
      });
    } catch (error) {
      if (!silent) showToast(error instanceof Error ? error.message : '加载分块失败', 'error');
    } finally {
      if (!silent) setIsLoadingList(false);
    }
  }, [agentId, isDirty, knowledgeId, keyword, showToast, statusFilter]);

  const loadDetail = React.useCallback(async (chunkId: string, silent = false) => {
    detailAbortRef.current?.abort();
    const controller = new AbortController();
    detailAbortRef.current = controller;
    if (!silent) setIsLoadingDetail(true);
    try {
      const data = await readResult<KnowledgeChunk>(
        await fetch(`/api/v1/agent-knowledge/${knowledgeId}/chunks/${chunkId}?agentId=${agentId}`, { signal: controller.signal }),
      );
      if (selectedIdRef.current === chunkId) {
        setSelectedChunk(data);
      }
    } catch (error) {
      if (!silent && !(error instanceof DOMException && error.name === 'AbortError')) {
        showToast(error instanceof Error ? error.message : '加载分块详情失败', 'error');
      }
    } finally {
      if (!silent) setIsLoadingDetail(false);
    }
  }, [agentId, knowledgeId, showToast]);

  React.useEffect(() => {
    const timer = window.setTimeout(() => void loadChunks(), 250);
    return () => window.clearTimeout(timer);
  }, [loadChunks]);

  React.useEffect(() => {
    const timer = window.setTimeout(() => {
      if (selectedId) void loadDetail(selectedId);
      else setSelectedChunk(null);
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadDetail, selectedId]);

  React.useEffect(() => {
    const hasPending = chunks.some((chunk) => chunk.vectorStatus === 'PENDING' || chunk.vectorStatus === 'PROCESSING');
    if (!hasPending) return undefined;
    const timer = window.setInterval(() => {
      void loadChunks(true);
    }, POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [chunks, loadChunks, loadDetail, selectedId]);

  const applyResult = (result: KnowledgeChunkUpdateResult, successMessage: string) => {
    setSelectedChunk(result.detail);
    setChunks((current) => current.map((chunk) => chunk.id === result.detail.id ? { ...chunk, ...result.detail } : chunk));
    showToast(result.messageSubmitted ? successMessage : '内容已保存，但异步任务提交失败，请稍后手动重试', result.messageSubmitted ? 'success' : 'error');
  };

  const handleSave = async (name: string, content: string) => {
    if (!selectedChunk) return;
    setIsSaving(true);
    try {
      const result = await readResult<KnowledgeChunkUpdateResult>(
        await fetch(`/api/v1/agent-knowledge/${knowledgeId}/chunks/${selectedChunk.id}?agentId=${agentId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            name: name.trim(),
            content,
            contentVersion: selectedChunk.contentVersion,
            manualNameChanged: name.trim() !== selectedChunk.name,
          }),
        }),
      );
      applyResult(result, '分块已保存，正在重新向量化');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '保存分块失败', 'error');
    } finally {
      setIsSaving(false);
    }
  };

  const handleRetry = async () => {
    if (!selectedChunk) return;
    setIsRetrying(true);
    try {
      const result = await readResult<KnowledgeChunkUpdateResult>(
        await fetch(`/api/v1/agent-knowledge/${knowledgeId}/chunks/${selectedChunk.id}/retry?agentId=${agentId}`, { method: 'POST' }),
      );
      applyResult(result, '已重新提交向量化任务');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '重新提交失败', 'error');
    } finally {
      setIsRetrying(false);
    }
  };

  const handleRecover = async () => {
    if (!selectedChunk) return;
    setIsRetrying(true);
    try {
      const result = await readResult<KnowledgeChunkUpdateResult>(
        await fetch(`/api/v1/agent-knowledge/${knowledgeId}/chunks/${selectedChunk.id}/recover?agentId=${agentId}`, { method: 'POST' }),
      );
      applyResult(result, '已恢复超时向量任务');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '恢复任务失败', 'error');
    } finally {
      setIsRetrying(false);
    }
  };

  const handleGenerateName = async () => {
    if (!selectedChunk) return;
    setIsGeneratingName(true);
    try {
      const result = await readResult<KnowledgeChunkUpdateResult>(
        await fetch(`/api/v1/agent-knowledge/${knowledgeId}/chunks/${selectedChunk.id}/generate-name?agentId=${agentId}`, { method: 'POST' }),
      );
      applyResult(result, '已提交 AI 命名任务');
      window.setTimeout(() => void loadChunks(true), POLL_INTERVAL_MS);
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'AI 命名提交失败', 'error');
    } finally {
      setIsGeneratingName(false);
    }
  };

  return (
    <div className="flex h-full min-h-0 w-full flex-col overflow-hidden bg-white select-none">
      <header className="flex h-[4.75rem] flex-none items-center justify-between gap-4 border-b border-gray-200 px-5">
        <div className="flex min-w-0 items-center gap-3">
          <button
            type="button"
            onClick={() => {
              if (!isDirty || window.confirm('当前分块存在未保存修改，确定离开吗？')) onBack();
            }}
            title="返回文件列表"
            className="flex size-9 shrink-0 items-center justify-center rounded-md border border-gray-200 bg-white text-gray-500 transition-colors hover:bg-gray-50 hover:text-gray-900"
          >
            <ArrowLeft className="size-4" />
          </button>
          <div className="flex size-9 shrink-0 items-center justify-center rounded-md bg-gray-100 text-gray-600">
            <FileText className="size-4" />
          </div>
          <div className="min-w-0">
            <div className="truncate text-sm font-bold text-gray-900">{fileName}</div>
            <div className="mt-1 flex items-center gap-1.5 truncate text-[10px] font-semibold text-gray-400">
              <span>{knowledgeBaseName}</span>
              <span>/</span>
              <span>{chunks.length} 个分块</span>
            </div>
          </div>
        </div>
        <button
          type="button"
          onClick={() => void loadChunks()}
          disabled={isLoadingList}
          title="刷新分块列表"
          className="flex size-9 shrink-0 items-center justify-center rounded-md border border-gray-200 bg-white text-gray-500 transition-colors hover:bg-gray-50 hover:text-gray-900 disabled:opacity-50"
        >
          <RefreshCw className={isLoadingList ? 'size-4 animate-spin' : 'size-4'} />
        </button>
      </header>
      <div className="flex min-h-0 flex-1 max-md:flex-col">
        <KnowledgeChunkOutline
          chunks={chunks}
          selectedId={selectedId}
          keyword={keyword}
          statusFilter={statusFilter}
          isLoading={isLoadingList}
          onKeywordChange={setKeyword}
          onStatusFilterChange={setStatusFilter}
          onSelect={(chunkId) => {
            if (!isDirty || window.confirm('当前分块存在未保存修改，确定切换吗？')) setSelectedId(chunkId);
          }}
        />
        <KnowledgeChunkEditor
          key={selectedChunk ? `${selectedChunk.id}-${selectedChunk.contentVersion}-${selectedChunk.name}` : 'empty'}
          chunk={selectedChunk}
          isLoading={isLoadingDetail}
          isSaving={isSaving}
          isRetrying={isRetrying}
          isGeneratingName={isGeneratingName}
          onSave={handleSave}
          onRetry={handleRetry}
          onRecover={handleRecover}
          onGenerateName={handleGenerateName}
          onDirtyChange={setIsDirty}
        />
      </div>
    </div>
  );
};
