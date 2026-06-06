import React, { useEffect, useMemo, useState } from 'react';
import { AlertCircle, CheckCircle2, RefreshCw, Search } from 'lucide-react';
import clsx from 'clsx';
import { useLocation, useNavigate, useOutletContext, useParams } from 'react-router-dom';
import type { KnowledgeBase, KnowledgeFile } from './types';
import { CreateKnowledgeDialog } from './components/CreateKnowledgeDialog';
import { KnowledgeList } from './components/KnowledgeList';
import { KnowledgeDetail } from './components/KnowledgeDetail';
import { KnowledgeCandidatePage } from './components/KnowledgeCandidatePage';
import { KnowledgeChunkWorkbench } from './components/KnowledgeChunkWorkbench';
import { CollapsedSidebarMenuButton } from '../../layout/CollapsedSidebarMenuButton';
import type { LayoutOutletContext } from '../../layout/GlobalLayout';
import { useCurrentAgentStore } from '../../stores/currentAgent';

type AgentKnowledgeResponse = {
  id: number;
  title?: string;
  sourceFilename?: string;
  fileSize?: number;
  embeddingStatus?: string;
  errorMsg?: string;
  splitterType?: string;
  createTime?: string;
  updateTime?: string;
};

const formatFileSize = (bytes?: number): string => {
  if (!bytes) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB'];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(k)), sizes.length - 1);
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
};

const formatBackendTime = (value?: string): string => {
  if (!value) return new Date().toISOString().replace('T', ' ').substring(0, 19);
  return value.replace('T', ' ').substring(0, 19);
};

const mapStatus = (status?: string): KnowledgeFile['status'] => {
  if (status === 'COMPLETED') return 'success';
  if (status === 'FAILED') return 'failed';
  if (status === 'DELETING') return 'deleting';
  if (status === 'DELETE_FAILED') return 'delete_failed';
  if (status === 'PROCESSING' || status === 'PENDING') return 'parsing';
  return 'success';
};

const buildAgentKnowledgeBase = (agentId: string, items: AgentKnowledgeResponse[]): KnowledgeBase => ({
  id: `kb-agent-${agentId}`,
  name: '智能体知识库',
  creator: 'Data Agent',
  status: items.some((item) => item.embeddingStatus === 'FAILED' || item.embeddingStatus === 'DELETE_FAILED') ? 'failed' : 'ready',
  updatedAt: formatBackendTime(items[0]?.updateTime),
  fileCount: items.length,
  description: '当前智能体真实上传并向量化的知识文件',
  files: items.map((item) => ({
    id: `knowledge-${item.id}`,
    backendId: item.id,
    name: item.sourceFilename || item.title || `knowledge-${item.id}`,
    size: formatFileSize(item.fileSize),
    status: mapStatus(item.embeddingStatus),
    progress: mapStatus(item.embeddingStatus) === 'success' ? 100 : undefined,
    uploadedAt: formatBackendTime(item.createTime),
    splitterType: item.splitterType,
    errorMsg: item.errorMsg,
  })),
});

