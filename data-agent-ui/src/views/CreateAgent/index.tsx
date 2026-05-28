import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  ArrowLeft,
  ChevronRight,
  Save,
  Reply,
  Play,
  Share2,
  Sparkles,
  RefreshCw,
  Database,
  Loader2,
  Terminal,
  Key,
  Eye,
  EyeOff,
  Copy,
  Check,
  AlertCircle,
  CheckCircle2
} from 'lucide-react';

interface FormState {
  name: string;
  description: string;
  avatar: string; // 头像颜色渐变索引："0" - "6"
  prompt: string; // 系统提示词
  category: string; // 智能体分类
  tags: string; // 逗号分隔的标签字符串
}

interface AgentDatasourceVO {
  id: number;
  agentId: number;
  datasourceId: number;
  datasourceName?: string;
  datasourceType?: string;
  schemaStatus: string; // 'pending' | 'syncing' | 'success' | 'failed'
  embeddingStatus: string; // 'pending' | 'vectorizing' | 'success' | 'failed'
  lastSyncTime?: string;
  selectTables?: string[];
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

export const CreateAgent: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();

  // Tab 导航状态：'info' (智能体信息) | 'datasource' (数据源配置) | 'apikey' (API key相关)
  const [activeTab, setActiveTab] = useState<'info' | 'datasource' | 'apikey'>('info');

  // 是否为编辑模式
  const [isEdit, setIsEdit] = useState(false);
  const [agentId, setAgentId] = useState<number | null>(null);

  // 表单状态
  const [form, setForm] = useState<FormState>({
    name: '',
    description: '',
    avatar: '0',
    prompt: '',
    category: '',
    tags: '',
  });

  // 标签交互管理
  const [tagInput, setTagInput] = useState('');
  const [tagsList, setTagsList] = useState<string[]>([]);

  // 自定义轻量高颜值 Toast 提示状态
  const [notification, setNotification] = useState<{ msg: string; type: 'success' | 'error' } | null>(null);
  const showNotification = (msg: string, type: 'success' | 'error' = 'success') => {
    setNotification({ msg, type });
    setTimeout(() => setNotification(null), 3000);
  };

  // ==================== NL2SQL 相关数据状态 ====================
  const [datasources, setDatasources] = useState<{ id: number; name: string; type: string }[]>([]);
  const [selectedDsId, setSelectedDsId] = useState<string>('');
  const [allTables, setAllTables] = useState<{ tableName: string; comment?: string }[]>([]);
  const [selectedTables, setSelectedTables] = useState<string[]>([]);
  const [loadingTables, setLoadingTables] = useState(false);
  const [bindingVo, setBindingVo] = useState<AgentDatasourceVO | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [showUnbindConfirm, setShowUnbindConfirm] = useState(false); // 原位解绑二次确认状态

  // ==================== API Key 密钥管理状态 ====================
  const [apiKey, setApiKey] = useState<string>('');
  const [keyLoading, setKeyLoading] = useState(false);
  const [keyVisible, setKeyVisible] = useState(false);
  const [confirmReset, setConfirmReset] = useState(false);
  const [copySuccess, setCopySuccess] = useState(false);

  // 加载可用数据源列表
  const fetchDatasources = () => {
    fetch('/api/datasource')
      .then(res => res.json())
      .then(data => {
        if (data.success && data.data) {
          setDatasources(data.data);
        }
      })
      .catch(err => console.error('加载数据源失败:', err));
  };

  // 根据数据源ID加载其所有的表
  const loadTablesForDs = async (dsId: number, defaultSelected: string[] = []) => {
    setLoadingTables(true);
    try {
      const res = await fetch(`/api/datasource/${dsId}/tables`).then(r => r.json());
      if (res.success && res.data) {
        setAllTables(res.data);
        setSelectedTables(defaultSelected);
      }
    } catch (err) {
      console.error('加载数据表失败:', err);
    } finally {
      setLoadingTables(false);
    }
  };

  // 加载当前智能体关联的数据源绑定信息
  const loadBindingInfo = async (currAgentId: number) => {
    try {
      const res = await fetch(`/api/agent/${currAgentId}/datasource`).then(r => r.json());
      if (res.success && res.data) {
        setBindingVo(res.data);
        setSelectedDsId(String(res.data.datasourceId));
        const defaultSelected = res.data.selectTables || [];
        loadTablesForDs(res.data.datasourceId, defaultSelected);
      } else {
        setBindingVo(null);
        setSelectedDsId('');
        setAllTables([]);
        setSelectedTables([]);
      }
    } catch (err) {
      console.error('拉取当前绑定关系失败:', err);
    }
  };

  // 刷新当前绑定提取状态 (暂时保留，以备后续调用)
  /*
  const refreshBindingStatus = async () => {
    if (!agentId) return;
    setActionLoading(true);
    try {
      const res = await fetch(`/api/agent/${agentId}/datasource`).then(r => r.json());
      if (res.success && res.data) {
        setBindingVo(res.data);
      }
    } catch (err) {
      console.error('刷新状态失败:', err);
    } finally {
      setActionLoading(false);
    }
  };
  */

  // 加载 API Key 信息
  const loadApiKeyInfo = async (currAgentId: number) => {
    setKeyLoading(true);
    try {
      const res = await fetch(`/api/agent/${currAgentId}/api-key`).then(r => r.json());
      if (res.success && res.data) {
        setApiKey(res.data);
      } else {
        setApiKey('');
      }
    } catch (err) {
      console.error('拉取 API Key 失败:', err);
    } finally {
      setKeyLoading(false);
    }
  };

