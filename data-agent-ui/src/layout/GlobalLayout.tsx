import React from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
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
  Ellipsis
} from 'lucide-react';
import clsx from 'clsx';

const GlobalLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems = [
    { key: '/data', icon: Database, label: '数据中心' },
    { key: '/knowledge', icon: Book, label: '知识中心' },
    { key: '/memory', icon: BookMarked, label: '记忆管理' },
    { key: '/agent', icon: Atom, label: '自定义Agent' },
    { key: '/search', icon: Search, label: '搜索任务' },
  ];

  return (
    <div className="flex h-screen w-full overflow-hidden bg-[#F6F6F6]">
      
      {/* 阿里云原生 1:1 悬浮卡片侧边栏 */}
      <div className="relative z-50 h-full flex-none animate-nav-bar-container">
        
        {/* 宽度占位符，预留 236px 空间防遮挡 */}
        <div className="transition-[width] duration-300 w-[236px]"></div>
        
        {/* 绝对定位的浮动卡片边栏 (h-[calc(100%-1rem)], 宽度 228px, rounded-lg, 边距 m-2 mr-0) */}
        <aside 
          data-testid="nav-sidebar" 
          className="group/nav-bar flex flex-col bg-[#FAFAFA] h-[calc(100%-1rem)] w-[228px] rounded-lg border border-gray-200/80 z-40 absolute left-0 transition-all duration-300 m-2 mr-0 px-2"
        >
          {/* 顶部 Logo 与空间切换区 */}
          <div className="group/nav-bar-top mb-3 flex flex-none flex-col border-b border-gray-200/60 pb-3">
            
            <div className="my-4 flex items-center justify-between gap-2 px-1">
              <div className="flex items-center gap-2">
                <a 
                  href="/" 
                  onClick={(e) => { e.preventDefault(); navigate('/'); }}
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
                  <span className="max-w-[100px] truncate text-sm font-medium text-gray-700">默认个人空间</span>
                  <ChevronDown className="h-4 w-4 text-zinc-400 flex-none" />
                </div>
              </div>

              <button className="invisible group-hover/nav-bar:visible flex size-7 items-center justify-center rounded-md hover:bg-gray-200/50 text-gray-400 hover:text-gray-600 transition-colors">
                <ChevronsLeft className="w-4 h-4" />
              </button>
            </div>

            {/* 新任务按钮 (h-9 rounded-lg border bg-white, 包含 Ctrl+P 快捷键) */}
            <div className="relative my-4 flex items-center">
              <button 
                onClick={() => navigate('/')}
                className="group/create-btn z-10 h-9 w-full rounded-lg border border-gray-200 bg-white p-0 text-sm shadow-none hover:bg-gray-50 flex items-center transition-colors"
              >
                <div className="flex h-full w-full items-center justify-between rounded-lg pl-[13px] pr-3 text-gray-700">
                  <div className="flex items-center gap-2">
                    <Plus className="w-4 h-4 text-gray-400 group-hover:text-gray-600 flex-none" />
                    <span className="flex-1 truncate text-left font-medium text-gray-700">新任务</span>
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
                        ? "text-gray-900 bg-gray-200/50 font-semibold" 
                        : "text-gray-600 hover:bg-gray-200/50 font-medium"
                    )}
                  >
                    <Icon className={clsx("w-4 h-4 flex-none", isActive ? "text-gray-950" : "text-gray-500")} />
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
          <button className="whitespace-nowrap font-medium flex h-9 w-auto max-w-full items-center justify-start gap-1 rounded-lg px-3 text-sm hover:bg-gray-200/50 text-gray-600 transition-colors mb-2" type="button">
            <span className="truncate text-sm">所有任务</span>
            <ChevronDown className="w-4 h-4 text-zinc-400 flex-none" />
          </button>

          {/* 滚动会话列表 */}
          <div className="min-h-0 flex-1 overflow-y-auto no-scrollbar">
            <div className="group/session-list-card text-sm mb-4">
              <div className="group/List relative text-gray-700 mt-2">
                <div 
                  onClick={() => navigate('/')}
                  className="group/session-item h-8 px-3.5 py-1.5 text-sm flex items-center justify-between cursor-pointer rounded-lg hover:bg-gray-200/50 mb-2 last:mb-0"
                >
                  <div className="flex-1 overflow-hidden">
                    <div className="flex items-center text-gray-700">
                      <span className="truncate leading-5 text-[13px]">请帮我结合这份游戏数据，洞察并产出策略类游戏的营销策略</span>
                    </div>
                  </div>
                  <div className="hidden h-5 items-center group-hover/session-item:flex ml-1.5">
                    <button className="inline-flex items-center justify-center rounded-md hover:bg-gray-200 text-gray-500 size-6 p-1 shrink-0" type="button">
                      <Ellipsis className="w-4 h-4" />
                    </button>
                  </div>
                </div>
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
                <span className="flex-1 overflow-hidden truncate text-sm font-medium text-gray-700 text-left pl-2">
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
        <Outlet />
      </main>
    </div>
  );
};

export default GlobalLayout;
