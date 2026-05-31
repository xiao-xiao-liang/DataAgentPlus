import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import { 
  Plus,
  AlertCircle,
  CheckCircle2,
  Ellipsis,
  Settings,
  Search,
  RefreshCw,
  MessageSquare,
  Trash2
} from 'lucide-react';
import clsx from 'clsx';
import { useCurrentAgentStore } from '../../stores/currentAgent';

interface AgentVO {
  id: number;
  name: string;
  description: string;
  avatar: string;
  status: string; // 'draft' | 'published' | 'offline'
  createTime: string;
}

const AVATAR_GRADIENTS = [
  'from-[#2D336B]/80 to-[#2D336B] shadow-[#2D336B]/10',
  'from-slate-500 to-slate-600 shadow-slate-500/10',
  'from-blue-500 to-blue-600 shadow-blue-500/10',
  'from-indigo-500 to-indigo-600 shadow-indigo-500/10',
  'from-cyan-500 to-cyan-600 shadow-cyan-500/10',
  'from-emerald-500 to-emerald-600 shadow-emerald-500/10',
  'from-zinc-500 to-zinc-600 shadow-zinc-500/10'
];

export const CustomAgent: React.FC = () => {
  const navigate = useNavigate();
  const setCurrentAgent = useCurrentAgentStore((state) => state.setCurrentAgent);
  const [agents, setAgents] = useState<AgentVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [isRefreshing, setIsRefreshing] = useState(false);

  // 自定义轻量 Toast 提示状态
  const [notification, setNotification] = useState<{ msg: string; type: 'success' | 'error' } | null>(null);
  const showNotification = (msg: string, type: 'success' | 'error' = 'success') => {
    setNotification({ msg, type });
  };

  useEffect(() => {
    if (notification) {
      const timer = setTimeout(() => setNotification(null), 3000);
      return () => clearTimeout(timer);
    }
  }, [notification]);

  // 加载智能体列表
  const fetchAgents = (isRefreshAction = false) => {
    if (isRefreshAction) {
      setIsRefreshing(true);
    } else {
      setLoading(true);
    }
    fetch('/api/agent/list')
      .then(res => res.json())
      .then(data => {
        if (data.success && data.data) {
          setAgents(data.data);
          if (isRefreshAction) {
            showNotification('智能体列表刷新成功！');
          }
        } else {
          console.error('加载智能体列表失败:', data.message);
          showNotification(data.message || '加载列表失败', 'error');
        }
        setLoading(false);
        setIsRefreshing(false);
      })
      .catch(err => {
        console.error('拉取接口发生网络异常:', err);
        showNotification('拉取接口发生网络异常', 'error');
        setLoading(false);
        setIsRefreshing(false);
      });
  };

  useEffect(() => {
    fetchAgents();
  }, []);

  // 删除智能体
  const executeDelete = async (id: number) => {
    try {
      const response = await fetch(`/api/agent/${id}`, {
        method: 'DELETE',
      });
      const result = await response.json();
      if (result.success) {
        showNotification('删除成功');
        fetchAgents(); // 重新加载
      } else {
        showNotification(result.message || '删除失败', 'error');
      }
    } catch (err) {
      console.error(err);
      showNotification('删除操作网络异常', 'error');
    }
  };

  const handleDeleteAgent = (agent: AgentVO) => {
    if (window.confirm(`确认删除智能体 "${agent.name}" 吗？此操作不可逆！`)) {
      executeDelete(agent.id);
    }
  };

  // 模糊检索智能体名称
  const filteredAgents = useMemo(() => {
    return agents.filter(agent => 
      agent.name.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [agents, searchQuery]);

  // 渲染状态标签
  const renderStatusTag = (status: string) => {
    switch (status) {
      case 'published':
        return (
          <span className="shrink-0 rounded-full px-1.5 py-0.5 text-xs bg-emerald-50 text-emerald-600 border border-emerald-200/50 font-bold font-sans">
            已发布
          </span>
        );
      case 'offline':
        return (
          <span className="shrink-0 rounded-full px-1.5 py-0.5 text-xs bg-rose-50 text-rose-600 border border-rose-200/50 font-bold font-sans">
            已下线
          </span>
        );
      case 'draft':
      default:
        return (
          <span className="shrink-0 rounded-full px-1.5 py-0.5 text-xs bg-gray-50 text-gray-600 border border-gray-200/50 font-bold font-sans">
            草稿
          </span>
        );
    }
  };

  return (
    <div className="relative h-full flex-1 overflow-hidden bg-white">
      {/* 轻量高颜值 Toast 提示框 */}
      {notification && (
        <div className="fixed top-5 left-1/2 -translate-x-1/2 z-[9999] flex items-center gap-2 bg-gray-900/95 backdrop-blur-xs text-white px-4 py-2 rounded-xl text-xs font-bold shadow-lg animate-in fade-in slide-in-from-top-4 duration-200 select-none">
          {notification.type === 'error' ? (
            <AlertCircle className="h-4 w-4 text-rose-400 animate-in fade-in" />
          ) : (
            <CheckCircle2 className="h-4 w-4 text-emerald-400 animate-in fade-in" />
          )}
          <span>{notification.msg}</span>
        </div>
      )}
      
      <div className="relative m-2 h-[calc(100%-1rem)] w-[calc(100%-1rem)] rounded-lg border border-gray-200/80 shadow-sm bg-white flex flex-col overflow-hidden font-sans select-none">
        
        {/* 顶部操作栏 */}
        <div className="flex-none flex items-center justify-between px-6 py-4 bg-[#FAFAFA] border-b border-gray-200/80 select-none">
          <span className="text-[14px] font-bold text-gray-800">自定义智能体管理</span>
          <button
            onClick={() => navigate('/agent/create')}
            className="inline-flex items-center justify-center gap-1.5 whitespace-nowrap text-xs font-semibold transition-colors bg-[#2D336B] hover:bg-[#1C214C] text-white h-8 rounded-md px-3 border-none cursor-pointer shadow-sm active:scale-95"
          >
            <Plus className="w-3.5 h-3.5" />
            新建智能体
          </button>
        </div>

        {/* 搜索和刷新过滤栏 */}
        <div className="flex h-full w-full flex-col gap-4 pb-4 flex-1 overflow-hidden">
          
          <div className="flex items-center gap-2 px-6 pt-4 flex-none">
            {/* 搜索输入框 */}
            <div className="relative">
              <input 
                className="flex border border-gray-200 bg-white px-3 py-2 rounded pl-8 h-8 w-80 text-xs focus:outline-none focus:ring-1 focus:ring-[#2D336B] text-gray-700 placeholder-gray-400 font-semibold transition-all" 
                placeholder="搜索智能体..." 
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
              <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-gray-400 pointer-events-none" />
            </div>
            
            {/* 刷新按钮 */}
            <button 
              onClick={() => fetchAgents(true)}
              className="inline-flex items-center justify-center rounded-md border border-gray-200 bg-white hover:bg-gray-50 text-gray-400 hover:text-gray-700 size-8 shrink-0 transition-colors cursor-pointer outline-none active:scale-95"
              title="刷新"
            >
              <RefreshCw className={clsx("size-3.5", isRefreshing && "animate-spin text-[#2D336B]")} />
            </button>
          </div>

          {/* 主体卡片列表或空状态 */}
          <div className="flex-1 overflow-y-auto px-6 pb-6">
            {loading ? (
              /* 骨架屏加载状态 */
              <div className="grid min-h-0 flex-1 auto-rows-min grid-cols-1 items-start gap-4 md:grid-cols-2 lg:grid-cols-3">
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
            ) : agents.length === 0 ? (
              /* 空状态 */
              <div className="flex h-full flex-col items-center justify-center gap-4 py-12">
                <img
                  src="https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/custom-agent.svg"
                  alt="自定义Agent"
                  className="pointer-events-none select-none w-[360px] h-auto"
                />
                <p className="text-gray-500 -mt-16 text-sm font-medium">
                  构建适合您的特定 data 源和分析需求的自定义Agent
                </p>
                <button
                  onClick={() => navigate('/agent/create')}
                  className="inline-flex items-center justify-center gap-2 whitespace-nowrap text-xs font-semibold transition-colors bg-[#2D336B] hover:bg-[#1C214C] text-white h-8 rounded-md px-4 border-none cursor-pointer shadow-sm active:scale-95"
                >
                  开始创建
                </button>
              </div>
            ) : filteredAgents.length === 0 ? (
              /* 搜索无结果提示 */
              <div className="flex-grow flex flex-col items-center justify-center p-8 text-gray-400 font-bold text-xs py-24">
                暂无匹配的智能体数据
              </div>
            ) : (
              /* 精美卡片网格列表 */
              <div className="grid min-h-0 flex-1 auto-rows-min grid-cols-1 items-start gap-4 md:grid-cols-2 lg:grid-cols-3 no-scrollbar select-none animate-in fade-in duration-200">
                {/* “创建智能体”卡片 */}
                <div 
                  onClick={() => navigate('/agent/create')}
                  className="group relative h-[10rem] cursor-pointer rounded-xl border border-dashed border-indigo-200 hover:border-indigo-400 bg-[#F6F6FD]/70 hover:bg-[#F6F6FD] p-[1px] transition-all hover:shadow-[0_0.25rem_0_rgba(102,127,255,0.15)] hover:-translate-y-[2px] duration-300 overflow-hidden"
                >
                  <div className="relative flex h-full flex-col justify-end gap-2 px-6 py-5">
                    {/* 右下角大图 */}
                    <img 
                      className="absolute bottom-[-1.5rem] right-0 h-[85%] w-auto object-contain opacity-55 transition-transform duration-500 group-hover:scale-105" 
                      src="https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/create.svg" 
                      alt="创建智能体" 
                    />
                    <div className="relative z-10">
                      <span className="bg-gradient-to-r from-[#2D336B] to-[#4F46E5] bg-clip-text text-base font-extrabold tracking-[1px] text-transparent">
                        创建智能体
                      </span>
                      <div className="text-gray-500 mt-1.5 text-xs font-medium">
                        构建适合特定分析需求的自定义 Data Agent
                      </div>
                    </div>
                  </div>
                </div>

                {/* 已有智能体卡片 */}
                {filteredAgents.map((agent) => {
                  const avatarIdx = Number(agent.avatar) || 0;
                  const gradient = AVATAR_GRADIENTS[avatarIdx % AVATAR_GRADIENTS.length];

                  return (
                    <div 
                      key={agent.id}
                      onClick={() => {
                        navigate(`/agent/create?id=${agent.id}`);
                      }}
                      className="group relative h-[10rem] cursor-pointer rounded-xl border border-gray-200/80 bg-white p-[1px] transition-all hover:shadow-[0_0.25rem_0_rgba(102,127,255,0.15)] hover:-translate-y-[2px] duration-300 flex flex-col justify-between"
                    >
                      <div className="relative flex h-full w-full flex-col justify-between p-4 bg-white rounded-xl">
                        
                        {/* 卡片右上角下拉管理菜单 */}
                        <div className="absolute right-3 top-3 z-20">
                          <DropdownMenu.Root>
                            <DropdownMenu.Trigger asChild>
                              <button 
                                onClick={(e) => e.stopPropagation()}
                                className="flex size-6 items-center justify-center rounded-md hover:bg-gray-100 text-gray-400 hover:text-gray-700 transition-colors border-none bg-transparent outline-none cursor-pointer"
                                type="button"
                              >
                                <Ellipsis className="size-4" />
                              </button>
                            </DropdownMenu.Trigger>

                            <DropdownMenu.Portal>
                              <DropdownMenu.Content 
                                align="end" 
                                sideOffset={5}
                                onClick={(e) => e.stopPropagation()}
                                className="z-50 min-w-[110px] rounded-lg border border-gray-150 bg-white p-1 shadow-md animate-in fade-in slide-in-from-top-1 duration-100 font-sans"
                              >
                                {/* 对话项 */}
                                <DropdownMenu.Item 
                                  onClick={() => {
                                    setCurrentAgent({ agentId: String(agent.id), agentName: agent.name });
                                    navigate('/chat');
                                  }}
                                  className="flex items-center gap-2 px-2.5 py-1.5 text-xs font-semibold text-gray-700 rounded-md hover:bg-gray-100 focus:bg-gray-100 cursor-pointer outline-none transition-colors"
                                >
                                  <MessageSquare className="size-3.5 text-gray-500" />
                                  进入对话
                                </DropdownMenu.Item>

                                {/* 编辑项 */}
                                <DropdownMenu.Item 
                                  onClick={() => navigate(`/agent/create?id=${agent.id}`)}
                                  className="flex items-center gap-2 px-2.5 py-1.5 text-xs font-semibold text-gray-700 rounded-md hover:bg-gray-100 focus:bg-gray-100 cursor-pointer outline-none transition-colors"
                                >
                                  <Settings className="size-3.5 text-gray-500" />
                                  编辑智能体
                                </DropdownMenu.Item>
                                
                                {/* 分割线 */}
                                <div className="h-px bg-gray-100 my-1" />

                                {/* 删除项 */}
                                <DropdownMenu.Item 
                                  onClick={() => handleDeleteAgent(agent)}
                                  className="flex items-center gap-2 px-2.5 py-1.5 text-xs font-semibold text-red-600 rounded-md hover:bg-red-50 focus:bg-red-50 cursor-pointer outline-none transition-colors"
                                >
                                  <Trash2 className="size-3.5 text-red-500" />
                                  删除
                                </DropdownMenu.Item>
                              </DropdownMenu.Content>
                            </DropdownMenu.Portal>
                          </DropdownMenu.Root>
                        </div>

                        {/* 卡片标题区 */}
                        <div className="pr-8">
                          <div className="flex items-center gap-2">
                            {/* 渐变色首字母头像功能恢复 */}
                            <div className={`size-8 rounded bg-gradient-to-br ${gradient} flex items-center justify-center text-white text-xs font-extrabold shadow-3xs shrink-0 select-none`}>
                              {agent.name ? agent.name.charAt(0).toUpperCase() : 'A'}
                            </div>
                            <h3 className="truncate text-sm font-bold text-gray-800 leading-tight">
                              {agent.name}
                            </h3>
                          </div>
                          <p className="text-gray-400 mt-2 text-xs line-clamp-2 leading-relaxed font-medium">
                            {agent.description || '暂无描述信息'}
                          </p>
                        </div>

                        {/* 卡片尾部元数据 */}
                        <div className="text-gray-400 flex items-center gap-2 text-sm border-t border-gray-100/70 pt-2 font-mono">
                          {renderStatusTag(agent.status)}
                          <span className="shrink-0 text-gray-200 font-sans">|</span>
                          <span className="truncate max-w-[80px]" title={`ID: ${agent.id}`}>
                            ID: {agent.id}
                          </span>
                          <span className="shrink-0 text-gray-200 font-sans">|</span>
                          <span className="truncate">
                            创建于 {agent.createTime ? agent.createTime.substring(0, 10) : '—'}
                          </span>
                        </div>

                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