  // 检测是否是编辑模式并加载智能体详情
  useEffect(() => {
    fetchDatasources();

    const params = new URLSearchParams(location.search);
    const id = params.get('id');
    if (id) {
      setIsEdit(true);
      const parsedId = Number(id);
      setAgentId(parsedId);
      
      // 1. 拉取后端智能体详情
      fetch(`/api/agent/${parsedId}`)
        .then(res => res.json())
        .then(data => {
          if (data.success && data.data) {
            setForm({
              name: data.data.name || '',
              description: data.data.description || '',
              avatar: data.data.avatar || '0',
              prompt: data.data.prompt || '',
              category: data.data.category || '',
              tags: data.data.tags || '',
            });
            // 解析标签
            const parsedTags = data.data.tags ? data.data.tags.split(',') : [];
            setTagsList(parsedTags.filter((t: string) => t.trim().length > 0));
          } else {
            console.error('加载智能体失败:', data.message);
          }
        })
        .catch(err => console.error('网络请求失败:', err));

      // 2. 拉取数据源绑定
      loadBindingInfo(parsedId);

      // 3. 拉取 API Key 信息
      loadApiKeyInfo(parsedId);
    }
  }, [location]);

  // 头像、标签、表名变动等交互函数
  const handleRandomAvatar = () => {
    const nextIdx = (Number(form.avatar) + 1) % AVATAR_GRADIENTS.length;
    setForm(prev => ({ ...prev, avatar: String(nextIdx) }));
  };

