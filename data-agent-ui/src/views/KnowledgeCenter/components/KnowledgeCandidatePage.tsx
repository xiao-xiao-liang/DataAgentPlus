import React, { useEffect, useMemo, useState } from 'react';
import { ArrowLeft, CheckCircle2, Clock3, RefreshCw, Search, XCircle } from 'lucide-react';
import clsx from 'clsx';
import type { KnowledgeCandidate } from '../types';
import { buildKnowledgeCandidateViewModel } from '../knowledgeCandidatePresentation';
import { KnowledgeCandidateCard } from './KnowledgeCandidateCard';

interface KnowledgeCandidatePageProps {
  agentId: string;
  onBack: () => void;
  showToast: (message: string, type?: 'success' | 'error') => void;
}

const STATUS_OPTIONS = [
  { label: '全部', value: 'ALL' },
  { label: '待处理', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已驳回', value: 'REJECTED' },
];

export const KnowledgeCandidatePage: React.FC<KnowledgeCandidatePageProps> = ({
  agentId,
  onBack,
  showToast,
}) => {
  const [items, setItems] = useState<KnowledgeCandidate[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedStatus, setSelectedStatus] = useState('ALL');
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const load = async () => {
    if (!agentId) return;
    setLoading(true);
    try {
      const response = await fetch(`/api/v1/knowledge-candidates?agentId=${agentId}`);
      const result = await response.json();
      setItems(Array.isArray(result.data) ? result.data : []);
    } catch (error) {
      console.error('加载候选知识失败', error);
      showToast('加载候选知识失败，请稍后重试', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [agentId]);

  const filteredItems = useMemo(() => {
    return items.filter((item) => {
      if (selectedStatus !== 'ALL' && item.status !== selectedStatus) {
        return false;
      }

      if (!searchQuery.trim()) {
        return true;
      }

      const keyword = searchQuery.trim().toLowerCase();
      const viewModel = buildKnowledgeCandidateViewModel(item);
      const haystack = [
        item.title,
        item.sourceQuestion,
        viewModel.businessTerm,
        viewModel.description,
        viewModel.calculationRule,
        ...viewModel.synonyms,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();

      return haystack.includes(keyword);
    });
  }, [items, searchQuery, selectedStatus]);

  const activeItem = useMemo(() => {
    if (filteredItems.length === 0) return null;
    return filteredItems.find(item => item.id === selectedId) || filteredItems[0];
  }, [filteredItems, selectedId]);

  const activeViewModel = useMemo(() => {
    if (!activeItem) return null;
    return buildKnowledgeCandidateViewModel(activeItem);
  }, [activeItem]);

  const formattedJsonContent = useMemo(() => {
    if (!activeItem || !activeItem.normalizedContent) return '无原始内容';
    const raw = activeItem.normalizedContent.trim();
    if (raw.startsWith('{') || raw.startsWith('[')) {
      try {
        const parsed = JSON.parse(raw);
        return JSON.stringify(parsed, null, 2);
      } catch (e) {
        return raw;
      }
    }
    return raw;
  }, [activeItem]);

  const stats = useMemo(() => {
    return {
      total: items.length,
      draft: items.filter((item) => item.status === 'DRAFT').length,
      published: items.filter((item) => item.status === 'PUBLISHED').length,
      rejected: items.filter((item) => item.status === 'REJECTED').length,
    };
  }, [items]);

  const action = async (id: number, type: 'publish' | 'reject') => {
    try {
      const response = await fetch(`/api/v1/knowledge-candidates/${id}/${type}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body:
          type === 'publish'
            ? JSON.stringify({ targetType: 'BUSINESS_KNOWLEDGE' })
            : JSON.stringify({ reviewComment: '人工驳回' }),
      });

      if (!response.ok) {
        throw new Error(`请求失败: ${response.status}`);
      }

      showToast(type === 'publish' ? '候选知识已发布' : '候选知识已驳回', 'success');
      await load();
    } catch (error) {
      console.error('处理候选知识失败', error);
      showToast(type === 'publish' ? '发布失败，请稍后重试' : '驳回失败，请稍后重试', 'error');
    }
  };

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div className="flex h-[3.75rem] items-center justify-between px-6 py-4 flex-none">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={onBack}
            className="inline-flex h-9 items-center gap-2 rounded-md border border-gray-200 px-3 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-50"
          >
            <ArrowLeft className="size-4" />
            返回知识库
          </button>
          <div>
            <div className="text-base font-bold text-gray-900">候选知识</div>
            <div className="text-xs text-gray-550">在这里完成候选知识的筛选、审核与发布。</div>
          </div>
        </div>

        <button
          type="button"
          onClick={load}
          className="inline-flex h-9 items-center gap-2 rounded-md border border-gray-200 bg-white px-3 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-50"
        >
          <RefreshCw className={clsx('size-4', loading && 'animate-spin')} />
          刷新
        </button>
      </div>

      {/* 统计排版 */}
      <div className="grid grid-cols-1 gap-4 px-6 md:grid-cols-4 flex-none">
        <div className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
          <div className="text-xs font-medium text-gray-500">总数</div>
          <div className="mt-2 text-2xl font-semibold text-gray-900">{stats.total}</div>
        </div>
        <div className="rounded-lg border border-amber-200 bg-amber-50/60 p-4 shadow-sm">
          <div className="text-xs font-medium text-amber-700">待处理</div>
          <div className="mt-2 text-2xl font-semibold text-amber-900">{stats.draft}</div>
        </div>
        <div className="rounded-lg border border-emerald-200 bg-emerald-50/60 p-4 shadow-sm">
          <div className="text-xs font-medium text-emerald-700">已发布</div>
          <div className="mt-2 text-2xl font-semibold text-emerald-900">{stats.published}</div>
        </div>
        <div className="rounded-lg border border-rose-200 bg-rose-50/60 p-4 shadow-sm">
          <div className="text-xs font-medium text-rose-700">已驳回</div>
          <div className="mt-2 text-2xl font-semibold text-rose-900">{stats.rejected}</div>
        </div>
      </div>

      {/* 过滤搜索栏 */}
      <div className="mt-4 flex flex-wrap items-center gap-3 px-6 flex-none">
        <div className="relative min-w-[280px] flex-1">
          <input
            className="h-10 w-full rounded-md border border-gray-200 bg-white pl-10 pr-3 text-sm text-gray-700 outline-none transition-all placeholder:text-gray-400 focus:border-[#B8C0F5] focus:ring-2 focus:ring-[#E3E7FF]"
            placeholder="搜索候选知识标题、业务术语或描述"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          <Search className="pointer-events-none absolute left-3 top-3.5 size-4 text-gray-400" />
        </div>

        <div className="flex flex-wrap gap-2">
          {STATUS_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              onClick={() => setSelectedStatus(option.value)}
              className={clsx(
                'inline-flex h-9 items-center rounded-full border px-4 text-sm font-medium transition-colors',
                selectedStatus === option.value
                  ? 'border-[#C9D2FF] bg-[#F5F7FF] text-[#2D336B]'
                  : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50',
              )}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      {/* Master-Detail 左右双栏结构 */}
      <div className="mt-4 flex min-h-0 flex-1 gap-4 px-6 pb-6 overflow-hidden">
        {/* 左侧：精简的高密卡片列表 */}
        <div className="w-[340px] shrink-0 flex flex-col gap-2 overflow-y-auto pr-1">
          {loading ? (
            <div className="space-y-3">
              {[1, 2, 3, 4].map((item) => (
                <div key={item} className="h-[76px] animate-pulse rounded-xl border border-gray-150 bg-gray-50/50" />
              ))}
            </div>
          ) : filteredItems.length === 0 ? (
            <div className="flex h-40 flex-col items-center justify-center rounded-xl border border-dashed border-gray-200 bg-gray-50/30 text-center text-xs text-gray-400">
              暂无匹配数据
            </div>
          ) : (
            filteredItems.map((item) => (
              <KnowledgeCandidateCard
                key={item.id}
                item={item}
                viewModel={buildKnowledgeCandidateViewModel(item)}
                isActive={activeItem?.id === item.id}
                onClick={() => setSelectedId(item.id)}
              />
            ))
          )}
        </div>

        {/* 右侧：常驻详情与审批大面板 */}
        <div className="flex-1 rounded-xl border border-gray-200 bg-white p-5 shadow-xs flex flex-col min-h-0 overflow-y-auto">
          {loading ? (
            <div className="space-y-4 animate-pulse">
              <div className="h-6 w-1/3 rounded bg-gray-200" />
              <div className="h-px bg-gray-105" />
              <div className="grid grid-cols-3 gap-4">
                <div className="col-span-1 h-32 rounded bg-gray-100" />
                <div className="col-span-2 h-32 rounded bg-gray-100" />
              </div>
            </div>
          ) : !activeItem || !activeViewModel ? (
            <div className="flex flex-1 flex-col items-center justify-center p-8 py-24 text-xs font-semibold text-gray-400">
              请选择左侧项查看候选知识详情
            </div>
          ) : (
            <div className="flex flex-col gap-4 min-h-0 select-text">
              {/* 详情标题与动作栏 */}
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between border-b border-gray-100 pb-4">
                <div className="min-w-0 flex-1">
                  <h3 className="text-base font-bold text-gray-900 leading-6 break-words">
                    {activeItem.title}
                  </h3>
                  <p className="mt-1 text-xs text-gray-500 leading-relaxed whitespace-pre-wrap select-all">
                    {activeViewModel.description || '该候选知识暂未生成摘要说明。'}
                  </p>
                </div>

                <div className="flex shrink-0 items-center gap-3">
                  {/* 置信度评分 Badge */}
                  {activeItem.confidenceScore && (
                    <div className="flex items-center gap-1 rounded-lg bg-indigo-50/60 border border-indigo-100 px-2.5 py-1">
                      <span className="text-[10px] font-semibold text-indigo-500">置信度</span>
                      <span className="text-xs font-bold text-indigo-700">
                        {Math.round(activeItem.confidenceScore * 100)}%
                      </span>
                    </div>
                  )}

                  {/* 操作按钮 */}
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => action(activeItem.id, 'reject')}
                      disabled={activeItem.status === 'PUBLISHED' || activeItem.status === 'REJECTED'}
                      className="inline-flex h-8 items-center gap-1 rounded-lg border border-gray-250 px-2.5 text-xs font-semibold text-gray-700 transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:text-gray-300 disabled:border-gray-200"
                    >
                      <XCircle className="size-3.5" />
                      驳回
                    </button>
                    <button
                      type="button"
                      onClick={() => action(activeItem.id, 'publish')}
                      disabled={activeItem.status === 'PUBLISHED'}
                      className="inline-flex h-8 items-center gap-1 rounded-lg bg-[#1F2A52] px-2.5 text-xs font-semibold text-white transition-colors hover:bg-[#283568] disabled:cursor-not-allowed disabled:bg-gray-300"
                    >
                      <CheckCircle2 className="size-3.5" />
                      发布
                    </button>
                  </div>
                </div>
              </div>

              {/* 属性及核心内容（2x2 均匀网格布局） */}
              <div className="grid gap-4 md:grid-cols-2">
                {/* 1. 业务术语 */}
                <section className="rounded-lg border border-gray-200 bg-white p-3.5 shadow-xs flex flex-col justify-between">
                  <div>
                    <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">业务术语</div>
                    <div className="mt-1.5 text-xs font-bold leading-5 text-gray-900 select-all">{activeViewModel.businessTerm}</div>
                  </div>
                </section>

                {/* 2. 口径 / 规则 */}
                <section className="rounded-lg border border-gray-200 bg-white p-3.5 shadow-xs flex flex-col justify-between">
                  <div>
                    <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">口径 / 规则</div>
                    <div className="mt-1.5 text-xs leading-5 text-gray-750 select-all">
                      {activeViewModel.calculationRule || '暂未提炼明确口径规则。'}
                    </div>
                  </div>
                </section>

                {/* 3. 同义说法 */}
                <section className="rounded-lg border border-gray-200 bg-white p-3.5 shadow-xs flex flex-col justify-between">
                  <div>
                    <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">同义说法</div>
                    <div className="mt-2 flex flex-wrap gap-1.5 select-all">
                      {activeViewModel.synonyms.length > 0 ? (
                        activeViewModel.synonyms.map((synonym) => (
                          <span
                            key={synonym}
                            className="inline-flex items-center rounded-full border border-gray-200 bg-gray-50 px-2 py-0.5 text-[10px] font-semibold text-gray-700"
                          >
                            {synonym}
                          </span>
                        ))
                      ) : (
                        <span className="text-xs text-gray-400">无同义说法</span>
                      )}
                    </div>
                  </div>
                </section>

                {/* 4. 来源问题 */}
                <section className="rounded-lg border border-gray-200 bg-white p-3.5 shadow-xs flex flex-col justify-between">
                  <div>
                    <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">来源问题</div>
                    <div className="mt-1.5 text-xs leading-relaxed text-gray-600 select-all">
                      {activeItem.sourceQuestion || '无来源问题'}
                    </div>
                  </div>
                </section>
              </div>

              {/* 原始内容 (常驻格式化 JSON 视图) */}
              <section className="rounded-lg border border-gray-200 bg-white p-3.5 shadow-xs">
                <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">原始内容 (JSON)</div>
                <div className="rounded-lg border border-gray-150 bg-gray-50/70 p-3">
                  <pre className="font-mono text-[11px] leading-5 text-indigo-900 select-all whitespace-pre-wrap break-all">
                    {formattedJsonContent}
                  </pre>
                </div>
              </section>

              {/* 底部时间 */}
              <div className="flex items-center gap-2 text-[10px] text-gray-400 select-none">
                <Clock3 className="size-3.5" />
                <span>最近更新：{activeItem.updateTime || activeItem.createTime || '未记录'}</span>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