export const KnowledgeCenter: React.FC = () => {
  const {
    isSidebarCollapsed,
    isSidebarVisible,
    expandSidebar,
  } = useOutletContext<LayoutOutletContext>();
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [toastType, setToastType] = useState<'success' | 'error'>('success');
  const [candidateCount, setCandidateCount] = useState(0);

  const { knowledgeBaseId, knowledgeId } = useParams<{ knowledgeBaseId: string; knowledgeId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const currentAgentId = useCurrentAgentStore((state) => state.agentId);
  const setCurrentAgent = useCurrentAgentStore((state) => state.setCurrentAgent);
  const effectiveAgentId = currentAgentId && currentAgentId !== 'default' ? currentAgentId : '1';
  const isCandidatePage = location.pathname === '/knowledge/candidates';

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const queryAgentId = params.get('agentId');
    if (!queryAgentId || queryAgentId === 'default') {
      return;
    }

    setCurrentAgent({ agentId: queryAgentId });
    params.delete('agentId');
    const nextSearch = params.toString();
    navigate(
      {
        pathname: location.pathname,
        search: nextSearch ? `?${nextSearch}` : '',
        hash: location.hash,
      },
      { replace: true, state: location.state }
    );
  }, [location.hash, location.pathname, location.search, location.state, navigate, setCurrentAgent]);

  useEffect(() => {
    const loadAgentKnowledge = async () => {
      setIsLoading(true);
      try {
        const response = await fetch(`/api/v1/agent-knowledge?agentId=${effectiveAgentId}`);
        const result = await response.json();
        const items = Array.isArray(result.data) ? result.data : [];
        setKnowledgeBases([buildAgentKnowledgeBase(effectiveAgentId, items)]);
      } catch (error) {
        console.error('加载智能体知识库失败', error);
        setKnowledgeBases([buildAgentKnowledgeBase(effectiveAgentId, [])]);
        showToast('加载智能体知识库失败', 'error');
      } finally {
        setIsLoading(false);
      }
    };

    loadAgentKnowledge();
  }, [effectiveAgentId]);

  useEffect(() => {
    if (!effectiveAgentId) return;

    const loadCandidateCount = async () => {
      try {
        const response = await fetch(`/api/v1/knowledge-candidates?agentId=${effectiveAgentId}`);
        const result = await response.json();
        setCandidateCount(Array.isArray(result.data) ? result.data.length : 0);
      } catch (error) {
        console.error('加载候选知识数量失败', error);
        setCandidateCount(0);
      }
    };

    loadCandidateCount();
  }, [effectiveAgentId, location.pathname]);

  useEffect(() => {
    if (!toastMessage) return undefined;

    const timer = window.setTimeout(() => {
      setToastMessage(null);
    }, 3000);

    return () => window.clearTimeout(timer);
  }, [toastMessage]);

  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToastMessage(message);
    setToastType(type);
  };

  const handleRefresh = () => {
    if (isLoading) return;
    setIsLoading(true);

    fetch(`/api/v1/agent-knowledge?agentId=${effectiveAgentId}`)
      .then((response) => response.json())
      .then((result) => {
        const items = Array.isArray(result.data) ? result.data : [];
        setKnowledgeBases([buildAgentKnowledgeBase(effectiveAgentId, items)]);
        showToast('知识库列表已刷新', 'success');
      })
      .catch((error) => {
        console.error('刷新知识库列表失败', error);
        showToast('知识库列表刷新失败', 'error');
      })
      .finally(() => {
        setIsLoading(false);
      });
  };

  const filteredBases = useMemo(() => {
    return knowledgeBases.filter((kb) => kb.name.toLowerCase().includes(searchQuery.toLowerCase()));
  }, [knowledgeBases, searchQuery]);

  const currentKB = useMemo(() => {
    if (isCandidatePage || !knowledgeBaseId) return null;
    return knowledgeBases.find((kb) => kb.id === knowledgeBaseId) || null;
  }, [isCandidatePage, knowledgeBaseId, knowledgeBases]);

  const handleDeleteKB = (id: string) => {
    const targetKB = knowledgeBases.find((kb) => kb.id === id);
    if (!targetKB) return;

    if (window.confirm(`确认删除知识库 "${targetKB.name}" 吗？此操作不可撤销。`)) {
      const updated = knowledgeBases.filter((kb) => kb.id !== id);
      setKnowledgeBases(updated);
      showToast(`已删除知识库 "${targetKB.name}"`, 'success');
      if (knowledgeBaseId === id) {
        navigate('/knowledge');
      }
    }
  };

  const handleUpdateKB = (updatedKB: KnowledgeBase) => {
    const updatedList = knowledgeBases.map((kb) => (kb.id === updatedKB.id ? updatedKB : kb));
    setKnowledgeBases(updatedList);
  };

  const handleConfirmCreate = (
    name: string,
    description: string,
    initialFile: { name: string; size: string; status: 'success' } | null,
  ) => {
    void name;
    void description;
    void initialFile;
    setIsCreateOpen(false);
    showToast('智能体知识库已自动创建，请直接进入后上传文件', 'success');
  };

  return (
    <div className="relative m-2 flex h-[calc(100%-1rem)] w-[calc(100%-1rem)] flex-col overflow-hidden rounded-lg border border-gray-200/80 bg-white font-sans shadow-sm select-none">
      <div className="absolute left-0 top-0 z-20 flex h-[3.75rem] w-12 items-center justify-between">
        <div className="flex items-center">
          <button className="mx-3 hidden size-7 items-center justify-center rounded-md p-1 text-gray-500 hover:bg-gray-100">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="24"
              height="24"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="h-4 w-4"
            >
              <path d="M4 5h16" />
              <path d="M4 12h16" />
              <path d="M4 19h16" />
            </svg>
          </button>
        </div>
      </div>

      {isCandidatePage ? (
        <KnowledgeCandidatePage agentId={effectiveAgentId} onBack={() => navigate('/knowledge')} showToast={showToast} />
      ) : currentKB && knowledgeId ? (
        <KnowledgeChunkWorkbench
          agentId={effectiveAgentId}
          knowledgeId={Number(knowledgeId)}
          fileName={currentKB.files.find((file) => file.backendId === Number(knowledgeId))?.name || `knowledge-${knowledgeId}`}
          knowledgeBaseName={currentKB.name}
          onBack={() => navigate(`/knowledge/${currentKB.id}?name=${encodeURIComponent(currentKB.name)}`)}
          showToast={showToast}
        />
      ) : currentKB ? (
        <KnowledgeDetail
          kb={currentKB}
          agentId={effectiveAgentId}
          onBack={() => navigate('/knowledge')}
          onUpdateKB={handleUpdateKB}
          showToast={showToast}
          onOpenFile={(file) => {
            if (file.backendId) {
              navigate(`/knowledge/${currentKB.id}/files/${file.backendId}/chunks?name=${encodeURIComponent(currentKB.name)}`);
            }
          }}
        />
      ) : (
        <div className="flex h-full flex-col overflow-hidden">
          <div className="flex h-[3.75rem] w-full items-center gap-3 px-6 py-4 text-sm text-gray-800">
            <CollapsedSidebarMenuButton
              isSidebarCollapsed={isSidebarCollapsed}
              isSidebarVisible={isSidebarVisible}
              expandSidebar={expandSidebar}
            />
            <span className="text-base font-bold text-gray-800">知识中心</span>
          </div>

          <div className="flex h-full w-full flex-1 flex-col gap-4 overflow-hidden pb-4">
            <div className="flex flex-none items-center gap-2 px-6 pt-4">
              <div className="relative max-w-[26rem] flex-1">
                <input
                  className="flex h-9 w-full rounded-md border border-gray-200 bg-white px-3 py-2 pl-8 text-xs font-semibold text-gray-750 transition-all placeholder-gray-400 focus:outline-none focus:ring-1 focus:ring-[#2D336B]"
                  placeholder="搜索知识库..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
                <Search className="pointer-events-none absolute left-2.5 top-3 h-3.5 w-3.5 text-gray-400" />
              </div>

              <button
                onClick={handleRefresh}
                className="inline-flex size-9 shrink-0 items-center justify-center rounded-md border border-gray-200 bg-white text-gray-400 transition-colors hover:bg-gray-50 hover:text-gray-700 active:scale-95"
                title="刷新"
              >
                <RefreshCw className={clsx('size-3.5', isLoading && 'animate-spin text-[#2D336B]')} />
              </button>

              <button
                type="button"
                onClick={() => navigate('/knowledge/candidates')}
                className="inline-flex h-9 items-center gap-2 rounded-md border border-gray-200 bg-white px-3 text-xs font-semibold text-gray-750 transition-colors hover:bg-gray-50"
              >
                <span>候选知识</span>
                <span className="inline-flex min-w-6 items-center justify-center rounded-full bg-[#F2F4FF] px-2 py-0.5 text-[11px] font-bold text-[#2D336B]">
                  {candidateCount}
                </span>
              </button>
            </div>

            {isLoading ? (
              <div className="grid min-h-0 flex-1 auto-rows-min grid-cols-1 items-start gap-4 overflow-y-auto px-6 md:grid-cols-2 lg:grid-cols-3">
                {[1, 2, 3].map((item) => (
                  <div
                    key={item}
                    className="flex h-[10rem] animate-pulse flex-col justify-between rounded-xl border border-gray-100 bg-gray-50/50 p-4"
                  >
                    <div className="space-y-3">
                      <div className="h-4 w-[45%] rounded bg-gray-200/80" />
                      <div className="h-3 w-[80%] rounded bg-gray-200/80" />
                      <div className="h-3 w-[60%] rounded bg-gray-200/80" />
                    </div>
                    <div className="h-3 w-[90%] rounded bg-gray-200/80" />
                  </div>
                ))}
              </div>
            ) : filteredBases.length === 0 ? (
              <div className="flex flex-1 flex-col items-center justify-center p-8 py-24 text-xs font-bold text-gray-400">
                暂无知识库数据
              </div>
            ) : (
              <KnowledgeList
                list={filteredBases}
                onCreateClick={() => {
                  const firstKb = filteredBases[0];
                  if (firstKb) {
                    navigate(`/knowledge/${firstKb.id}?name=${encodeURIComponent(firstKb.name)}`);
                  }
                }}
                onSelect={(id) => {
                  const kb = knowledgeBases.find((item) => item.id === id);
                  if (kb) {
                    navigate(`/knowledge/${kb.id}?name=${encodeURIComponent(kb.name)}`);
                  }
                }}
                onDelete={handleDeleteKB}
              />
            )}
          </div>
        </div>
      )}

      <CreateKnowledgeDialog isOpen={isCreateOpen} onOpenChange={setIsCreateOpen} onConfirm={handleConfirmCreate} />

      {toastMessage && (
        <div className="fixed left-1/2 top-5 z-[9999] flex -translate-x-1/2 items-center gap-2 rounded-xl bg-gray-900/95 px-4 py-2 text-xs font-bold text-white shadow-lg duration-200 animate-in fade-in slide-in-from-top-4 select-none backdrop-blur-xs">
          {toastType === 'success' ? (
            <CheckCircle2 className="h-4 w-4 animate-in fade-in text-emerald-400" />
          ) : (
            <AlertCircle className="h-4 w-4 text-rose-400 animate-in fade-in" />
          )}
          <span>{toastMessage}</span>
        </div>
      )}
    </div>
  );
};
