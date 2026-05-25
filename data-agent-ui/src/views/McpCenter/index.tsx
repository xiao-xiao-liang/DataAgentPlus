import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Unplug, ChevronDown, Search, RadioTower, Pen, Trash2, CheckCircle2, UserRound, Copy, Clock, Globe, Cable, X, RotateCw, Plus, HelpCircle } from 'lucide-react';
import clsx from 'clsx';

interface HeaderItem {
  key: string;
  value: string;
}

interface McpService {
  id: string;
  name: string;
  url: string;
  enabled: boolean;
  status: 'ready' | 'error';
  type: string;
  createdAt: string;
  scope: string;
  protocol: string;
  description: string;
  autoAliyunAuth: boolean;
  headers: HeaderItem[];
}

const STORAGE_KEY = 'data-agent-mcp-services';

const DEFAULT_MCP_LIST: McpService[] = [
  {
    id: '3my99tebo2hdfxutftou00ziv',
    name: '测试',
    url: 'https://aaa.bbb.com',
    enabled: true,
    status: 'ready',
    type: '用户',
    createdAt: '2026-05-22',
    scope: '公网',
    protocol: 'streamablehttp',
    description: '',
    autoAliyunAuth: false,
    headers: []
  }
];

export const McpCenter: React.FC = () => {
  const navigate = useNavigate();
  const [mcpList, setMcpList] = useState<McpService[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [isRefreshing, setIsRefreshing] = useState(false);
  
  // 弹窗控制
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingMcp, setEditingMcp] = useState<McpService | null>(null);
  
  // 表单状态
  const [formName, setFormName] = useState('');
  const [formUrl, setFormUrl] = useState('');
  const [formDescription, setFormDescription] = useState('');
  const [formAutoAliyunAuth, setFormAutoAliyunAuth] = useState(false);
  const [formHeaders, setFormHeaders] = useState<HeaderItem[]>([]);
  const [acceptNotice, setAcceptNotice] = useState(false);
  const [formScope, setFormScope] = useState<'公网' | '内网'>('公网');
  const [formProtocol, setFormProtocol] = useState<'SSE' | 'StreamableHttp'>('SSE');
  const [isProtocolDropdownOpen, setIsProtocolDropdownOpen] = useState(false);
  
  // 复制提示状态
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // 连通测试状态
  const [testingId, setTestingId] = useState<string | null>(null);
  const [testedId, setTestedId] = useState<string | null>(null);

  // 初始化加载
  useEffect(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        setMcpList(JSON.parse(stored));
      } else {
        setMcpList(DEFAULT_MCP_LIST);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(DEFAULT_MCP_LIST));
      }
    } catch (e) {
      setMcpList(DEFAULT_MCP_LIST);
    }
  }, []);

  // 保存数据
  const saveToStorage = (newList: McpService[]) => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(newList));
    } catch (e) {
      console.error('Failed to save MCP services to localStorage', e);
    }
  };

  // 一键刷新
  const handleRefresh = () => {
    if (isRefreshing) return;
    setIsRefreshing(true);
    setTimeout(() => {
      setIsRefreshing(false);
      try {
        const stored = localStorage.getItem(STORAGE_KEY);
        if (stored) {
          setMcpList(JSON.parse(stored));
        }
      } catch (e) {
        // 降级
      }
    }, 800);
  };

  // 启用/停用开关
  const handleToggle = (id: string) => {
    const updated = mcpList.map(mcp => 
      mcp.id === id ? { ...mcp, enabled: !mcp.enabled } : mcp
    );
    setMcpList(updated);
    saveToStorage(updated);
  };

  // 删除服务
  const handleDelete = (id: string) => {
    if (window.confirm('确认删除该 MCP 服务配置吗？')) {
      const updated = mcpList.filter(mcp => mcp.id !== id);
      setMcpList(updated);
      saveToStorage(updated);
    }
  };

  // 复制唯一 ID
  const handleCopyId = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    navigator.clipboard.writeText(id).then(() => {
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 1500);
    });
  };

  // 连通性测试模拟
  const handleTestConnection = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (testingId) return;
    setTestingId(id);
    setTestedId(null);
    setTimeout(() => {
      setTestingId(null);
      setTestedId(id);
      setTimeout(() => setTestedId(null), 2000);
    }, 1000);
  };

  // 开启添加弹窗
  const openAddModal = () => {
    setEditingMcp(null);
    setFormName('');
    setFormUrl('');
    setFormDescription('');
    setFormAutoAliyunAuth(false);
    setFormHeaders([]);
    setAcceptNotice(false);
    setFormScope('公网');
    setFormProtocol('SSE');
    setIsProtocolDropdownOpen(false);
    setIsModalOpen(true);
  };

  // 开启编辑弹窗
  const openEditModal = (mcp: McpService, e: React.MouseEvent) => {
    e.stopPropagation();
    setEditingMcp(mcp);
    setFormName(mcp.name);
    setFormUrl(mcp.url);
    setFormDescription(mcp.description || '');
    setFormAutoAliyunAuth(mcp.autoAliyunAuth || false);
    setFormHeaders(mcp.headers ? [...mcp.headers] : []);
    setAcceptNotice(false); // 每次打开编辑都重置为未勾选，确保安全合规
    setFormScope(mcp.scope === '内网' ? '内网' : '公网');
    setFormProtocol(mcp.protocol?.toLowerCase() === 'streamablehttp' ? 'StreamableHttp' : 'SSE');
    setIsProtocolDropdownOpen(false);
    setIsModalOpen(true);
  };

  // 添加自定义请求头行
  const handleAddHeader = () => {
    setFormHeaders([...formHeaders, { key: '', value: '' }]);
  };

  // 修改请求头
  const handleHeaderChange = (index: number, field: 'key' | 'value', value: string) => {
    const updated = [...formHeaders];
    updated[index][field] = value;
    setFormHeaders(updated);
  };

  // 删除请求头
  const handleRemoveHeader = (index: number) => {
    setFormHeaders(formHeaders.filter((_, idx) => idx !== index));
  };

  // 提交配置表单
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formName.trim() || !formUrl.trim() || !acceptNotice) return;

    // 过滤掉无效的空请求头
    const validHeaders = formHeaders.filter(h => h.key.trim() !== '');

    if (editingMcp) {
      // 修改已存在的 MCP
      const updated = mcpList.map(mcp => 
        mcp.id === editingMcp.id 
          ? { 
              ...mcp, 
              name: formName.trim(), 
              url: formUrl.trim(),
              description: formDescription.trim(),
              autoAliyunAuth: formAutoAliyunAuth,
              headers: validHeaders,
              scope: formScope,
              protocol: formProtocol.toLowerCase()
            } 
          : mcp
      );
      setMcpList(updated);
      saveToStorage(updated);
    } else {
      // 新增 MCP
      const newMcp: McpService = {
        id: Math.random().toString(36).substring(2, 11) + Date.now().toString(36).substring(4),
        name: formName.trim(),
        url: formUrl.trim(),
        enabled: true,
        status: 'ready',
        type: '用户',
        createdAt: new Date().toISOString().split('T')[0],
        scope: formScope,
        protocol: formProtocol.toLowerCase(),
        description: formDescription.trim(),
        autoAliyunAuth: formAutoAliyunAuth,
        headers: validHeaders
      };
      const updated = [...mcpList, newMcp];
      setMcpList(updated);
      saveToStorage(updated);
    }
    setIsModalOpen(false);
  };

  // 列表本地搜索过滤
  const filteredList = mcpList.filter(mcp => 
    mcp.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    mcp.url.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="relative h-full flex-1 overflow-hidden bg-[#F6F6F6]">
      {/* 像素级复现的白色卡片式容器 */}
      <div className="relative w-full m-2 h-[calc(100%-1rem)] rounded-2xl border border-gray-200 shadow-sm bg-white">
        
        <div className="flex h-full">
          <div className="flex h-full w-full flex-col text-xl">
            
            {/* 顶栏面包屑与返回 */}
            <div className="flex h-[3.75rem] items-center border-b border-gray-100 px-6 py-4">
              <span 
                onClick={() => navigate('/chat')}
                className="mr-2 cursor-pointer p-1 rounded-lg hover:bg-gray-100 text-gray-700 transition-colors z-10"
              >
                <ArrowLeft className="h-5 w-5" />
              </span>
              <h1 
                onClick={() => navigate('/chat')}
                className="cursor-pointer text-lg font-semibold text-gray-800"
              >
                MCP
              </h1>
            </div>
            
            <div className="flex flex-1 overflow-hidden">
              
              {/* 左侧次导航栏，宽度为 14.75rem */}
              <div className="box-border w-[14.75rem] border-r border-gray-100 bg-[#FAFAFA]">
                <nav className="p-6">
                  <ul>
                    <li>
                      <button className="gap-2 whitespace-nowrap rounded-lg transition-colors flex h-8 w-full cursor-pointer items-center justify-start px-3 py-1.5 text-sm font-medium mt-2 bg-gray-200/60 text-gray-900 border-none">
                        <span className="text-gray-600 size-4">
                          <Unplug className="h-4 w-4" />
                        </span>
                        <span>MCP服务</span>
                      </button>
                    </li>
                  </ul>
                </nav>
              </div>
              
              {/* 右侧主工作区 */}
              <div className="flex-1 overflow-hidden p-6 bg-white">
                <div className="flex h-full w-full flex-col overflow-hidden">
                  
                  {/* 标题及添加按钮 */}
                  <div className="w-full bg-white pb-6">
                    <div className="mb-3 flex items-center justify-between">
                      <h2 className="text-lg font-semibold text-gray-800">MCP服务</h2>
                      
                      <button 
                        onClick={openAddModal}
                        className="justify-center whitespace-nowrap text-xs font-semibold bg-[#151517] text-white hover:bg-[#151517]/90 active:scale-98 rounded-lg px-3.5 flex h-8 items-center gap-1.5 border-none shadow-sm cursor-pointer transition-all"
                        type="button"
                      >
                        <span>添加 MCP</span>
                        <ChevronDown className="h-3.5 w-3.5 text-zinc-300" />
                      </button>
                    </div>
                    <div>
                      <p className="text-xs leading-6 text-gray-500">
                        开启新的 MCP 服务为 Data Agent 扩展更多工具，开启可在 Agent 使用界面 and 自定义 Agent 中选择该 MCP。
                      </p>
                    </div>
                  </div>
                  
                  {/* 搜索框与刷新栏 */}
                  <div className="flex flex-1 flex-col overflow-hidden">
                    <div className="mb-4 flex items-center justify-between">
                      <div className="relative max-w-sm flex-1">
                        <input 
                          className="flex w-full border border-gray-200 bg-white px-3 py-2 text-sm rounded-lg pl-8 focus:outline-none focus:border-gray-300 text-gray-800 placeholder-gray-400 h-8"
                          placeholder="请输入关键词进行搜索"
                          value={searchQuery}
                          onChange={(e) => setSearchQuery(e.target.value)}
                        />
                        <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400 size-4" />
                      </div>
                      
                      <button 
                        onClick={handleRefresh}
                        className="gap-2 whitespace-nowrap text-sm font-medium bg-white hover:bg-gray-50 text-gray-500 active:scale-95 ml-2 h-8 w-8 cursor-pointer rounded-md border border-gray-200 flex items-center justify-center transition-all"
                        title="刷新"
                      >
                        <RotateCw className={clsx("h-4 w-4", isRefreshing && "animate-spin")} />
                      </button>
                    </div>
                    
                    {/* 列表区（支持纵向滚动） */}
                    <div className="flex flex-1 flex-col gap-3 overflow-y-auto py-3 no-scrollbar">
                      {filteredList.length === 0 ? (
                        <div className="text-gray-400 mt-[100px] text-center text-sm font-medium">
                          暂无 MCP 服务配置
                        </div>
                      ) : (
                        filteredList.map((mcp) => (
                          <div 
                            key={mcp.id}
                            className="group cursor-pointer rounded-2xl border border-gray-200 bg-[#FAFAFA]/40 hover:bg-white py-4 pb-[18px] pt-4 transition-all hover:shadow-md"
                          >
                            <div className="flex items-center gap-3 border-gray-100 px-4">
                              {/* 左侧图标 */}
                              <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-md bg-gray-100 border border-gray-200/50">
                                <Unplug className="h-5 w-5 text-gray-500" />
                              </div>
                              
                              {/* 服务信息 */}
                              <div className="flex min-w-0 flex-1 flex-col justify-center text-left">
                                <h3 className="truncate text-sm font-semibold text-gray-800">{mcp.name}</h3>
                                <div className="mt-1 w-fit max-w-full truncate text-xs text-gray-400 select-text">
                                  {mcp.url}
                                </div>
                              </div>
                              
                              {/* 右侧操作按钮组 (hover 时才可见) */}
                              <div className="flex flex-shrink-0 items-center gap-1">
                                {/* 连通测试 */}
                                <div 
                                  onClick={(e) => handleTestConnection(mcp.id, e)}
                                  className="relative group/tooltip inline-flex h-8 w-8 items-center justify-center rounded-full transition-all cursor-pointer opacity-0 group-hover:opacity-100 hover:bg-gray-100 text-gray-400 hover:text-gray-800"
                                >
                                  <span className="absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 whitespace-nowrap rounded-lg bg-white border border-gray-300 px-2.5 py-1 text-xs text-[#151517] font-normal shadow-[0_2px_8px_rgba(0,0,0,0.06)] opacity-0 pointer-events-none group-hover/tooltip:opacity-100 transition-opacity duration-150 z-30 select-none">
                                    {testingId === mcp.id ? '测试连接中...' : testedId === mcp.id ? '连接成功！' : '连通测试'}
                                  </span>
                                  <RadioTower className={clsx(
                                    "h-[18px] w-[18px] transition-all",
                                    testingId === mcp.id && "animate-pulse text-indigo-500 scale-110",
                                    testedId === mcp.id && "text-green-500 scale-110"
                                  )} />
                                </div>

                                {/* 编辑 */}
                                <div 
                                  onClick={(e) => openEditModal(mcp, e)}
                                  className="relative group/tooltip inline-flex h-8 w-8 items-center justify-center rounded-full transition-all cursor-pointer opacity-0 group-hover:opacity-100 hover:bg-gray-100 text-gray-400 hover:text-gray-800" 
                                >
                                  <span className="absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 whitespace-nowrap rounded-lg bg-white border border-gray-300 px-2.5 py-1 text-xs text-[#151517] font-normal shadow-[0_2px_8px_rgba(0,0,0,0.06)] opacity-0 pointer-events-none group-hover/tooltip:opacity-100 transition-opacity duration-150 z-30 select-none">
                                    编辑
                                  </span>
                                  <Pen className="h-[18px] w-[18px]" />
                                </div>

                                {/* 删除 */}
                                <div 
                                  onClick={(e) => { e.stopPropagation(); handleDelete(mcp.id); }}
                                  className="relative group/tooltip inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-full opacity-0 transition-all hover:bg-gray-100 text-[#C5221F] group-hover:opacity-100" 
                                >
                                  <span className="absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 whitespace-nowrap rounded-lg bg-white border border-gray-300 px-2.5 py-1 text-xs text-[#151517] font-normal shadow-[0_2px_8px_rgba(0,0,0,0.06)] opacity-0 pointer-events-none group-hover/tooltip:opacity-100 transition-opacity duration-150 z-30 select-none">
                                    删除
                                  </span>
                                  <Trash2 className="h-[18px] w-[18px]" />
                                </div>
                                
                                {/* 开关控制 */}
                                <div className="flex h-6 items-center pl-1">
                                  <button 
                                    type="button" 
                                    onClick={(e) => { e.stopPropagation(); handleToggle(mcp.id); }}
                                    className={clsx(
                                      "inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors focus:outline-none",
                                      mcp.enabled ? "bg-[#151517]" : "bg-gray-200"
                                    )}
                                  >
                                    <span 
                                      className={clsx(
                                        "pointer-events-none block h-5 w-5 rounded-full bg-white shadow-md ring-0 transition-transform",
                                        mcp.enabled ? "translate-x-5" : "translate-x-0"
                                      )}
                                    />
                                  </button>
                                </div>
                              </div>
                            </div>
                            
                            {/* 元数据行 */}
                            <div className="mt-6 flex items-center overflow-x-auto px-4 text-xs text-gray-400 select-none">
                              {/* 状态 */}
                              <div className="flex flex-shrink-0 items-center">
                                <div className={clsx(
                                  "rounded-full px-2 py-0.5 font-semibold flex h-5 items-center border text-[11px]",
                                  mcp.enabled 
                                    ? "bg-green-50 text-green-600 border-green-200/80" 
                                    : "bg-gray-50 text-gray-400 border-gray-200"
                                )}>
                                  <CheckCircle2 className="mr-1 h-3.5 w-3.5" />
                                  {mcp.enabled ? "已就绪" : "已断开"}
                                </div>
                              </div>
                              <div className="mx-2 h-3 w-px flex-shrink-0 bg-gray-200" />
                              
                              {/* 类型 */}
                              <div className="flex flex-shrink-0 items-center gap-1">
                                <UserRound className="h-3 w-3" />
                                <span>{mcp.type}</span>
                              </div>
                              <div className="mx-2 h-3 w-px flex-shrink-0 bg-gray-200" />
                              
                              {/* ID 复制 */}
                              <div className="relative flex flex-shrink-0 items-center gap-1">
                                <span className="font-mono text-gray-400">{mcp.id}</span>
                                <Copy 
                                  onClick={(e) => handleCopyId(mcp.id, e)}
                                  className="h-3.5 w-3.5 cursor-pointer text-gray-400 hover:text-gray-600 transition-colors" 
                                />
                                {copiedId === mcp.id && (
                                  <span className="absolute -top-7 left-1/2 -translate-x-1/2 bg-gray-800 text-white text-[10px] px-1.5 py-0.5 rounded shadow-md z-20 animate-bounce">
                                    已复制
                                  </span>
                                )}
                              </div>
                              <div className="mx-2 h-3 w-px flex-shrink-0 bg-gray-200" />
                              
                              {/* 日期 */}
                              <div className="flex flex-shrink-0 items-center gap-1">
                                <Clock className="h-3.5 w-3.5" />
                                <span>{mcp.createdAt} 创建</span>
                              </div>
                              <div className="mx-2 h-3 w-px flex-shrink-0 bg-gray-200" />
                              
                              {/* 范围 */}
                              <div className="flex flex-shrink-0 items-center gap-1">
                                <Globe className="h-3.5 w-3.5" />
                                <span>{mcp.scope}</span>
                              </div>
                              <div className="mx-2 h-3 w-px flex-shrink-0 bg-gray-200" />
                              
                              {/* 协议 */}
                              <div className="flex flex-shrink-0 items-center gap-1">
                                <Cable className="h-3.5 w-3.5" />
                                <span>{mcp.protocol}</span>
                              </div>
                            </div>
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 600px 官网像素级高保真模态配置对话框 */}
      {isModalOpen && (
        <div className="fixed inset-0 bg-black/40 z-50 backdrop-blur-xs flex items-center justify-center p-4">
          <div 
            onClick={(e) => e.stopPropagation()}
            className="bg-white border border-gray-200 rounded-2xl shadow-xl p-6 w-[600px] max-h-[90vh] overflow-y-auto relative z-50 animate-in fade-in zoom-in-95 duration-200 text-left select-none text-gray-800 flex flex-col"
          >
            
            {/* 顶部标题区 */}
            <div className="flex flex-col space-y-1.5 mb-5">
              <h2 className="text-lg font-semibold leading-none tracking-tight text-gray-900">
                MCP配置
              </h2>
            </div>

            {/* 表单内容 */}
            <form onSubmit={handleSubmit} className="max-h-[72vh] flex-1 overflow-y-auto pr-1 space-y-3.5 py-2 pb-4 no-scrollbar">
              
              {/* 网络类型单选 */}
              <div className="space-y-2">
                <label className="text-sm font-medium leading-none text-gray-700">
                  <span className="mr-1 text-red-500">*</span>网络类型
                </label>
                
                <div className="flex gap-4">
                  {/* 公网 */}
                  <div 
                    onClick={() => setFormScope('公网')}
                    className={clsx(
                      "rounded-2xl border h-[58px] flex flex-1 items-center gap-2 px-3 py-2 transition-all focus-visible:ring-0 cursor-pointer",
                      formScope === '公网'
                        ? "border-indigo-300 from-indigo-50/40 to-indigo-50/30 bg-gradient-to-b shadow-sm"
                        : "border-gray-200 bg-white hover:bg-gray-50"
                    )}
                    role="button"
                  >
                    <div className="flex flex-1 items-start gap-3">
                      <button 
                        type="button" 
                        role="radio" 
                        aria-checked={formScope === '公网'}
                        className={clsx(
                          "aspect-square h-4 w-4 rounded-full border flex items-center justify-center mt-1 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500",
                          formScope === '公网' ? "border-indigo-600 text-indigo-600" : "border-gray-300"
                        )}
                      >
                        {formScope === '公网' && (
                          <span className="flex items-center justify-center">
                            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" className="lucide lucide-circle h-2.5 w-2.5 fill-current text-current"><circle cx="12" cy="12" r="10"></circle></svg>
                          </span>
                        )}
                      </button>
                      
                      <div className="flex flex-1 flex-col gap-0.5">
                        <span className="text-sm font-medium text-gray-800">公网</span>
                        <span className="text-xs text-gray-400">支持云端部署，适用于公网环境</span>
                      </div>
                    </div>
                  </div>

                  {/* 内网 */}
                  <div 
                    onClick={() => setFormScope('内网')}
                    className={clsx(
                      "rounded-2xl border h-[58px] flex flex-1 items-center gap-2 px-3 py-2 transition-all focus-visible:ring-0 cursor-pointer",
                      formScope === '内网'
                        ? "border-indigo-300 from-indigo-50/40 to-indigo-50/30 bg-gradient-to-b shadow-sm"
                        : "border-gray-200 bg-white hover:bg-gray-50"
                    )}
                    role="button"
                  >
                    <div className="flex flex-1 items-start gap-3">
                      <button 
                        type="button" 
                        role="radio" 
                        aria-checked={formScope === '内网'}
                        className={clsx(
                          "aspect-square h-4 w-4 rounded-full border flex items-center justify-center mt-1 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500",
                          formScope === '内网' ? "border-indigo-600 text-indigo-600" : "border-gray-300"
                        )}
                      >
                        {formScope === '内网' && (
                          <span className="flex items-center justify-center">
                            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" className="lucide lucide-circle h-2.5 w-2.5 fill-current text-current"><circle cx="12" cy="12" r="10"></circle></svg>
                          </span>
                        )}
                      </button>
                      
                      <div className="flex flex-1 flex-col gap-0.5">
                        <span className="text-sm font-medium text-gray-800">内网</span>
                        <span className="text-xs text-gray-400">支持私有部署VPC、VSW</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* 网格双列 (名称、传输类型) */}
              <div className="grid grid-cols-2 gap-4">
                {/* 名称 */}
                <div className="space-y-2">
                  <label className="text-sm font-medium leading-none text-gray-600">
                    <span className="mr-1 text-red-500">*</span>名称
                  </label>
                  <input 
                    className="flex w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-base focus:outline-none focus:border-gray-400 text-gray-800 placeholder-gray-400 h-8 md:text-sm focus-visible:ring-0"
                    name="name" 
                    placeholder="e.g. My Custom Server"
                    value={formName}
                    onChange={(e) => setFormName(e.target.value)}
                    required
                  />
                </div>

                {/* 传输类型 */}
                <div className="space-y-2 relative">
                  <label className="text-sm font-medium leading-none text-gray-600">
                    <span className="mr-1 text-red-500">*</span>传输类型
                  </label>
                  
                  <button 
                    onClick={() => setIsProtocolDropdownOpen(!isProtocolDropdownOpen)}
                    className="inline-flex items-center gap-2 whitespace-nowrap rounded-lg text-sm font-medium border border-gray-200 bg-white hover:bg-gray-50 px-4 min-h-6 min-w-20 justify-between text-gray-700 relative py-0 pr-8 group/item m-0 h-8 w-full outline-none cursor-pointer"
                    type="button"
                  >
                    {formProtocol}
                    <ChevronDown className="absolute right-2.5 h-4 w-4 text-gray-400 opacity-70" />
                  </button>

                  {isProtocolDropdownOpen && (
                    <>
                      <div 
                        className="fixed inset-0 z-40 bg-transparent" 
                        onClick={() => setIsProtocolDropdownOpen(false)}
                      />
                      <div className="absolute top-[58px] left-0 right-0 z-50 rounded-lg border border-gray-200 bg-white shadow-lg p-1 text-xs select-none animate-in fade-in slide-in-from-top-1 duration-100">
                        <div 
                          onClick={() => { setFormProtocol('SSE'); setIsProtocolDropdownOpen(false); }}
                          className={clsx(
                            "px-3 py-2 cursor-pointer hover:bg-gray-100 rounded-md text-gray-700 font-medium",
                            formProtocol === 'SSE' && "bg-gray-50 text-gray-900 font-semibold"
                          )}
                        >
                          SSE
                        </div>
                        <div 
                          onClick={() => { setFormProtocol('StreamableHttp'); setIsProtocolDropdownOpen(false); }}
                          className={clsx(
                            "px-3 py-2 cursor-pointer hover:bg-gray-100 rounded-md text-gray-700 font-medium",
                            formProtocol === 'StreamableHttp' && "bg-gray-50 text-gray-900 font-semibold"
                          )}
                        >
                          StreamableHttp
                        </div>
                      </div>
                    </>
                  )}
                </div>
              </div>

              {/* 描述文本域 */}
              <div className="space-y-2">
                <label className="text-sm font-medium leading-none text-gray-600">描述</label>
                <textarea 
                  className="flex w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-base focus:outline-none focus:border-gray-400 text-gray-800 placeholder-gray-400 min-h-20 resize-none md:text-sm focus-visible:ring-0"
                  name="description" 
                  placeholder="提供MCP文档或者说明，以明确如何以及何时使用此MCP"
                  value={formDescription}
                  onChange={(e) => setFormDescription(e.target.value)}
                />
              </div>

              {/* 服务器 URL */}
              <div className="space-y-2">
                <label className="text-sm font-medium leading-none text-gray-600">
                  <span className="mr-1 text-red-500">*</span>服务器url
                </label>
                <input 
                  className="flex h-10 w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-base focus:outline-none focus:border-gray-400 text-gray-800 placeholder-gray-400 md:text-sm focus-visible:ring-0"
                  name="endpoint" 
                  placeholder={formProtocol === 'SSE' ? "https://mcp.yourserver.com/sse" : "https://mcp.yourserver.com/mcp"}
                  value={formUrl}
                  onChange={(e) => setFormUrl(e.target.value)}
                  required
                />
              </div>

              {/* 请求头与阿里云身份携带 */}
              <div className="bg-gray-100/30 space-y-2 rounded-2xl border border-gray-200 p-3">
                <div className="-m-3 mb-2 flex items-center justify-between px-3 py-3 border-b border-gray-100 bg-gray-50/20">
                  <label className="text-sm font-medium leading-none text-gray-700">请求头</label>
                  
                  <div className="flex items-center gap-2">
                    <label className="mb-0 text-sm font-normal text-gray-600">自动携带阿里云身份</label>
                    <HelpCircle className="h-4 w-4 text-gray-400 opacity-80 cursor-help" />
                    
                    <button 
                      type="button" 
                      onClick={() => setFormAutoAliyunAuth(!formAutoAliyunAuth)}
                      className={clsx(
                        "inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors focus:outline-none",
                        formAutoAliyunAuth ? "bg-[#151517]" : "bg-gray-200"
                      )}
                    >
                      <span 
                        className={clsx(
                          "pointer-events-none block h-5 w-5 rounded-full bg-white shadow-md ring-0 transition-transform",
                          formAutoAliyunAuth ? "translate-x-5" : "translate-x-0"
                        )}
                      />
                    </button>
                  </div>
                </div>

                {/* 动态自定义请求头 */}
                {formHeaders.length > 0 && (
                  <div className="space-y-2 mb-3 max-h-[140px] overflow-y-auto no-scrollbar pr-1 pt-1">
                    {formHeaders.map((header, idx) => (
                      <div key={idx} className="grid grid-cols-12 gap-2 items-center">
                        <input 
                          type="text" 
                          placeholder="Header Key"
                          value={header.key}
                          onChange={(e) => handleHeaderChange(idx, 'key', e.target.value)}
                          className="col-span-5 border border-gray-200 bg-white px-2 py-1 text-xs rounded-md focus:outline-none focus:border-gray-400 text-gray-800 h-8"
                        />
                        <input 
                          type="text" 
                          placeholder="Header Value"
                          value={header.value}
                          onChange={(e) => handleHeaderChange(idx, 'value', e.target.value)}
                          className="col-span-6 border border-gray-200 bg-white px-2 py-1 text-xs rounded-md focus:outline-none focus:border-gray-400 text-gray-800 h-8"
                        />
                        <button 
                          type="button" 
                          onClick={() => handleRemoveHeader(idx)}
                          className="col-span-1 flex items-center justify-center text-gray-400 hover:text-red-500 rounded-md hover:bg-gray-100 h-8 transition-colors border-none bg-transparent cursor-pointer"
                        >
                          <X className="h-4 w-4" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}

                <button 
                  type="button"
                  onClick={handleAddHeader}
                  className="inline-flex items-center justify-center gap-1 text-xs font-semibold border border-gray-200 hover:bg-gray-50 bg-white text-[#151517] h-8 rounded-lg px-3 cursor-pointer shadow-2xs transition-colors"
                >
                  <Plus className="mr-0.5 h-3.5 w-3.5" />
                  添加请求头
                </button>
              </div>

              {/* 底部安全警告提示 Checkbox 及确定取消 */}
              <div className="flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2 gap-2 pt-4 border-t border-gray-100">
                <div className="flex w-full items-center justify-between">
                  {/* 复选框及安全提示标题 */}
                  <div className="flex items-center space-x-2 py-2">
                    <input 
                      type="checkbox"
                      id="security-notice"
                      checked={acceptNotice}
                      onChange={(e) => setAcceptNotice(e.target.checked)}
                      className="h-4 w-4 shrink-0 rounded border border-gray-300 text-[#151517] focus:ring-[#151517] cursor-pointer"
                    />
                    <label htmlFor="security-notice" className="text-sm font-medium text-gray-600 leading-none cursor-pointer">
                      MCP服务安全风险提示 <button type="button" className="text-blue-600 font-bold hover:underline bg-transparent border-none p-0 cursor-pointer">查看详情</button>
                    </label>
                  </div>
                  
                  {/* 保存与取消按钮组 */}
                  <div className="flex shrink-0 items-center space-x-4">
                    <button 
                      type="submit"
                      disabled={!formName.trim() || !formUrl.trim() || !acceptNotice}
                      className={clsx(
                        "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-lg text-sm font-medium h-10 px-4 py-2 border-none transition-all shadow-sm cursor-pointer truncate",
                        (!formName.trim() || !formUrl.trim() || !acceptNotice)
                          ? "bg-gray-100 text-gray-400 cursor-not-allowed opacity-60 pointer-events-none shadow-none"
                          : "bg-[#151517] text-white hover:bg-[#151517]/90 active:scale-98"
                      )}
                    >
                      保存
                    </button>
                    
                    <button 
                      type="button"
                      onClick={() => setIsModalOpen(false)}
                      className="inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-lg text-sm font-medium bg-gray-100 text-gray-600 hover:bg-gray-200 active:scale-98 h-10 px-4 py-2 border-none cursor-pointer transition-all truncate"
                    >
                      取消
                    </button>
                  </div>
                </div>
              </div>
            </form>

            {/* 右上角关闭 X */}
            <button 
              type="button" 
              onClick={() => setIsModalOpen(false)}
              className="absolute right-4 top-4 rounded opacity-70 transition-opacity hover:opacity-100 focus:outline-none text-gray-400 hover:text-gray-600 p-1 hover:bg-gray-100 border-none bg-transparent cursor-pointer"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