  const handleAddTag = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      const val = tagInput.trim().replace(/,/g, '');
      if (val && !tagsList.includes(val)) {
        setTagsList([...tagsList, val]);
      }
      setTagInput('');
    }
  };

  const handleRemoveTag = (indexToRemove: number) => {
    setTagsList(tagsList.filter((_, idx) => idx !== indexToRemove));
  };

  const handleDsChange = async (e: React.ChangeEvent<HTMLSelectElement>) => {
    const dsId = e.target.value;
    setSelectedDsId(dsId);
    setSelectedTables([]);
    setAllTables([]);

    if (!dsId) return;

    setLoadingTables(true);
    try {
      const res = await fetch(`/api/datasource/${dsId}/tables`).then(r => r.json());
      if (res.success && res.data) {
        setAllTables(res.data);
      }
    } catch (err) {
      console.error('加载数据表失败:', err);
    } finally {
      setLoadingTables(false);
    }
  };

  const handleTableCheck = (tableName: string) => {
    setSelectedTables(prev => 
      prev.includes(tableName) 
        ? prev.filter(t => t !== tableName) 
        : [...prev, tableName]
    );
  };

  const toggleSelectAllTables = () => {
    if (selectedTables.length === allTables.length) {
      setSelectedTables([]);
    } else {
      setSelectedTables(allTables.map(t => t.tableName));
    }
  };

  // 绑定数据源 (即时提交)
  const handleBind = async () => {
    if (!agentId || !selectedDsId) return;
    setActionLoading(true);
    try {
      const res = await fetch(`/api/agent/${agentId}/datasource/${selectedDsId}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(selectedTables)
      }).then(r => r.json());

      if (res.success) {
        showNotification('数据源关联成功，后台元数据正在解析同步', 'success');
        await loadBindingInfo(agentId);
      } else {
        showNotification(res.message || '绑定失败', 'error');
      }
    } catch (err) {
      console.error(err);
      showNotification('绑定请求发生网络异常', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  // 执行解绑 (从 window.confirm 升级为高颜值原地 Popconfirm)
  const executeUnbind = async () => {
    if (!agentId) return;
    setActionLoading(true);
    try {
      const res = await fetch(`/api/agent/${agentId}/datasource`, {
        method: 'DELETE'
      }).then(r => r.json());

      if (res.success) {
        showNotification('已解除绑定并清理旧的向量数据', 'success');
        setBindingVo(null);
        setSelectedDsId('');
        setAllTables([]);
        setSelectedTables([]);
      } else {
        showNotification(res.message || '解绑失败', 'error');
      }
    } catch (err) {
      console.error(err);
      showNotification('解绑请求发生网络异常', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  // 手动强同步 Schema
  const handleSyncSchema = async () => {
    if (!agentId) return;
    setActionLoading(true);
    try {
      const res = await fetch(`/api/agent/${agentId}/schema/sync`, {
        method: 'POST'
      }).then(r => r.json());

      if (res.success) {
        showNotification('已成功触发元数据Schema提取与重新向量化', 'success');
        await loadBindingInfo(agentId);
      } else {
        showNotification(res.message || '同步失败', 'error');
      }
    } catch (err) {
      console.error(err);
      showNotification('同步请求发生网络异常', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  // ==================== API Key 动作 ====================
  const handleGenerateKey = async () => {
    if (!agentId) return;
    setKeyLoading(true);
    try {
      const res = await fetch(`/api/agent/${agentId}/api-key/generate`, {
        method: 'POST'
      }).then(r => r.json());

      if (res.success && res.data) {
        setApiKey(res.data);
        showNotification('成功为智能体生成 API Key', 'success');
      } else {
        showNotification(res.message || '生成 API Key 失败', 'error');
      }
    } catch (err) {
      console.error('网络请求错误:', err);
    } finally {
      setKeyLoading(false);
    }
  };

  const handleResetKey = async () => {
    if (!agentId) return;
    setKeyLoading(true);
    try {
      const res = await fetch(`/api/agent/${agentId}/api-key/reset`, {
        method: 'POST'
      }).then(r => r.json());

      if (res.success && res.data) {
        setApiKey(res.data);
        setConfirmReset(false);
        showNotification('已作废旧密钥，新 API Key 生成成功', 'success');
      } else {
        showNotification(res.message || '重置 API Key 失败', 'error');
      }
    } catch (err) {
      console.error('网络请求错误:', err);
    } finally {
      setKeyLoading(false);
    }
  };

  const handleCopyKey = () => {
    if (!apiKey) return;
    navigator.clipboard.writeText(apiKey);
    setCopySuccess(true);
    showNotification('已成功复制 API Key 至剪切板', 'success');
    setTimeout(() => setCopySuccess(false), 2000);
  };

  // 注入模版：游戏销售分析助手
  const handleInjectTemplate1 = () => {
    const template = `你是一个资深的游戏销量分析专家。
分析框架：
1. 需按日、周、月维度监控核心指标（GMV、游戏下载量、UV、付费转化率），分析整体趋势及环比波动。
2. 划分新老玩家、推广渠道、地域进行细分拆解，识别核心增长点与受众短板。
3. 结合玩家的行为路径（浏览→详情→下载→付费）开展漏斗分析，精确定位玩家流失环节。`;
    setForm(prev => ({ ...prev, prompt: template }));
  };

  // 注入模版：餐饮连锁分析助手
  const handleInjectTemplate2 = () => {
    const template = `你是一个专业的餐饮连锁销售管理顾问。
分析框架：
1. 重点分析各门店的客单价、营业额（GMV）、菜品销量、翻台率。
2. 对比午市、晚市不同时间段客流结构变化，评估运营效能。
3. 挖掘排名前 10 的爆款菜品 and 表现不佳的边缘菜品，并给出合理的备料及推广调整策略。`;
    setForm(prev => ({ ...prev, prompt: template }));
  };

  // 校验表单是否合法（名称和分类为必填项）
  const isFormValid = form.name.trim().length > 0 && form.category.trim().length > 0;

  // 执行保存操作 (全局)
  const handleSave = async (silent = false) => {
    if (!isFormValid) return null;

    const payload = {
      id: agentId || undefined,
      name: form.name.trim(),
      description: form.description.trim(),
      avatar: form.avatar,
      prompt: form.prompt.trim(),
      category: form.category.trim(),
      tags: tagsList.join(','),
    };

    try {
      const response = await fetch('/api/agent', {
        method: isEdit ? 'PUT' : 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
      });
      const result = await response.json();
      if (result.success) {
        const savedId = isEdit ? agentId! : result.data;
        
        if (!isEdit) {
          setAgentId(savedId);
          setIsEdit(true);
          // 就地静默重写 URL，将新建状态转换为编辑状态，无需退回主页即可解锁其他 Tab
          window.history.replaceState(null, '', `/agent/create?id=${savedId}`);
          loadBindingInfo(savedId);
          loadApiKeyInfo(savedId);
        }

        if (!silent) {
          showNotification(isEdit ? '智能体信息更新成功' : '智能体创建成功！已解锁数据源与密钥配置。', 'success');
          // 体验优化：如果是新建，保存后自动切换至数据源 Tab 方便用户继续配置
          if (!isEdit) {
            setActiveTab('datasource');
          }
        }
        return savedId;
      } else {
        showNotification(result.message || '保存失败', 'error');
        return null;
      }
    } catch (err) {
      console.error(err);
      showNotification('保存时发生网络异常', 'error');
      return null;
    }
  };

  // 调试：保存并携带 agentId 跳转到 Chat
  const handleDebug = async () => {
    const savedId = await handleSave(true);
    if (savedId) {
      navigate(`/chat?agentId=${savedId}`);
    }
  };

  // 发布：保存并自动激活发布状态
  const handlePublish = async () => {
    const savedId = await handleSave(true);
    if (!savedId) return;

    try {
      const response = await fetch(`/api/agent/${savedId}/publish`, {
        method: 'POST',
      });
      const result = await response.json();
      if (result.success) {
        showNotification('发布成功，智能体已处于发布状态', 'success');
        navigate('/agent');
      } else {
        showNotification(result.message || '发布状态设置失败', 'error');
      }
    } catch (err) {
      console.error(err);
      showNotification('发布时网络通讯异常', 'error');
    }
  };

  // 渲染同步状态徽章
  const renderStatusIndicator = (status: string) => {
    switch (status) {
      case 'syncing':
      case 'vectorizing':
        return (
          <>
            <span className="relative flex h-2 w-2 mr-1">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-indigo-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2 w-2 bg-indigo-500"></span>
            </span>
            <span className="text-indigo-650 text-[11px] font-bold">正在提取同步...</span>
          </>
        );
      case 'success':
        return (
          <>
            <span className="h-2 w-2 rounded-full bg-emerald-500 mr-1.5" />
            <span className="text-emerald-700 text-[11px] font-bold">Schema 提取成功</span>
          </>
        );
      case 'failed':
        return (
          <>
            <span className="h-2 w-2 rounded-full bg-rose-500 mr-1.5" />
            <span className="text-rose-700 text-[11px] font-bold">同步失败</span>
          </>
        );
      case 'pending':
      default:
        return (
          <>
            <span className="h-2 w-2 rounded-full bg-slate-400 mr-1.5" />
            <span className="text-slate-500 text-[11px] font-bold">等待同步中</span>
          </>
        );
    }
  };

  const avatarIndex = Number(form.avatar) || 0;

  return (
    <div className="relative h-full flex-1 overflow-hidden bg-white">
      
      {/* 轻量高颜值 Toast 提示框 */}
      {notification && (
        <div className={`fixed top-5 left-1/2 -translate-x-1/2 z-9999 flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium shadow-lg animate-in fade-in slide-in-from-top-4 duration-200 select-none ${
          notification.type === 'error' ? 'bg-red-900/90 text-white' : 'bg-gray-900/90 text-white'
        }`}>
          {notification.type === 'error' ? (
            <AlertCircle className="h-4 w-4 text-red-400 shrink-0" />
          ) : (
            <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
          )}
          <span>{notification.msg}</span>
        </div>
      )}

      <div className="relative m-2 h-[calc(100%-1rem)] w-[calc(100%-1rem)] rounded-lg border border-gray-200/80 shadow-sm bg-white flex flex-col justify-between overflow-hidden font-sans">
        
        {/* 顶部导航与分段式 Tab 控制器 */}
        <div className="flex-none bg-[#FAFAFA] border-b border-gray-200/80 select-none">
          
          <div className="flex items-center justify-between px-6 py-2.5">
            {/* 面包屑 */}
            <div className="flex h-6 items-center text-xs">
              <nav aria-label="breadcrumb">
                <ol className="flex flex-wrap items-center gap-1.5 break-words text-slate-400">
                  <li className="inline-flex items-center gap-1.5">
                    <a
                      className="transition-colors hover:text-[#2D336B] cursor-pointer"
                      onClick={() => navigate('/agent')}
                    >
                      自定义Agent
                    </a>
                  </li>
                  <li role="presentation" aria-hidden="true" className="text-slate-300">
                    <ChevronRight className="w-3 h-3" />
                  </li>
                  <li className="inline-flex items-center gap-1.5">
                    <span className="font-medium text-slate-700">
                      {isEdit ? '配置自定义Agent' : '创建自定义Agent'}
                    </span>
                  </li>
                </ol>
              </nav>
            </div>

            {/* Tab 栏 */}
            <div className="bg-gray-100 p-0.5 rounded-lg flex gap-0.5">
              {[
                { id: 'info', label: '智能体信息' },
                { id: 'datasource', label: '数据源配置' },
                { id: 'apikey', label: 'API Key 相关' }
              ].map(tab => (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => setActiveTab(tab.id as any)}
                  className={`px-4 py-1 text-[11px] font-semibold rounded-md transition-all duration-200 border-none focus:outline-none cursor-pointer ${
                    activeTab === tab.id
                      ? 'bg-white text-gray-800 shadow-sm'
                      : 'text-gray-500 hover:text-gray-700 bg-transparent'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </div>

          <div className="text-slate-900 flex h-10 w-full items-center gap-3 px-6 pb-2">
            <button
              onClick={() => navigate('/agent')}
              className="flex size-7 flex-none items-center justify-center rounded-md border border-gray-200 bg-white hover:bg-gray-50 text-gray-600 transition-colors focus:outline-none cursor-pointer"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
            </button>
            <span className="flex-none text-[14px] font-bold text-gray-800">
              {isEdit ? `编辑智能体: ${form.name || '未命名'}` : '配置新自定义智能体'}
            </span>
          </div>
        </div>

        {/* 主编辑渲染区分支 */}
        <div className="flex-grow min-h-0 p-6 overflow-hidden">
          
          {/* ==================== TAB 1: 智能体配置与 Prompt 编程区 ==================== */}
          {activeTab === 'info' && (
            <div className="h-full flex flex-row gap-6 overflow-hidden animate-in fade-in duration-300">
              
              {/* 左侧：基本资料 (340px) */}
              <div className="w-[340px] flex-none flex flex-col overflow-y-auto pr-1">
                <div className="bg-white border border-gray-200 rounded-lg p-5 space-y-5 shadow-sm">
                  
                  {/* 智能体名片 */}
                  <div className="flex items-center gap-3.5 select-none">
                    <div className={`relative size-12 rounded-lg bg-gradient-to-br ${AVATAR_GRADIENTS[avatarIndex]} flex items-center justify-center text-white text-base font-bold shadow-sm shrink-0 transition-all duration-300`}>
                      {form.name ? form.name.charAt(0).toUpperCase() : 'A'}
                      <button
                        type="button"
                        onClick={handleRandomAvatar}
                        title="更换头像风格"
                        className="absolute -bottom-1 -right-1 size-5.5 bg-white rounded-full border border-gray-200 flex items-center justify-center shadow-sm cursor-pointer hover:bg-gray-50 text-gray-500 transition-all duration-200 focus:outline-none"
                      >
                        <RefreshCw className="w-2.5 h-2.5" />
                      </button>
                    </div>
                    <div className="flex flex-col min-w-0">
                      <span className="text-xs font-bold text-gray-800 truncate">{form.name || '未命名智能体'}</span>
                      <span className="text-[10px] text-gray-400 mt-0.5">请在此编辑助手基本资料</span>
                    </div>
                  </div>

                  {/* 表单域 */}
                  <div className="space-y-4">
                    <div className="space-y-1.5">
                      <label className="font-bold text-[10px] text-slate-650 block">
                        <span className="text-rose-500 mr-0.5">*</span>智能体名称
                      </label>
                      <div className="relative">
                        <input
                          type="text"
                          maxLength={20}
                          value={form.name}
                          onChange={(e) => setForm({ ...form, name: e.target.value.slice(0, 20) })}
                          placeholder="请输入名称"
                          className="flex w-full rounded-md border border-gray-200 bg-white px-3 py-1.5 pr-10 text-xs h-8 placeholder:text-gray-400 focus:outline-none focus:border-[#2D336B] focus:ring-1 focus:ring-[#2D336B] transition-all"
                        />
                        <span className="absolute bottom-2 right-2.5 text-[8px] text-gray-400 select-none">
                          {form.name.length}/20
                        </span>
                      </div>
                    </div>

                    <div className="space-y-1.5">
                      <label className="font-bold text-[10px] text-slate-650 block">
                        <span className="text-rose-500 mr-0.5">*</span>智能体分类
                      </label>
                      <div className="relative">
                        <select
                          value={form.category}
                          onChange={(e) => setForm({ ...form, category: e.target.value })}
                          className="flex w-full rounded-md border border-gray-200 bg-white px-3 py-1 text-xs h-8 placeholder:text-gray-400 focus:outline-none focus:border-[#2D336B] focus:ring-1 focus:ring-[#2D336B] transition-all appearance-none cursor-pointer"
                        >
                          <option value="">-- 请选择分类 --</option>
                          <option value="NL2SQL助手">NL2SQL数据助手</option>
                          <option value="经营分析助手">经营分析助手</option>
                          <option value="报表提取专家">报表提取专家</option>
                          <option value="日常业务答疑">日常业务答疑</option>
                        </select>
                        <div className="absolute right-3 top-2.5 pointer-events-none text-gray-450 text-[8px]">▼</div>
                      </div>
                    </div>

                    <div className="space-y-1.5">
                      <label className="font-bold text-[10px] text-slate-650 block">
                        标签 (Enter 添加)
                      </label>
                      <div className="space-y-1.5">
                        <input
                          type="text"
                          value={tagInput}
                          onChange={(e) => setTagInput(e.target.value)}
                          onKeyDown={handleAddTag}
                          placeholder="输入标签并回车"
                          className="flex w-full rounded-md border border-gray-200 bg-white px-3 py-1.5 text-xs h-8 placeholder:text-gray-400 focus:outline-none focus:border-[#2D336B] focus:ring-1 focus:ring-[#2D336B] transition-all"
                        />
                        {tagsList.length > 0 && (
                          <div className="flex flex-wrap gap-1 max-h-[80px] overflow-y-auto mt-1">
                            {tagsList.map((tag, idx) => (
                              <span 
                                key={idx} 
                                className="inline-flex items-center gap-0.5 px-2 py-0.5 rounded-md text-[9px] font-semibold bg-gray-100 text-gray-700 border border-gray-200 shadow-sm select-none"
                              >
                                {tag}
                                <button
                                  type="button"
                                  onClick={() => handleRemoveTag(idx)}
                                  className="size-3.5 flex items-center justify-center rounded-full hover:bg-rose-100 text-gray-400 hover:text-rose-600 cursor-pointer text-[7px] font-bold p-0 shrink-0 transition-colors border-none bg-transparent focus:outline-none"
                                >
                                  ✕
                                </button>
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="space-y-1.5">
                      <label className="font-bold text-[10px] text-slate-650 block">
                        简要描述
                      </label>
                      <div className="relative">
                        <input
                          type="text"
                          maxLength={100}
                          value={form.description}
                          onChange={(e) => setForm({ ...form, description: e.target.value.slice(0, 100) })}
                          placeholder="请输入描述"
                          className="flex w-full rounded-md border border-gray-200 bg-white px-3 py-1.5 pr-10 text-xs h-8 placeholder:text-gray-400 focus:outline-none focus:border-[#2D336B] focus:ring-1 focus:ring-[#2D336B] transition-all"
                        />
                        <span className="absolute bottom-2 right-2.5 text-[8px] text-gray-400 select-none">
                          {form.description.length}/100
                        </span>
                      </div>
                    </div>
                  </div>

                </div>
              </div>

              {/* 右侧：“代码白”指令编辑器 (flex-1) */}
              <div className="flex-1 min-w-[400px] flex flex-col gap-4 overflow-y-auto px-1">
                <div className="flex-none flex items-center justify-between select-none">
                  <div className="flex items-center gap-1.5">
                    <Terminal className="w-3.5 h-3.5 text-[#2D336B]" />
                    <label className="font-bold text-xs text-gray-800">
                      系统提示词配置 (System Prompt)
                    </label>
                  </div>
                  <span className="text-[10px] text-gray-400">
                    系统指令将作为底层 LLM 决策的全局规则
                  </span>
                </div>

                {/* 提示词文本编辑器卡片 */}
                <div className="flex-grow min-h-[220px] relative border border-gray-200 rounded-lg overflow-hidden flex flex-col bg-white">
                  <div className="flex-none flex items-center justify-between border-b border-gray-200 px-4 py-2 bg-[#FAFAFA] text-[10px] text-gray-500 select-none">
                    <div className="flex items-center gap-1.5">
                      <div className="size-2 rounded-full bg-emerald-500 animate-pulse" />
                      <span className="font-bold text-gray-700">Prompt IDE 沙盒</span>
                    </div>
                    <div>
                      当前长度: <span className="font-bold text-[#2D336B]">{form.prompt.length}</span> 字符
                    </div>
                  </div>
                  <textarea
                    value={form.prompt}
                    onChange={(e) => setForm(prev => ({ ...prev, prompt: e.target.value }))}
                    placeholder="请输入详细的 Prompt 指令。例如：&#10;你是一个财务分析助手。你需要帮助我解析各季度的支出报表，计算环比变化情况并标记异常大额开销..."
                    className="w-full flex-1 p-4 text-xs font-mono leading-relaxed resize-none focus:outline-none bg-white placeholder:text-gray-400 text-gray-800 focus:bg-white"
                  />
                </div>

                {/* 快捷模板工具箱 */}
                <div className="bg-white border border-gray-200 rounded-lg p-4 space-y-3 shadow-sm flex-none select-none">
                  <div className="flex items-center gap-1.5">
                    <Sparkles className="w-3.5 h-3.5 text-[#2D336B]" />
                    <span className="text-xs font-bold text-gray-800">一键填充 Prompt 快捷模板</span>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <button
                      type="button"
                      onClick={handleInjectTemplate1}
                      className="text-left text-[11px] font-semibold text-gray-700 bg-white hover:bg-gray-50 border border-gray-200 rounded-md p-2.5 transition-all duration-200 cursor-pointer shadow-sm focus:outline-none"
                    >
                      🎮 游戏销量分析专家模版
                    </button>
                    <button
                      type="button"
                      onClick={handleInjectTemplate2}
                      className="text-left text-[11px] font-semibold text-gray-700 bg-white hover:bg-gray-50 border border-gray-200 rounded-md p-2.5 transition-all duration-200 cursor-pointer shadow-sm focus:outline-none"
                    >
                      🍕 餐饮连锁销售顾问模版
                    </button>
                  </div>
                </div>
              </div>

            </div>
          )}

          {/* ==================== TAB 2: 数据源配置 (NL2SQL 数据实验室) ==================== */}
          {activeTab === 'datasource' && (
            <div className="h-full flex flex-row gap-6 overflow-hidden animate-in fade-in duration-300">
              {!agentId ? (
                /* 锁定引导面板 */
                <div className="flex-1 flex flex-col items-center justify-center text-center p-8 bg-white border border-dashed border-gray-200 rounded-lg select-none">
                  <div className="size-14 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 shadow-inner mb-4">
                    <Key className="w-6 h-6 animate-pulse" />
                  </div>
                  <h4 className="text-sm font-bold text-slate-750">数据源配置锁定中</h4>
                  <p className="text-[11px] text-slate-400 mt-2 max-w-[340px] leading-relaxed">
                    当前智能体尚未保存。请先在 **智能体信息** 选项卡下输入智能体名称和分类，点击右下角 **保存** 创建后即可解锁该项配置。
                  </p>
                </div>
              ) : (
                /* 数据实验室分栏布局 */
                <>
                  {/* 左侧：数据源选择器 (360px) */}
                  <div className="w-[360px] flex-none flex flex-col overflow-y-auto pr-1">
                    <div className="bg-white border border-gray-200 rounded-lg p-5 space-y-4 shadow-sm h-full flex flex-col justify-between">
                      <div className="space-y-4">
                        <span className="text-xs font-bold text-slate-800 flex items-center gap-2 select-none">
                          <Database className="w-4 h-4 text-[#2D336B]" />
                          NL2SQL 关联数据环境
                        </span>
                        
                        <div className="space-y-2">
                          <label className="text-[10px] font-bold text-slate-500 block">选择关联的目标数据库</label>
                          <div className="relative select-none">
                            <select 
                              value={selectedDsId} 
                              onChange={handleDsChange}
                              className="w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-xs focus:outline-none focus:border-[#2D336B] focus:ring-1 focus:ring-[#2D336B]/10 transition-all appearance-none cursor-pointer"
                            >
                              <option value="">-- 未关联任何数据库 --</option>
                              {datasources.map(ds => (
                                <option key={ds.id} value={ds.id}>{ds.name} ({ds.type})</option>
                              ))}
                            </select>
                            <div className="absolute right-3 top-3 pointer-events-none text-slate-400 text-[10px]">▼</div>
                          </div>
                        </div>

                        {/* 绑定状态卡片 */}
                        {bindingVo && (
                          <div className="border border-gray-200 rounded-lg p-4 space-y-3 bg-[#FAFAFA] select-none shadow-sm animate-in fade-in duration-300">
                            <div className="flex justify-between items-center text-xs pb-2 border-b border-gray-200">
                              <span className="text-slate-500 font-medium">已关联数据源</span>
                              <span className="font-extrabold text-slate-800">{bindingVo.datasourceName || '未知'}</span>
                            </div>
                            
                            <div className="flex flex-col gap-2 text-xs pt-1">
                              <div className="flex justify-between items-center bg-white border border-gray-200 rounded-md px-3 py-2">
                                <span className="text-[10px] text-slate-400 font-bold">元数据 (Schema)</span>
                                <span className="font-extrabold flex items-center gap-1">
                                  {renderStatusIndicator(bindingVo.schemaStatus)}
                                </span>
                              </div>
                            </div>

                            {bindingVo.lastSyncTime && (
                              <div className="text-[8px] text-slate-400 font-mono text-right mt-1">
                                最近同步: {bindingVo.lastSyncTime.replace('T', ' ').substring(0, 19)}
                              </div>
                            )}
                          </div>
                        )}
                      </div>

                      {/* 绑定与解绑操作区 */}
                      <div className="space-y-2.5 pt-4 border-t border-gray-250 select-none">
                        {!bindingVo ? (
                          <button
                            type="button"
                            disabled={!selectedDsId || actionLoading}
                            onClick={handleBind}
                            className={`w-full inline-flex items-center justify-center gap-1.5 rounded-md font-semibold text-xs h-9 text-white transition-all border-none ${
                              selectedDsId && !actionLoading
                                ? 'bg-[#2D336B] hover:bg-[#1C214C] cursor-pointer shadow-sm active:scale-95'
                                : 'bg-gray-100 text-gray-400 cursor-not-allowed opacity-60'
                            }`}
                          >
                            {actionLoading ? (
                              <>
                                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                                绑定同步中...
                              </>
                            ) : (
                              '保存并开始自动同步'
                            )}
                          </button>
                        ) : (
                          <div className="space-y-3">
                            {/* 原地气泡确认 */}
                            {showUnbindConfirm ? (
                              <div className="bg-red-50/50 border border-red-200 rounded-lg p-3.5 space-y-3 select-none animate-in slide-in-from-top-1 duration-200">
                                <div className="flex items-start gap-2">
                                  <AlertCircle className="w-4 h-4 text-red-650 shrink-0 mt-0.5" />
                                  <div className="text-[10px] text-red-800 leading-relaxed font-semibold">
                                    确定要解除数据源绑定吗？这将会彻底清空已存入向量库的表及字段 Schema 向量数据。
                                  </div>
                                </div>
                                <div className="flex gap-2 justify-end">
                                  <button
                                    type="button"
                                    onClick={() => setShowUnbindConfirm(false)}
                                    className="px-2.5 py-1 rounded bg-white border border-gray-200 text-gray-700 font-semibold text-[10px] cursor-pointer hover:bg-gray-50 focus:outline-none"
                                  >
                                    取消
                                  </button>
                                  <button
                                    type="button"
                                    disabled={actionLoading}
                                    onClick={async () => {
                                      await executeUnbind();
                                      setShowUnbindConfirm(false);
                                    }}
                                    className="px-2.5 py-1 rounded bg-red-600 text-white font-semibold text-[10px] cursor-pointer hover:bg-red-700 border-none shadow-sm focus:outline-none"
                                  >
                                    确认解绑
                                  </button>
                                </div>
                              </div>
                            ) : (
                              <div className="flex gap-2 animate-in fade-in duration-200">
                                <button
                                  type="button"
                                  disabled={actionLoading}
                                  onClick={handleSyncSchema}
                                  className="flex-1 inline-flex items-center justify-center gap-1.5 rounded-md font-semibold text-xs h-9 bg-white border border-gray-200 hover:bg-gray-50 text-gray-700 cursor-pointer shadow-sm transition-all active:scale-95"
                                >
                                  {actionLoading ? (
                                    <Loader2 className="w-3.5 h-3.5 animate-spin text-[#2D336B]" />
                                  ) : (
                                    <RefreshCw className="w-3 h-3" />
                                  )}
                                  强刷同步
                                </button>
                                
                                <button
                                  type="button"
                                  disabled={actionLoading}
                                  onClick={() => setShowUnbindConfirm(true)}
                                  className="flex-1 inline-flex items-center justify-center gap-1.5 rounded-md font-semibold text-xs h-9 bg-red-50 border border-red-200 hover:bg-red-100/50 text-red-655 cursor-pointer shadow-sm transition-all active:scale-95"
                                >
                                  解除绑定
                                </button>
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* 右侧：表网格与元数据监控 (flex-1) */}
                  <div className="flex-grow flex flex-col min-h-0 bg-white border border-gray-200 rounded-lg p-5 shadow-sm overflow-hidden">
                    {selectedDsId ? (
                      <div className="flex-1 flex flex-col min-h-0 space-y-4">
                        <div className="flex justify-between items-center text-xs select-none flex-none">
                          <span className="font-semibold text-gray-800">同步的业务数据表 ({selectedTables.length} / {allTables.length})</span>
                          <button 
                            type="button" 
                            onClick={toggleSelectAllTables}
                            className="text-[#2D336B] hover:text-[#1C214C] hover:underline cursor-pointer border-none bg-transparent text-[10px] font-semibold transition-all focus:outline-none"
                          >
                            {selectedTables.length === allTables.length ? '全不选' : '全选'}
                          </button>
                        </div>
                        
                        {loadingTables ? (
                          <div className="flex-1 flex flex-col items-center justify-center text-slate-400 text-xs bg-[#FAFAFA] border border-gray-200 rounded-lg">
                            <Loader2 className="w-6 h-6 animate-spin text-[#2D336B] mb-2" />
                            分析读取数据表中...
                          </div>
                        ) : allTables.length === 0 ? (
                          <div className="text-[11px] text-slate-400 bg-slate-55 border border-gray-200 rounded-lg p-4 text-center select-none flex-1 flex items-center justify-center">
                            该数据源下未发现任何可同步的数据表
                          </div>
                        ) : (
                          /* 网格化大表卡片 Bento Card Grid */
                          <div className="flex-grow overflow-y-auto border border-gray-200 rounded-lg bg-[#FAFAFA] p-4 shadow-inner">
                            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
                              {allTables.map(table => {
                                const isChecked = selectedTables.includes(table.tableName);
                                return (
                                  <label 
                                    key={table.tableName} 
                                    className={`flex items-start gap-3 p-3 rounded-lg cursor-pointer text-xs select-none transition-all duration-200 border hover:scale-[1.01] ${
                                      isChecked 
                                        ? 'bg-[#2D336B]/5 border-[#2D336B] shadow-sm' 
                                        : 'bg-white hover:bg-gray-50 border-gray-200'
                                    }`}
                                  >
                                    <input
                                      type="checkbox"
                                      checked={isChecked}
                                      onChange={() => handleTableCheck(table.tableName)}
                                      className="mt-0.5 rounded text-[#2D336B] focus:ring-[#2D336B]/20 cursor-pointer"
                                    />
                                    <div className="flex flex-col min-w-0">
                                      <span className={`font-bold transition-colors truncate ${isChecked ? 'text-gray-800' : 'text-gray-650'}`}>
                                        {table.tableName}
                                      </span>
                                      {table.comment && (
                                        <span className="text-[9px] text-slate-400 truncate mt-1">
                                          {table.comment}
                                        </span>
                                      )}
                                    </div>
                                  </label>
                                );
                              })}
                            </div>
                          </div>
                        )}
                      </div>
                    ) : (
                      /* 提示先选择数据库 */
                      <div className="flex-1 flex flex-col items-center justify-center text-center p-8 text-slate-400 select-none">
                        <Database className="w-10 h-10 text-slate-300 animate-pulse mb-3" />
                        <span className="text-xs">请先在左侧选择需要绑定的目标数据库。</span>
                      </div>
                    )}
                  </div>
                </>
              )}
            </div>
          )}

          {/* ==================== TAB 3: API Key 密钥管理器 ==================== */}
          {activeTab === 'apikey' && (
            <div className="h-full flex items-center justify-center animate-in fade-in duration-300">
              {!agentId ? (
                /* 锁定引导面板 */
                <div className="w-[480px] flex flex-col items-center justify-center text-center p-8 bg-white border border-dashed border-gray-200 rounded-lg select-none">
                  <div className="size-14 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 shadow-inner mb-4">
                    <Key className="w-6 h-6 animate-pulse" />
                  </div>
                  <h4 className="text-sm font-bold text-slate-750">API Key 锁屏中</h4>
                  <p className="text-[11px] text-slate-400 mt-2 max-w-[340px] leading-relaxed">
                    当前智能体尚未保存。请先在 **智能体信息** 选项卡下输入基本信息并点击 **保存** 创建后即可解锁该项配置。
                  </p>
                </div>
              ) : (
                /* 独立高配置 API Key 面板 */
                <div className="bg-white border border-gray-200 rounded-lg w-[560px] max-w-full shadow-sm p-8 flex flex-col gap-6 relative animate-in zoom-in-95 duration-200">
                  <div className="space-y-1 border-b border-gray-200 pb-4 select-none">
                    <h3 className="text-sm font-semibold text-gray-800 flex items-center gap-2">
                      <Key className="w-4.5 h-4.5 text-[#2D336B]" />
                      API Key 极客密钥配置
                    </h3>
                    <p className="text-[10px] text-gray-400 leading-normal">
                      管理该智能体的 API 调用凭证，可以通过该 Key 将智能体的 NL2SQL 功能嵌入第三方系统。
                    </p>
                  </div>

                  {keyLoading ? (
                    <div className="flex flex-col items-center justify-center py-10 gap-2 select-none">
                      <Loader2 className="w-6 h-6 text-[#2D336B] animate-spin" />
                      <span className="text-[10px] text-slate-400">正在与服务器同步 API Key...</span>
                    </div>
                  ) : (
                    <div className="space-y-4">
                      
                      {!apiKey ? (
                        /* 状态 1：尚未生成 API Key */
                        <div className="space-y-4 py-4 text-center select-none">
                          <AlertCircle className="w-10 h-10 text-slate-355 mx-auto" />
                          <p className="text-[11px] text-slate-550 max-w-[280px] mx-auto leading-relaxed">
                            该智能体目前尚未拥有 API 密钥凭证，无法从外部进行接口拉取。
                          </p>
                          <button
                            type="button"
                            onClick={handleGenerateKey}
                            className="inline-flex items-center justify-center gap-1.5 rounded-md font-semibold text-xs h-9 px-6 text-white bg-[#2D336B] hover:bg-[#1C214C] cursor-pointer shadow-sm transition-all active:scale-95 border-none focus:outline-none"
                          >
                            <Key className="w-3.5 h-3.5" />
                            生成第一颗 API Key
                          </button>
                        </div>
                      ) : (
                        /* 状态 2：已有 API Key，显示管理面板 */
                        <div className="space-y-5">
                          <div className="space-y-2">
                            <label className="text-[10px] font-bold text-slate-500 block select-none">调用密钥 (API KEY)</label>
                            <div className="flex items-center gap-2">
                              <div className="flex-1 bg-[#FAFAFA] text-gray-800 font-mono text-[11px] px-3.5 py-2.5 rounded-md select-all overflow-x-auto whitespace-nowrap scrollbar-none flex items-center justify-between border border-gray-200 shadow-inner">
                                <span>
                                  {keyVisible ? apiKey : `sk-${'•'.repeat(24)}`}
                                </span>
                                <button
                                  type="button"
                                  onClick={() => setKeyVisible(!keyVisible)}
                                  className="text-gray-400 hover:text-gray-700 cursor-pointer border-none bg-transparent ml-2 focus:outline-none shrink-0"
                                  title={keyVisible ? '隐藏' : '显示'}
                                >
                                  {keyVisible ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                                </button>
                              </div>
                              
                              <button
                                type="button"
                                onClick={handleCopyKey}
                                className="inline-flex items-center justify-center size-9 bg-white border border-gray-200 hover:bg-gray-50 rounded-md cursor-pointer shrink-0 transition-all active:scale-95 focus:outline-none"
                                title="复制到剪贴板"
                              >
                                {copySuccess ? <Check className="w-4 h-4 text-emerald-500" /> : <Copy className="w-4 h-4 text-slate-650" />}
                              </button>
                            </div>
                          </div>

                          {/* 重置 Key 的警示确认区域 */}
                          {confirmReset ? (
                            <div className="bg-red-50/50 border border-red-200 rounded-lg p-4 space-y-3 animate-in slide-in-from-top-1 duration-200 select-none">
                              <div className="flex items-start gap-2.5">
                                <AlertCircle className="w-4 h-4 text-red-650 shrink-0 mt-0.5" />
                                <div className="text-[10px] text-red-805 leading-relaxed font-semibold">
                                  警告：重置后原密钥立即失效！任何正在使用旧密钥的第三方生产应用将立即丧失访问权限。
                                </div>
                              </div>
                              <div className="flex gap-2 justify-end pt-1">
                                <button
                                  type="button"
                                  onClick={() => setConfirmReset(false)}
                                  className="px-3 py-1 rounded bg-white border border-gray-200 text-gray-700 font-semibold text-[10px] cursor-pointer hover:bg-gray-50 focus:outline-none"
                                >
                                  取消
                                </button>
                                <button
                                  type="button"
                                  onClick={handleResetKey}
                                  className="px-3 py-1 rounded bg-red-600 text-white font-semibold text-[10px] cursor-pointer hover:bg-red-700 focus:outline-none border-none shadow-sm"
                                >
                                  确认重置密钥
                                </button>
                              </div>
                            </div>
                          ) : (
                            <button
                              type="button"
                              onClick={() => setConfirmReset(true)}
                              className="w-full inline-flex items-center justify-center gap-1.5 rounded-md font-semibold text-xs h-9 bg-red-50 border border-red-200 hover:bg-red-100/50 text-red-655 cursor-pointer shadow-sm transition-all active:scale-95 focus:outline-none select-none"
                            >
                              作废并重置 API Key 凭证
                            </button>
                          )}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

        </div>

        {/* 底部悬浮操作栏 */}
        <div className="flex-none flex items-center justify-end gap-3 border-t border-gray-200 p-4 bg-[#FAFAFA] rounded-b-lg shadow-sm select-none">
          <button
            type="button"
            onClick={() => navigate('/agent')}
            className="inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-md font-semibold text-xs h-9 px-4 border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 transition-all focus:outline-none cursor-pointer hover:scale-102 active:scale-95"
          >
            <Reply className="w-3.5 h-3.5 text-slate-500" />
            返回列表
          </button>
          
          <div className="w-[1px] h-6 bg-gray-200/80 mx-1" />

          {/* 保存修改 */}
          <button
            type="button"
            disabled={!isFormValid}
            onClick={() => handleSave(false)}
            className={`inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-md font-semibold text-xs h-9 px-4 border border-transparent transition-all focus:outline-none ${
              isFormValid
                ? 'bg-[#2D336B] hover:bg-[#1C214C] text-white cursor-pointer active:scale-95 duration-150 shadow-sm border-none'
                : 'bg-gray-100 text-gray-400 cursor-not-allowed opacity-40'
            }`}
          >
            <Save className="w-3.5 h-3.5" />
            保存修改
          </button>
          
          <button
            type="button"
            disabled={!isFormValid}
            onClick={handleDebug}
            className={`inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-md font-semibold text-xs h-9 px-4 border border-transparent transition-all focus:outline-none ${
              isFormValid
                ? 'bg-[#2D336B] hover:bg-[#1C214C] text-white cursor-pointer active:scale-95 duration-150 shadow-sm border-none'
                : 'bg-gray-100 text-gray-400 cursor-not-allowed opacity-40'
            }`}
          >
            <Play className="w-3.5 h-3.5" />
            调试运行
          </button>

          <button
            type="button"
            disabled={!isFormValid}
            onClick={handlePublish}
            className={`inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-md font-semibold text-xs h-9 px-4 border border-transparent transition-all focus:outline-none ${
              isFormValid
                ? 'bg-[#2D336B] hover:bg-[#1C214C] text-white cursor-pointer active:scale-95 duration-150 shadow-sm border-none'
                : 'bg-gray-100 text-gray-400 cursor-not-allowed opacity-40'
            }`}
          >
            <Share2 className="w-3.5 h-3.5" />
            发布上线
          </button>
        </div>

      </div>
    </div>
  );
};
