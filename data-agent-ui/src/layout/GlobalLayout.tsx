import React, { useState, useEffect, useRef } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import { buildPathWithAgentId, resolveSessionAgentId } from './agentRouting';
import { useCurrentAgentStore } from '../stores/currentAgent';
import { 
  Database, 
  Book, 
  BookMarked, 
  Atom,
  Search,
  ChevronDown,
  Plus,
  ChevronsLeft,
  ChevronsUpDown,
  Ellipsis,
  Trash2,
  Share2,
  Pencil,
  Pin,
  PinOff,
  Check,
  X
} from 'lucide-react';
import clsx from 'clsx';

type ChatSession = {
  id: string;
  agentId?: number;
  title?: string;
  isPinned?: number;
};

export interface LayoutOutletContext {
  activeSessionTitle: string;
  isSidebarCollapsed: boolean;
  isSidebarVisible: boolean;
  openSidebarPreview: () => void;
  queueSidebarPreviewClose: () => void;
  collapseSidebar: () => void;
  expandSidebar: () => void;
}

const GlobalLayout: React.FC = () => {
  const SIDEBAR_WIDTH = 207;
  const SIDEBAR_EXPANDED_OFFSET = 236;
  const navigate = useNavigate();
  const location = useLocation();
  const selectedAgentId = useCurrentAgentStore((state) => state.agentId);
  const setCurrentAgent = useCurrentAgentStore((state) => state.setCurrentAgent);

  const menuItems = [
    { key: '/data', icon: Database, label: '数据中心' },
    { key: '/knowledge', icon: Book, label: '知识中心' },
    { key: '/memory', icon: BookMarked, label: '记忆管理' },
    { key: '/agent', icon: Atom, label: '自定义Agent' },
    { key: '/search', icon: Search, label: '搜索任务' },
  ];

  // 状态维护与获取参数
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [renamingSessionId, setRenamingSessionId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [pendingDeleteSession, setPendingDeleteSession] = useState<ChatSession | null>(null);
  const [openMenuSessionId, setOpenMenuSessionId] = useState<string | null>(null);
  const [actionError, setActionError] = useState('');
  const [toastMessage, setToastMessage] = useState('');
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(() => {
    try {
      return localStorage.getItem('data-agent-sidebar-collapsed') === '1';
    } catch {
      return false;
    }
  });
  const [isSidebarPreviewOpen, setIsSidebarPreviewOpen] = useState(false);
  const previewCloseTimerRef = useRef<number | null>(null);

  // 正则提取当前路由下的 sessionId
  const match = location.pathname.match(/^\/chat\/([^?/\s]+)/);
  const pathnameSessionId = match ? match[1] : null;
  const activeSession = sessions.find((session) => session.id === pathnameSessionId);
  const currentAgentId = resolveSessionAgentId(activeSession?.agentId, selectedAgentId);
  const isSidebarVisible = !isSidebarCollapsed || isSidebarPreviewOpen;
  const activeSessionTitle = activeSession?.title || '新对话';

  useEffect(() => {
    try {
      localStorage.setItem('data-agent-sidebar-collapsed', isSidebarCollapsed ? '1' : '0');
    } catch (error) {
      console.error('保存边栏状态失败', error);
    }
  }, [isSidebarCollapsed]);

  useEffect(() => {
    if (!toastMessage) return;
    const timer = window.setTimeout(() => setToastMessage(''), 1800);
    return () => window.clearTimeout(timer);
  }, [toastMessage]);

  useEffect(() => {
    if (!actionError) return;
    const timer = window.setTimeout(() => setActionError(''), 2600);
    return () => window.clearTimeout(timer);
  }, [actionError]);

  useEffect(() => {
    return () => {
      if (previewCloseTimerRef.current) {
        window.clearTimeout(previewCloseTimerRef.current);
      }
    };
  }, []);

  const clearSidebarPreviewTimer = () => {
    if (previewCloseTimerRef.current) {
      window.clearTimeout(previewCloseTimerRef.current);
      previewCloseTimerRef.current = null;
    }
  };

  const openSidebarPreview = () => {
    clearSidebarPreviewTimer();
    if (isSidebarCollapsed) {
      setIsSidebarPreviewOpen(true);
    }
  };

  const queueSidebarPreviewClose = () => {
    if (!isSidebarCollapsed) return;
    clearSidebarPreviewTimer();
    previewCloseTimerRef.current = window.setTimeout(() => {
      setIsSidebarPreviewOpen(false);
    }, 120);
  };

  const collapseSidebar = () => {
    clearSidebarPreviewTimer();
    setIsSidebarPreviewOpen(false);
    setIsSidebarCollapsed(true);
  };

  const expandSidebar = () => {
    clearSidebarPreviewTimer();
    setIsSidebarCollapsed(false);
    setIsSidebarPreviewOpen(false);
  };

  const fetchSessions = async () => {
    let targetAgentId = selectedAgentId;
    if (!targetAgentId || targetAgentId === 'default') {
      try {
        const listRes = await fetch('/api/agent/list').then(res => res.json());
        if (listRes.success && Array.isArray(listRes.data) && listRes.data.length > 0) {
          const firstAgent = listRes.data[0];
          targetAgentId = firstAgent.id.toString();
          setCurrentAgent({
            agentId: targetAgentId,
            agentName: firstAgent.name || 'Data Agent',
          });
        } else {
          targetAgentId = '1';
          setCurrentAgent({ agentId: targetAgentId, agentName: 'Data Agent' });
        }
      } catch {
        targetAgentId = '1';
        setCurrentAgent({ agentId: targetAgentId, agentName: 'Data Agent' });
      }
    }

    fetch(`/api/agent/${targetAgentId}/sessions`)
      .then(res => res.json())
      .then(data => {
        if (data.success && Array.isArray(data.data)) {
          setSessions(data.data);
        } else {
          setSessions([]);
        }
      })
      .catch(err => {
        console.error("加载会话列表失败", err);
        setSessions([]);
      });
  };

  // 依赖当前智能体和路由路径刷新会话列表
  useEffect(() => {
    fetchSessions();
  }, [selectedAgentId, location.pathname]);

  const showToast = (message: string) => {
    setToastMessage(message);
  };

  const copyToClipboard = async (text: string) => {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return;
    }

    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
  };

  const handleShareSession = async (session: ChatSession) => {
    const sessionAgentId = resolveSessionAgentId(session.agentId, currentAgentId);
    const path = buildPathWithAgentId(`/chat/${session.id}`, sessionAgentId);
    const url = `${window.location.origin}${path}`;
    try {
      await copyToClipboard(url);
      showToast('会话链接已复制');
    } catch (err) {
      console.error("复制会话链接失败", err);
      setActionError('复制链接失败，请稍后重试');
    }
  };

  const startRenameSession = (session: ChatSession) => {
    setActionError('');
    setOpenMenuSessionId(null);
    setRenamingSessionId(session.id);
    setRenameValue(session.title || '新会话');
  };

  const cancelRenameSession = () => {
    setRenamingSessionId(null);
    setRenameValue('');
  };

  const submitRenameSession = async (sessionId: string) => {
    const title = renameValue.trim();
    if (!title) {
      setActionError('会话标题不能为空');
      return;
    }

    try {
      const response = await fetch(`/api/sessions/${sessionId}/rename?title=${encodeURIComponent(title)}`, {
        method: 'PUT'
      });
      const result = await response.json();
      if (!result.success) {
        setActionError(result.message || '重命名失败，请稍后重试');
        return;
      }
      setSessions(prev => prev.map(session => session.id === sessionId ? { ...session, title } : session));
      cancelRenameSession();
      showToast('会话已重命名');
    } catch (err) {
      console.error("重命名会话失败", err);
      setActionError('重命名失败，请稍后重试');
    }
  };

  const handleTogglePinSession = async (session: ChatSession) => {
    const nextPinned = session.isPinned === 1 ? 0 : 1;
    try {
      const response = await fetch(`/api/sessions/${session.id}/pin?isPinned=${nextPinned === 1}`, {
        method: 'PUT'
      });
      const result = await response.json();
      if (!result.success) {
        setActionError(result.message || '置顶状态更新失败');
        return;
      }
      setSessions(prev => prev.map(item => item.id === session.id ? { ...item, isPinned: nextPinned } : item));
      showToast(nextPinned === 1 ? '会话已置顶' : '已取消置顶');
      fetchSessions();
    } catch (err) {
      console.error("更新会话置顶状态失败", err);
      setActionError('置顶状态更新失败，请稍后重试');
    }
  };

  const confirmDeleteSession = async () => {
    if (!pendingDeleteSession) return;
    const sessId = pendingDeleteSession.id;
    try {
      const response = await fetch(`/api/sessions/${sessId}`, {
        method: 'DELETE'
      });
      const result = await response.json();
      if (!result.success) {
        setActionError(result.message || '删除会话失败');
        return;
      }
      setOpenMenuSessionId(null);
      setPendingDeleteSession(null);
      if (pathnameSessionId === sessId) {
        navigate('/chat');
      } else {
        setSessions(prev => prev.filter(session => session.id !== sessId));
      }
      showToast('会话已删除');
    } catch (err) {
      console.error("删除会话失败", err);
      setActionError('删除会话失败，请稍后重试');
    }
  };

  return (
    <div
      className="flex h-screen w-full overflow-hidden bg-[#F6F6F6]"
      style={{ ['--sidebar-offset' as string]: isSidebarCollapsed ? '0px' : `${SIDEBAR_EXPANDED_OFFSET}px` }}
    >
      
      {/* 阿里云原生 1:1 悬浮卡片侧边栏 */}
      <div
        className="relative z-50 h-full flex-none animate-nav-bar-container"
        onMouseEnter={openSidebarPreview}
        onMouseLeave={queueSidebarPreviewClose}
      >
        
        {/* 宽度占位符，预留 215px 空间防遮挡 (8 left-margin + 207 sidebar) */}
        <div
          className="transition-[width] duration-300"
          style={{ width: isSidebarCollapsed ? '0px' : `${SIDEBAR_EXPANDED_OFFSET}px` }}
        ></div>

        
        {/* 绝对定位的浮动卡片边栏 (h-[calc(100%-1rem)], 宽度 228px, rounded-[10px], 边距 m-2 mr-0) */}
        <aside 
          data-testid="nav-sidebar" 
          onMouseEnter={openSidebarPreview}
          onMouseLeave={queueSidebarPreviewClose}
          className={clsx(
            'group/nav-bar absolute left-0 z-40 mr-0 flex flex-col rounded-[10px] border border-gray-200/80 bg-[#FAFAFA] px-2 text-[#0A0A0B] shadow-[0_16px_40px_rgba(15,23,42,0.08)] transition-all duration-300 ease-out',
            isSidebarCollapsed ? 'top-[54px] m-2 h-[calc(100%-70px)]' : 'top-2 m-2 h-[calc(100%-1rem)]',
            isSidebarVisible ? 'translate-x-0 opacity-100 pointer-events-auto' : '-translate-x-[236px] opacity-0 pointer-events-none'
          )}
          style={{ width: `${SIDEBAR_WIDTH}px` }}
        >
          {/* 顶部 Logo 与空间切换区 */}
          <div className="group/nav-bar-top mb-3 flex flex-none flex-col border-b border-gray-200/60 pb-3">
            
            <div className="my-4 flex items-center justify-between gap-2 px-1">
              <div className="flex items-center gap-2">
                <a 
                  href="/" 
                  onClick={(e) => { e.preventDefault(); navigate('/chat'); }}
                  className="relative rounded-full flex h-[26px] w-[26px] items-center overflow-visible flex-none"
                >
                  <img 
                    className="h-full w-full aspect-auto" 
                    alt="data-agent" 
                    src="https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/logo-black.svg" 
                  />
                </a>
                
                <div data-testid="workspace-switch" className="flex cursor-pointer items-center gap-1">
                  <span className="mr-1 text-sm text-zinc-300">/</span>
                  <span className="max-w-[100px] truncate text-sm font-medium text-[#0A0A0B]">默认个人空间</span>
                  <ChevronDown className="h-4 w-4 text-zinc-400 flex-none" />
                </div>
              </div>

              <button
                type="button"
                title="关闭边栏"
                onClick={collapseSidebar}
                className="invisible group-hover/nav-bar:visible flex size-7 items-center justify-center rounded-md text-gray-400 transition-colors hover:bg-gray-200/50 hover:text-gray-600"
              >
                <ChevronsLeft className="w-4 h-4" />
              </button>
            </div>

            {/* 新任务按钮 (h-9 rounded-lg border bg-white, 包含 Ctrl+P 快捷键) */}
            <div className="relative my-4 flex items-center">
              <button 
                onClick={() => navigate('/chat')}
                className="group/create-btn z-10 h-9 w-full rounded-lg border border-gray-200 bg-white p-0 text-sm shadow-none hover:bg-gray-50 flex items-center transition-colors"
              >
                <div className="flex h-full w-full items-center justify-between rounded-lg pl-[13px] pr-3 text-[#0A0A0B]">
                  <div className="flex items-center gap-2">
                    <Plus className="w-4 h-4 text-gray-400 group-hover:text-gray-600 flex-none" />
                    <span className="flex-1 truncate text-left font-medium text-[#0A0A0B]">新任务</span>
                  </div>
                  <div className="flex items-center gap-0.5">
                    <span className="bg-gray-100 text-gray-400 group-hover:bg-gray-50 inline-flex size-5 flex-none items-center justify-center rounded text-[10px] font-mono">⌃</span>
                    <span className="bg-gray-100 text-gray-400 group-hover:bg-gray-50 inline-flex size-5 flex-none items-center justify-center rounded text-[10px] font-mono">P</span>
                  </div>
                </div>
              </button>
            </div>

            {/* 导航菜单项 */}
            <nav className="flex flex-col gap-0.5">
              {menuItems.map((item) => {
                const Icon = item.icon;
                const isActive = location.pathname.startsWith(item.key);
                return (
                  <button
                    key={item.key}
                    onClick={() => navigate(item.key)}
                    className={clsx(
                      "flex h-8 w-full cursor-pointer items-center justify-start gap-2 px-3 py-1.5 text-sm font-normal rounded-lg transition-colors group/menuItem",
                      isActive 
                        ? "text-[#0A0A0B] bg-gray-200/50 font-semibold" 
                        : "text-[#0A0A0B]/70 hover:bg-gray-200/50 font-medium"
                    )}
                  >
                    <Icon className={clsx("w-4 h-4 flex-none", isActive ? "text-[#0A0A0B]" : "text-[#0A0A0B]/50")} />
                    <span className="flex-1 text-left">{item.label}</span>
                    {item.key === '/search' && (
                      <div className="flex items-center gap-0.5 opacity-0 group-hover/menuItem:opacity-100 transition-opacity">
                        <span className="bg-gray-100 text-gray-400 inline-flex size-5 flex-none items-center justify-center rounded text-[10px] font-mono">⌃</span>
                        <span className="bg-gray-100 text-gray-400 inline-flex size-5 flex-none items-center justify-center rounded text-[10px] font-mono">K</span>
                      </div>
                    )}
                  </button>
                );
              })}
            </nav>
          </div>

          {/* 所有任务下拉选择 */}
          <button className="whitespace-nowrap font-medium flex h-9 w-auto max-w-full items-center justify-start gap-1 rounded-lg px-3 text-sm hover:bg-gray-200/50 text-[#0A0A0B]/70 transition-colors mb-2" type="button">
            <span className="truncate text-sm">所有任务</span>
            <ChevronDown className="w-4 h-4 text-zinc-400 flex-none" />
          </button>

          {/* 滚动会话列表 */}
          <div className="min-h-0 flex-1 overflow-y-auto no-scrollbar">
            <div className="group/session-list-card text-sm mb-4">
              <div className="group/List relative text-[#0A0A0B] mt-2">
                {sessions.length === 0 ? (
                  <div className="px-3.5 py-4 text-xs text-gray-400 font-medium text-center">
                    暂无历史会话
                  </div>
                ) : (
                  sessions.map((session) => {
                    const isActive = pathnameSessionId === session.id;
                    const title = session.title || "新对话";
                    const isRenaming = renamingSessionId === session.id;
                    const isPinned = session.isPinned === 1;
                    const isMenuOpen = openMenuSessionId === session.id;
                    return (
                      <div 
                        key={session.id}
                        onClick={() => {
                          if (!isRenaming) {
                            setCurrentAgent({ agentId: resolveSessionAgentId(session.agentId, currentAgentId) });
                            navigate(`/chat/${session.id}`);
                          }
                        }}
                        title={!isActive ? title : undefined}
                        className={clsx(
                          "group/session-item min-h-8 px-3.5 py-1.5 text-sm flex items-center justify-between cursor-pointer rounded-lg mb-2 last:mb-0 transition-colors",
                          isActive ? "bg-gray-200/60 font-semibold" : "hover:bg-gray-200/50"
                        )}
                      >
                        {isRenaming ? (
                          <div className="flex min-w-0 flex-1 items-center gap-1.5" onClick={(e) => e.stopPropagation()}>
                            <input
                              autoFocus
                              value={renameValue}
                              maxLength={60}
                              onChange={(e) => setRenameValue(e.target.value)}
                              onKeyDown={(e) => {
                                if (e.key === 'Enter') {
                                  submitRenameSession(session.id);
                                } else if (e.key === 'Escape') {
                                  cancelRenameSession();
                                }
                              }}
                              className="h-6 min-w-0 flex-1 rounded-md border border-gray-250 bg-white px-2 text-[12px] font-semibold text-[#0A0A0B] outline-none focus:border-gray-400 focus:ring-2 focus:ring-gray-200"
                            />
                            <button
                              type="button"
                              aria-label="确认重命名"
                              onClick={() => submitRenameSession(session.id)}
                              className="grid size-6 place-items-center rounded-md border-none bg-transparent text-gray-500 hover:bg-gray-200 hover:text-gray-800"
                            >
                              <Check className="size-3.5" />
                            </button>
                            <button
                              type="button"
                              aria-label="取消重命名"
                              onClick={cancelRenameSession}
                              className="grid size-6 place-items-center rounded-md border-none bg-transparent text-gray-400 hover:bg-gray-200 hover:text-gray-700"
                            >
                              <X className="size-3.5" />
                            </button>
                          </div>
                        ) : (
                          <div className="flex min-w-0 flex-1 items-center gap-1.5 overflow-hidden">
                            {isPinned && <Pin className="size-3 shrink-0 text-gray-400" />}
                            <span className="truncate leading-5 text-[13px] text-[#0A0A0B]">{title}</span>
                          </div>
                        )}
                        
                        {!isRenaming && (
                        <div className={clsx(
                          "h-5 items-center ml-1.5",
                          isActive || isMenuOpen ? "flex" : "hidden group-hover/session-item:flex"
                        )}>
                          <DropdownMenu.Root
                            modal={false}
                            open={isMenuOpen}
                            onOpenChange={(open) => {
                              setOpenMenuSessionId(open ? session.id : null);
                            }}
                          >
                            <DropdownMenu.Trigger asChild>
                              <button 
                                onClick={(e) => e.stopPropagation()}
                                onPointerDown={(e) => e.stopPropagation()}
                                onMouseDown={(e) => e.stopPropagation()}
                                onKeyDown={(e) => e.stopPropagation()}
                                className="inline-flex items-center justify-center rounded-md hover:bg-gray-200 text-gray-500 size-6 p-1 shrink-0 border-none bg-transparent outline-none cursor-pointer animate-in fade-in" 
                                type="button"
                              >
                                <Ellipsis className="w-4 h-4" />
                              </button>
                            </DropdownMenu.Trigger>
                            <DropdownMenu.Portal>
                              <DropdownMenu.Content 
                                align="end" 
                                sideOffset={5}
                                side="right"
                                onClick={(e) => e.stopPropagation()}
                                className="z-50 min-w-[132px] rounded-xl border border-gray-200/80 bg-white p-1 shadow-[0_12px_32px_rgba(15,23,42,0.12)] outline-none ring-0 animate-in fade-in slide-in-from-top-1 duration-100 font-sans"
                              >
                                <DropdownMenu.Item
                                  onClick={() => handleShareSession(session)}
                                  className="flex items-center gap-2 px-2.5 py-1.5 text-xs font-semibold text-gray-700 rounded-md hover:bg-gray-100 focus:bg-gray-100 cursor-pointer outline-none transition-colors"
                                >
                                  <Share2 className="size-3.5 text-gray-500" />
                                  分享链接
                                </DropdownMenu.Item>
                                <DropdownMenu.Item
                                  onClick={() => startRenameSession(session)}
                                  className="flex items-center gap-2 px-2.5 py-1.5 text-xs font-semibold text-gray-700 rounded-md hover:bg-gray-100 focus:bg-gray-100 cursor-pointer outline-none transition-colors"
                                >
                                  <Pencil className="size-3.5 text-gray-500" />
                                  重命名
                                </DropdownMenu.Item>
                                <DropdownMenu.Item
                                  onClick={() => handleTogglePinSession(session)}
                                  className="flex items-center gap-2 px-2.5 py-1.5 text-xs font-semibold text-gray-700 rounded-md hover:bg-gray-100 focus:bg-gray-100 cursor-pointer outline-none transition-colors"
                                >
                                  {isPinned ? <PinOff className="size-3.5 text-gray-500" /> : <Pin className="size-3.5 text-gray-500" />}
                                  {isPinned ? '取消置顶' : '置顶会话'}
                                </DropdownMenu.Item>
                                <DropdownMenu.Separator className="my-1 h-px bg-gray-100" />
                                <DropdownMenu.Item 
                                  onClick={() => {
                                    setActionError('');
                                    setPendingDeleteSession(session);
                                  }}
                                  className="flex items-center gap-2 px-2.5 py-1.5 text-xs font-semibold text-red-650 rounded-md hover:bg-red-50 focus:bg-red-50 cursor-pointer outline-none transition-colors"
                                >
                                  <Trash2 className="size-3.5 text-red-500" />
                                  删除会话
                                </DropdownMenu.Item>
                              </DropdownMenu.Content>
                            </DropdownMenu.Portal>
                          </DropdownMenu.Root>
                        </div>
                        )}
                      </div>
                    );
                  })
                )}
              </div>
            </div>
          </div>

          {/* 底部用户信息 (aliyun9466154613) */}
          <div className="group/nav-bar-bottom mb-1 flex flex-none flex-col border-t border-gray-200/60 pt-2">
            <button type="button" className="focus-visible:outline-none w-full">
              <div className="text-sm flex h-12 items-center rounded-lg px-2 py-1 hover:bg-gray-200/50 transition-colors">
                <span className="overflow-hidden rounded-full relative flex size-7 shrink-0 items-center justify-center bg-blue-100 text-blue-600 font-bold">
                  A
                </span>
                <span className="flex-1 overflow-hidden truncate text-sm font-medium text-[#0A0A0B] text-left pl-2">
                  aliyun9466154613
                </span>
                <ChevronsUpDown className="text-gray-400 w-4 h-4 flex-none" />
              </div>
            </button>
          </div>
          
        </aside>
      </div>

      {/* 右侧主内容区 */}
      <main className="flex-1 h-full relative overflow-hidden">
        <Outlet
          context={{
            activeSessionTitle,
            isSidebarCollapsed,
            isSidebarVisible,
            openSidebarPreview,
            queueSidebarPreviewClose,
            collapseSidebar,
            expandSidebar,
          } satisfies LayoutOutletContext}
        />
      </main>

      {toastMessage && (
        <div className="fixed left-1/2 top-4 z-[80] -translate-x-1/2 rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs font-semibold text-gray-700 shadow-lg">
          {toastMessage}
        </div>
      )}

      {actionError && (
        <div className="fixed left-1/2 top-4 z-[80] -translate-x-1/2 rounded-lg border border-red-100 bg-red-50 px-3 py-2 text-xs font-semibold text-red-700 shadow-lg">
          {actionError}
        </div>
      )}

      {pendingDeleteSession && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/10 px-4" onClick={() => setPendingDeleteSession(null)}>
          <div
            className="w-[320px] rounded-xl border border-gray-200 bg-white p-4 shadow-xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="text-sm font-bold text-gray-900">删除会话？</div>
            <div className="mt-2 text-xs leading-relaxed text-gray-500">
              将删除“{pendingDeleteSession.title || '新对话'}”及其中的历史消息，此操作不可撤销。
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setPendingDeleteSession(null)}
                className="h-8 rounded-md border border-gray-200 bg-white px-3 text-xs font-semibold text-gray-600 hover:bg-gray-50"
              >
                取消
              </button>
              <button
                type="button"
                onClick={confirmDeleteSession}
                className="h-8 rounded-md border border-red-600 bg-red-600 px-3 text-xs font-semibold text-white hover:bg-red-700"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default GlobalLayout;
