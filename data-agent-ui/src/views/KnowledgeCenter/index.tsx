import React, { useState, useEffect, useMemo } from 'react';
import { RefreshCw, Search, Info, CheckCircle2, AlertCircle } from 'lucide-react';
import clsx from 'clsx';

// 引入类型与 Mock 数据
import type { KnowledgeBase } from './types';
import { INITIAL_KNOWLEDGE_BASES } from './mockData';

// 引入子组件
import { CreateKnowledgeDialog } from './components/CreateKnowledgeDialog';
import { KnowledgeList } from './components/KnowledgeList';
import { KnowledgeDetail } from './components/KnowledgeDetail';

const LOCAL_STORAGE_KEY = 'data-agent-knowledge-bases';

export const KnowledgeCenter: React.FC = () => {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [selectedKBId, setSelectedKBId] = useState<string | null>(null);

  // 弹窗状态
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  // Toast 通知状态
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [toastType, setToastType] = useState<'success' | 'error'>('success');

  // 初始化加载数据
  useEffect(() => {
    try {
      const savedData = localStorage.getItem(LOCAL_STORAGE_KEY);
      if (savedData) {
        setKnowledgeBases(JSON.parse(savedData));
      } else {
        setKnowledgeBases(INITIAL_KNOWLEDGE_BASES);
        localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(INITIAL_KNOWLEDGE_BASES));
      }
    } catch (e) {
      console.error('读取 localStorage 失败，退避到初始数据', e);
      setKnowledgeBases(INITIAL_KNOWLEDGE_BASES);
    }
  }, []);

  // 辅助函数：保存数据至本地
  const saveToStorage = (newList: KnowledgeBase[]) => {
    try {
      localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(newList));
    } catch (e) {
      console.error('写入 localStorage 失败', e);
    }
  };

  // 触发 Toast 轻量提示
  const showToast = (message: string, type: 'success' | 'error' = 'success') => {
    setToastMessage(message);
    setToastType(type);
  };

  // Toast 自动隐藏
  useEffect(() => {
    if (toastMessage) {
      const timer = setTimeout(() => {
        setToastMessage(null);
      }, 3000);
      return () => clearTimeout(timer);
    }
  }, [toastMessage]);

  // 刷新列表数据模拟
  const handleRefresh = () => {
    if (isLoading) return;
    setIsLoading(true);
    setTimeout(() => {
      setIsLoading(false);
      try {
        const savedData = localStorage.getItem(LOCAL_STORAGE_KEY);
        if (savedData) {
          setKnowledgeBases(JSON.parse(savedData));
        }
      } catch (e) {
        // 优雅退避
      }
      showToast('知识库列表刷新成功！', 'success');
    }, 800);
  };

  // 模糊检索知识库名称
  const filteredBases = useMemo(() => {
    return knowledgeBases.filter(kb => 
      kb.name.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [knowledgeBases, searchQuery]);

  // 获取当前选中的知识库对象
  const currentKB = useMemo(() => {
    if (!selectedKBId) return null;
    return knowledgeBases.find(kb => kb.id === selectedKBId) || null;
  }, [knowledgeBases, selectedKBId]);

  // 处理删除知识库
  const handleDeleteKB = (id: string) => {
    const targetKB = knowledgeBases.find(kb => kb.id === id);
    if (!targetKB) return;

    if (window.confirm(`确认删除知识库 "${targetKB.name}" 吗？此操作不可逆！`)) {
      const updated = knowledgeBases.filter(kb => kb.id !== id);
      setKnowledgeBases(updated);
      saveToStorage(updated);
      showToast(`已删除知识库 "${targetKB.name}"`, 'success');
      if (selectedKBId === id) {
        setSelectedKBId(null);
      }
    }
  };

  // 统一的知识库更新入口（由详情页文档变动触发）
  const handleUpdateKB = (updatedKB: KnowledgeBase) => {
    const updatedList = knowledgeBases.map(kb => 
      kb.id === updatedKB.id ? updatedKB : kb
    );
    setKnowledgeBases(updatedList);
    saveToStorage(updatedList);
  };

  // 处理新建知识库
  const handleConfirmCreate = (
    name: string, 
    description: string, 
    initialFile: { name: string; size: string; status: 'success' } | null
  ) => {
    // 个人版限制：允许有内置的 Mock 知识库，但只支持额外创建 1 个自定义知识库
    const customKBCount = knowledgeBases.filter(kb => kb.id.startsWith('kb-custom')).length;
    if (customKBCount >= 1) {
      showToast('新建失败：免费版/个人版账号仅支持创建 1 个知识库，无法继续新建。', 'error');
      setIsCreateOpen(false);
      return;
    }

    const filesList = [];
    if (initialFile) {
      filesList.push({
        id: `file-custom-${Date.now()}`,
        name: initialFile.name,
        size: initialFile.size,
        status: 'success' as const,
        progress: 100,
        uploadedAt: new Date().toISOString().replace('T', ' ').substring(0, 19)
      });
    }

    const newKB: KnowledgeBase = {
      id: `kb-custom-${Date.now()}`,
      name,
      creator: 'aliyun9466154613',
      status: 'ready',
      updatedAt: new Date().toISOString().replace('T', ' ').substring(0, 19),
      fileCount: filesList.length,
      description,
      files: filesList
    };

    const updated = [newKB, ...knowledgeBases];
    setKnowledgeBases(updated);
    saveToStorage(updated);
    setIsCreateOpen(false);
    showToast(`知识库 "${name}" 创建成功！`, 'success');
  };

  return (
    <div className="relative m-2 h-[calc(100%-1rem)] w-[calc(100%-1rem)] rounded-lg border border-gray-200/80 shadow-sm bg-white overflow-hidden font-sans select-none flex flex-col">
      
      {/* 隐藏的顶部控制块（保留官网占位） */}
      <div className="flex items-center justify-between absolute left-0 top-0 h-[3.75rem] w-12 z-20">
        <div className="flex items-center">
          <button className="p-1 mx-3 size-7 items-center justify-center hidden text-gray-500 hover:bg-gray-100 rounded-md">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-4 h-4"><path d="M4 5h16"></path><path d="M4 12h16"></path><path d="M4 19h16"></path></svg>
          </button>
        </div>
      </div>

      {currentKB ? (
        /* 1. 详情管理视图态 */
        <KnowledgeDetail 
          kb={currentKB}
          onBack={() => setSelectedKBId(null)}
          onUpdateKB={handleUpdateKB}
          showToast={(msg) => showToast(msg, 'success')}
        />
      ) : (
        /* 2. 列表面板视图态 */
        <div className="flex h-full flex-col overflow-hidden">
          
          {/* 标题 */}
          <div className="text-gray-800 flex h-[3.75rem] w-full items-center gap-2 px-6 py-4 text-sm flex-none">
            <span className="flex-none text-base font-bold text-gray-800">知识中心</span>
          </div>

          {/* 警告横幅信息提示 */}
          <div className="px-6 flex-none">
            <div className="flex w-fit items-center gap-2 rounded-[10px] bg-[#EEF3FC] px-3 h-9">
              <Info className="text-[#2F54EB] h-3.5 w-3.5 flex-shrink-0" />
              <span className="text-[#0A0A0B] text-sm leading-[21px] font-normal">
                Data Agent免费版和个人版每个账号支持创建1个知识库，至多添加10个文件；DataAgent企业版默认支持至多10个知识库，每个库50个文件。
              </span>
            </div>
          </div>

          {/* 搜索和刷新过滤栏 */}
          <div className="flex h-full w-full flex-col gap-4 pb-4 flex-1 overflow-hidden">
            
            <div className="flex items-center gap-2 px-6 pt-4 flex-none">
              {/* 搜索输入框 */}
              <div className="relative">
                <input 
                  className="flex border border-gray-200 bg-white px-3 py-2 rounded pl-8 h-8 w-80 text-xs focus:outline-none focus:ring-1 focus:ring-[#2D336B] text-gray-700 placeholder-gray-400 font-semibold transition-all" 
                  placeholder="搜索知识库..." 
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
                <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-gray-400 pointer-events-none" />
              </div>
              
              {/* 刷新按钮 */}
              <button 
                onClick={handleRefresh}
                className="inline-flex items-center justify-center rounded-md border border-gray-200 bg-white hover:bg-gray-50 text-gray-400 hover:text-gray-700 size-8 shrink-0 transition-colors cursor-pointer outline-none active:scale-95"
                title="刷新"
              >
                <RefreshCw className={clsx("size-3.5", isLoading && "animate-spin text-[#2D336B]")} />
              </button>
            </div>

            {/* 骨架屏加载状态 */}
            {isLoading ? (
              <div className="grid min-h-0 flex-1 auto-rows-min grid-cols-1 items-start gap-4 overflow-y-auto px-6 md:grid-cols-2 lg:grid-cols-3">
                {[1, 2, 3].map(n => (
                  <div key={n} className="h-[10rem] rounded-xl border border-gray-100 bg-gray-50/50 p-4 animate-pulse flex flex-col justify-between">
                    <div className="space-y-3">
                      <div className="h-4 bg-gray-200/80 rounded w-[45%]" />
                      <div className="h-3 bg-gray-200/80 rounded w-[80%]" />
                      <div className="h-3 bg-gray-200/80 rounded w-[60%]" />
                    </div>
                    <div className="h-3 bg-gray-200/80 rounded w-[90%]" />
                  </div>
                ))}
              </div>
            ) : filteredBases.length === 0 ? (
              /* 空数据列表提示 */
              <div className="flex-1 flex flex-col items-center justify-center p-8 text-gray-400 font-bold text-xs py-24">
                暂无知识库数据
              </div>
            ) : (
              /* 知识库卡片网格列表 */
              <KnowledgeList 
                list={filteredBases}
                onCreateClick={() => setIsCreateOpen(true)}
                onSelect={(id) => setSelectedKBId(id)}
                onDelete={handleDeleteKB}
              />
            )}
          </div>
        </div>
      )}

      {/* 创建模态框 */}
      <CreateKnowledgeDialog 
        isOpen={isCreateOpen}
        onOpenChange={setIsCreateOpen}
        onConfirm={handleConfirmCreate}
      />

      {/* 全局 Toast 通知 */}
      {toastMessage && (
        <div className="fixed top-5 left-1/2 -translate-x-1/2 z-[9999] flex items-center gap-2 bg-gray-900/95 backdrop-blur-xs text-white px-4 py-2 rounded-xl text-xs font-bold shadow-lg animate-in fade-in slide-in-from-top-4 duration-200 select-none">
          {toastType === 'success' ? (
            <CheckCircle2 className="h-4 w-4 text-emerald-400 animate-in fade-in" />
          ) : (
            <AlertCircle className="h-4 w-4 text-rose-400 animate-in fade-in" />
          )}
          <span>{toastMessage}</span>
        </div>
      )}

    </div>
  );
};
