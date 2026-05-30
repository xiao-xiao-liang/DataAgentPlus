import React, { useEffect, useState } from 'react';
import { CheckCircle2, RefreshCw, XCircle } from 'lucide-react';
import type { KnowledgeCandidate } from '../types';

interface KnowledgeCandidateListProps {
  agentId: string;
  showToast: (message: string) => void;
}

export const KnowledgeCandidateList: React.FC<KnowledgeCandidateListProps> = ({ agentId, showToast }) => {
  const [items, setItems] = useState<KnowledgeCandidate[]>([]);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    if (!agentId) return;
    setLoading(true);
    try {
      const response = await fetch(`/api/v1/knowledge-candidates?agentId=${agentId}`);
      const result = await response.json();
      setItems(Array.isArray(result.data) ? result.data : []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [agentId]);

  const action = async (id: number, type: 'publish' | 'reject') => {
    await fetch(`/api/v1/knowledge-candidates/${id}/${type}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: type === 'publish' ? JSON.stringify({ targetType: 'BUSINESS_KNOWLEDGE' }) : JSON.stringify({ reviewComment: '人工驳回' }),
    });
    showToast(type === 'publish' ? '候选知识已发布' : '候选知识已驳回');
    await load();
  };

  return (
    <div className="mx-6 rounded-lg border border-gray-200 bg-white">
      <div className="flex h-11 items-center justify-between border-b border-gray-100 px-4">
        <div className="text-sm font-bold text-gray-800">候选知识</div>
        <button
          type="button"
          onClick={load}
          className="inline-flex h-7 items-center gap-1 rounded-md border border-gray-200 px-2 text-xs text-gray-600 hover:bg-gray-50"
        >
          <RefreshCw className={loading ? 'size-3 animate-spin' : 'size-3'} />
          刷新
        </button>
      </div>
      <div className="max-h-[260px] overflow-y-auto">
        {items.length === 0 ? (
          <div className="px-4 py-6 text-center text-xs text-gray-400">暂无候选知识</div>
        ) : (
          items.map(item => (
            <div key={item.id} className="grid grid-cols-[1fr_auto] gap-3 border-b border-gray-100 px-4 py-3 last:border-b-0">
              <div className="min-w-0">
                <div className="truncate text-sm font-semibold text-gray-800">{item.title}</div>
                <div className="mt-1 text-xs text-gray-500">
                  {item.candidateType} · {item.scope} · {item.status}
                  {item.confidenceScore ? ` · ${(item.confidenceScore * 100).toFixed(0)}%` : ''}
                </div>
                <div className="mt-2 line-clamp-2 text-xs leading-5 text-gray-600">{item.normalizedContent}</div>
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => action(item.id, 'publish')}
                  disabled={item.status === 'PUBLISHED'}
                  className="inline-flex h-8 items-center gap-1 rounded-md bg-gray-900 px-2.5 text-xs font-medium text-white hover:bg-gray-700 disabled:bg-gray-300"
                >
                  <CheckCircle2 className="size-3.5" />
                  发布
                </button>
                <button
                  type="button"
                  onClick={() => action(item.id, 'reject')}
                  disabled={item.status === 'REJECTED' || item.status === 'PUBLISHED'}
                  className="inline-flex h-8 items-center gap-1 rounded-md border border-gray-200 px-2.5 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:text-gray-300"
                >
                  <XCircle className="size-3.5" />
                  驳回
                </button>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
