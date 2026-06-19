import React, { useState, useEffect, useMemo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  ArrowLeft,
  ChevronDown,
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
  CheckCircle2,
  BookOpen,
  CalendarClock,
  ShieldCheck,
  Settings2
} from 'lucide-react';
import { useCurrentAgentStore } from '../../stores/currentAgent';

interface FormState {
  name: string;
  description: string;
  avatar: string; // 头像颜色渐变索引："0" - "6"
  prompt: string; // 系统提示词
  category: string; // 智能体分类
  tags: string; // 逗号分隔的标签字符串
  maxResultRows: number;
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

type AgentSection = 'overview' | 'info' | 'datasource' | 'apikey';
type ApiExampleLanguage = 'curl' | 'javascript' | 'python';

const AGENT_SECTIONS: AgentSection[] = ['overview', 'info', 'datasource', 'apikey'];
const DEFAULT_AGENT_SECTION: AgentSection = 'overview';
const API_EXAMPLE_LANGUAGES: { id: ApiExampleLanguage; label: string }[] = [
  { id: 'curl', label: 'curl' },
  { id: 'javascript', label: 'JavaScript' },
  { id: 'python', label: 'Python' }
];

const getAgentSectionFromSearch = (search: string): AgentSection => {
  const section = new URLSearchParams(search).get('tab') as AgentSection | null;
  return section && AGENT_SECTIONS.includes(section) ? section : DEFAULT_AGENT_SECTION;
};

const AVATAR_GRADIENTS = [
  'from-[#2D336B]/80 to-[#2D336B] shadow-[#2D336B]/10',
  'from-slate-500 to-slate-600 shadow-slate-500/10',
  'from-blue-500 to-blue-600 shadow-blue-500/10',
  'from-indigo-500 to-indigo-600 shadow-indigo-500/10',
  'from-cyan-500 to-cyan-600 shadow-cyan-500/10',
  'from-emerald-500 to-emerald-600 shadow-emerald-500/10',
  'from-zinc-500 to-zinc-600 shadow-zinc-500/10'
];

const SELECT_CLASS =
  'w-full h-9 rounded-md border border-gray-200 bg-slate-50/70 px-3 pr-9 text-xs font-medium text-slate-700 shadow-inner outline-none transition-all appearance-none cursor-pointer hover:border-slate-300 hover:bg-white focus:border-[#2D336B] focus:bg-white focus:ring-2 focus:ring-[#2D336B]/10';

const YIWEN_CARD_CLASS =
  'rounded-[14px] border border-[#dbe8f7] bg-white shadow-[0_8px_20px_rgba(31,74,125,0.05)]';

const YIWEN_ICON_TILE_CLASS =
  'flex size-9 flex-none items-center justify-center rounded-[10px] bg-[#eef5ff] text-[#2563eb]';

const DETAIL_PANEL_CLASS =
  'rounded-[18px] border border-[#dbe8f7] bg-[#fbfdff] shadow-[0_12px_30px_rgba(31,74,125,0.06)]';

export const CreateAgent: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const setCurrentAgent = useCurrentAgentStore((state) => state.setCurrentAgent);
  const queryAgentId = useMemo(() => new URLSearchParams(location.search).get('id'), [location.search]);
  const querySection = useMemo(() => getAgentSectionFromSearch(location.search), [location.search]);

  // 配置工作区：overview 汇总能力装配状态，其他 section 承载具体模块配置
  const [activeTab, setActiveTab] = useState<AgentSection>(querySection);

  // 是否为编辑模式
  const [isEdit, setIsEdit] = useState(false);
  const [agentId, setAgentId] = useState<number | null>(null);

  const navigateToSection = (section: AgentSection, replace = false) => {
    setActiveTab(section);
    const params = new URLSearchParams(location.search);
    if (agentId && !params.get('id')) {
      params.set('id', String(agentId));
    }
    params.set('tab', section);
    navigate(`${location.pathname}?${params.toString()}`, { replace });
  };

  // 表单状态
  const [form, setForm] = useState<FormState>({
    name: '',
    description: '',
    avatar: '0',
    prompt: '',
    category: '',
    tags: '',
    maxResultRows: 100,
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
  const [apiKeyEnabled, setApiKeyEnabled] = useState(false);
  const [keyLoading, setKeyLoading] = useState(false);
  const [keyVisible, setKeyVisible] = useState(false);
  const [confirmReset, setConfirmReset] = useState(false);
  const [copySuccess, setCopySuccess] = useState(false);
  const [apiExampleLanguage, setApiExampleLanguage] = useState<ApiExampleLanguage>('curl');
  const [exampleCopySuccess, setExampleCopySuccess] = useState(false);

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
        setApiKeyEnabled(false);
      }
    } catch (err) {
      console.error('拉取 API Key 失败:', err);
    } finally {
      setKeyLoading(false);
    }
  };

  // 检测是否是编辑模式并加载智能体详情
  useEffect(() => {
    setActiveTab(querySection);
  }, [querySection]);

  useEffect(() => {
    fetchDatasources();

    if (queryAgentId) {
      setIsEdit(true);
      const parsedId = Number(queryAgentId);
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
              maxResultRows: data.data.maxResultRows ?? 100,
            });
            setApiKeyEnabled(data.data.apiKeyEnabled === 1);
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
    } else {
      setIsEdit(false);
      setAgentId(null);
    }
  }, [queryAgentId]);

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

    if (bindingVo && String(bindingVo.datasourceId) === dsId) {
      await loadTablesForDs(Number(dsId), bindingVo.selectTables || []);
    }
  };

  const handleTableCheck = (tableName: string) => {
    if (!canConfigureTables) {
      showNotification('请先绑定数据源，再选择需要向量化的数据表', 'error');
      return;
    }
    setSelectedTables(prev => 
      prev.includes(tableName) 
        ? prev.filter(t => t !== tableName) 
        : [...prev, tableName]
    );
  };

  const toggleSelectAllTables = () => {
    if (!canConfigureTables) {
      showNotification('请先绑定数据源，再选择需要向量化的数据表', 'error');
      return;
    }
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
    if (!canConfigureTables) {
      showNotification('请先绑定数据源，再触发表同步与向量化', 'error');
      return;
    }
    if (selectedTables.length === 0) {
      showNotification('请至少选择一张需要向量化的数据表', 'error');
      return;
    }
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
        setApiKeyEnabled(true);
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
        setApiKeyEnabled(true);
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

  const handleToggleApiKeyEnabled = async () => {
    if (!agentId || !apiKey) return;
    setKeyLoading(true);
    try {
      const nextEnabled = !apiKeyEnabled;
      const res = await fetch(`/api/agent/${agentId}/api-key/${nextEnabled ? 'enable' : 'disable'}`, {
        method: 'POST'
      }).then(r => r.json());

      if (res.success) {
        setApiKeyEnabled(nextEnabled);
        showNotification(nextEnabled ? 'API Key 已启用' : 'API Key 已禁用', 'success');
      } else {
        showNotification(res.message || '更新 API Key 状态失败', 'error');
      }
    } catch (err) {
      console.error('更新 API Key 状态失败:', err);
      showNotification('更新 API Key 状态时发生网络异常', 'error');
    } finally {
      setKeyLoading(false);
    }
  };

  const handleDeleteKey = async () => {
    if (!agentId || !apiKey) return;
    setKeyLoading(true);
    try {
      const res = await fetch(`/api/agent/${agentId}/api-key`, {
        method: 'DELETE'
      }).then(r => r.json());

      if (res.success) {
        setApiKey('');
        setApiKeyEnabled(false);
        setKeyVisible(false);
        setConfirmReset(false);
        showNotification('API Key 已删除', 'success');
      } else {
        showNotification(res.message || '删除 API Key 失败', 'error');
      }
    } catch (err) {
      console.error('删除 API Key 失败:', err);
      showNotification('删除 API Key 时发生网络异常', 'error');
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

  const handleCopyExample = (example: string) => {
    navigator.clipboard.writeText(example);
    setExampleCopySuccess(true);
    showNotification('已复制调用示例', 'success');
    setTimeout(() => setExampleCopySuccess(false), 2000);
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
      maxResultRows: form.maxResultRows,
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
          navigate(`/agent/create?id=${savedId}&tab=${silent ? activeTab : 'datasource'}`, { replace: true });
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

  // 调试：保存并切换当前智能体后跳转到 Chat
  const handleDebug = async () => {
    if (!isFormValid) {
      navigateToSection('info');
      return;
    }
    if (!canConfigureTables) {
      showNotification('请先绑定数据源，再调试运行', 'error');
      navigateToSection('datasource');
      return;
    }
    const savedId = await handleSave(true);
    if (savedId) {
      setCurrentAgent({ agentId: String(savedId), agentName: form.name || '自定义智能体' });
      navigate('/chat');
    }
  };

  // 发布：保存并自动激活发布状态
  const handlePublish = async () => {
    if (!isFormValid) {
      navigateToSection('info');
      return;
    }
    if (!canConfigureTables) {
      showNotification('发布前请先绑定数据源', 'error');
      navigateToSection('datasource');
      return;
    }
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

  const boundDatasourceId = bindingVo?.datasourceId ? String(bindingVo.datasourceId) : '';
  const isDatasourceBound = Boolean(bindingVo && boundDatasourceId);
  const hasDatasourceSelectionChanged = Boolean(selectedDsId && selectedDsId !== boundDatasourceId);
  const canConfigureTables = isDatasourceBound && selectedDsId === boundDatasourceId;
  const canRunAgent = isFormValid && canConfigureTables && !hasDatasourceSelectionChanged;
  const selectedDatasource = datasources.find(ds => String(ds.id) === selectedDsId);
  const shouldShowBindAction = Boolean(selectedDsId) && (!bindingVo || hasDatasourceSelectionChanged);

  const tabCompletion = {
    info: isFormValid,
    datasource: canConfigureTables,
    apikey: Boolean(apiKey)
  };

  const selectedTableSummary = `${selectedTables.length}/${allTables.length || 0} 张表`;
  const datasourceStatusText = canConfigureTables
    ? `${bindingVo?.datasourceName || selectedDatasource?.name || '已绑定数据源'} · 已选 ${selectedTableSummary}`
    : agentId
      ? '待绑定数据源并选择分析表'
      : '保存基础信息后解锁';

  const readinessItems = [
    { label: '基础信息已完成', done: isFormValid, detail: isFormValid ? '名称与分类可用' : '需要名称和分类' },
    { label: '数据源已绑定', done: canConfigureTables, detail: canConfigureTables ? bindingVo?.datasourceName || '已绑定' : '调试前需要绑定' },
    { label: `已选 ${selectedTableSummary}`, done: selectedTables.length > 0, detail: selectedTables.length > 0 ? '将参与 Schema 召回' : '建议至少选择 1 张表' },
    { label: 'API Key 已生成', done: Boolean(apiKey), detail: apiKey ? (apiKeyEnabled ? '已启用' : '当前禁用') : '按需生成' },
    { label: '知识库未绑定', done: false, detail: '知识库绑定能力即将接入' },
    { label: '周期任务未配置', done: false, detail: '周期运行能力即将接入' }
  ];
  const pendingReadinessCount = readinessItems.filter(item => !item.done).length;

  const capabilityCards = [
    {
      id: 'info',
      title: '基础信息',
      description: isFormValid ? `${form.category} · ${form.name}` : '维护名称、分类、描述与系统 Prompt',
      status: isFormValid ? '已完成' : '待补全',
      icon: Settings2,
      enabled: true,
      action: '编辑',
      onClick: () => navigateToSection('info')
    },
    {
      id: 'datasource',
      title: '数据环境',
      description: datasourceStatusText,
      status: canConfigureTables ? '已完成' : '待配置',
      icon: Database,
      enabled: Boolean(agentId),
      action: agentId ? '配置' : '先保存',
      onClick: () => navigateToSection(agentId ? 'datasource' : 'info')
    },
    {
      id: 'knowledge',
      title: '知识库',
      description: '关联文档、FAQ 与业务知识，让 Agent 具备领域上下文',
      status: '规划中',
      icon: BookOpen,
      enabled: false,
      action: '预留',
      disabledReason: '知识库绑定能力即将接入'
    },
    {
      id: 'apikey',
      title: 'API 调用',
      description: apiKey ? (apiKeyEnabled ? '已生成并启用调用凭证' : '已生成调用凭证，当前禁用') : '生成或重置外部调用凭证',
      status: apiKey ? (apiKeyEnabled ? '已启用' : '已禁用') : '可选',
      icon: Key,
      enabled: Boolean(agentId),
      action: agentId ? '管理' : '先保存',
      onClick: () => navigateToSection(agentId ? 'apikey' : 'info')
    },
    {
      id: 'schedule',
      title: '周期运行',
      description: '按固定时间触发 Agent 分析任务并沉淀结果',
      status: '规划中',
      icon: CalendarClock,
      enabled: false,
      action: '预留',
      disabledReason: '周期运行能力即将接入'
    },
    {
      id: 'publish',
      title: '发布检查',
      description: pendingReadinessCount === 0 ? '全部检查项已完成，可以发布' : `${pendingReadinessCount} 项仍需处理`,
      status: pendingReadinessCount === 0 ? '已就绪' : '有阻塞',
      icon: ShieldCheck,
      enabled: true,
      action: '查看检查项',
      onClick: () => navigateToSection('overview')
    }
  ];

  const apiOrigin = typeof window !== 'undefined' ? window.location.origin : 'http://localhost:3000';
  const apiAgentId = agentId || 2;
  const apiKeyForExample = keyVisible && apiKey ? apiKey : '<your_api_key>';
  const sessionEndpoint = `${apiOrigin}/api/agent/${apiAgentId}/sessions`;
  const messageEndpoint = `${apiOrigin}/api/sessions/<sessionId>/messages`;
  const apiExamples: Record<ApiExampleLanguage, string> = {
    curl: `# 创建会话
curl -X POST "${sessionEndpoint}" \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: ${apiKeyForExample}" \\
  -d '{"title":"demo"}'

# 发送消息
curl -X POST "${messageEndpoint}" \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: ${apiKeyForExample}" \\
  -d '{"role":"user","content":"帮我分析一下本月销售额","messageType":"text","titleNeeded":true}'`,
    javascript: `const apiKey = "${apiKeyForExample}";

const sessionRes = await fetch("${sessionEndpoint}", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "X-API-Key": apiKey
  },
  body: JSON.stringify({ title: "demo" })
});
const session = await sessionRes.json();
const sessionId = session.data.id;

const messageRes = await fetch(\`${apiOrigin}/api/sessions/\${sessionId}/messages\`, {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "X-API-Key": apiKey
  },
  body: JSON.stringify({
    role: "user",
    content: "帮我分析一下本月销售额",
    messageType: "text",
    titleNeeded: true
  })
});
const message = await messageRes.json();`,
    python: `import requests

api_key = "${apiKeyForExample}"
headers = {
    "Content-Type": "application/json",
    "X-API-Key": api_key,
}

session_resp = requests.post(
    "${sessionEndpoint}",
    headers=headers,
    json={"title": "demo"},
)
session_id = session_resp.json()["data"]["id"]

message_resp = requests.post(
    f"${apiOrigin}/api/sessions/{session_id}/messages",
    headers=headers,
    json={
        "role": "user",
        "content": "帮我分析一下本月销售额",
        "messageType": "text",
        "titleNeeded": True,
    },
)`
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
              {([
                { id: 'overview' as const, label: '配置总览' },
                { id: 'info' as const, label: '智能体信息' },
                { id: 'datasource' as const, label: '数据环境' },
                { id: 'apikey' as const, label: 'API 调用' }
              ]).map(tab => {
                const isDone = tab.id === 'overview' ? pendingReadinessCount === 0 : tabCompletion[tab.id];
                const needsAttention = tab.id === 'datasource' && isFormValid && !canConfigureTables;
                return (
                  <button
                    key={tab.id}
                    type="button"
                    onClick={() => navigateToSection(tab.id)}
                    className={`px-3.5 py-1 text-[11px] font-semibold rounded-md transition-all duration-200 border-none focus:outline-none cursor-pointer inline-flex items-center gap-1.5 ${
                      activeTab === tab.id
                        ? 'bg-white text-gray-800 shadow-sm'
                        : 'text-gray-500 hover:text-gray-700 bg-transparent'
                    }`}
                  >
                    <span
                      className={`size-1.5 rounded-full ${
                        isDone
                          ? 'bg-emerald-500'
                          : needsAttention
                            ? 'bg-amber-500'
                            : 'bg-slate-300'
                      }`}
                    />
                    {tab.label}
                  </button>
                );
              })}
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

          {/* ==================== OVERVIEW: Agent 能力工作台 ==================== */}
          {activeTab === 'overview' && (
            <div className="h-full grid grid-cols-[minmax(0,1fr)_300px] gap-5 overflow-hidden animate-in fade-in duration-300">
              <div className="min-w-0 overflow-hidden">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 text-[11px] font-semibold text-slate-400 select-none">
                      <Sparkles className="h-3.5 w-3.5 text-[#2D336B]" />
                      Agent 能力工作台
                    </div>
                    <h2 className="mt-1 text-xl font-bold text-slate-900 tracking-normal">
                      {form.name || '未命名智能体'}
                    </h2>
                    <p className="mt-1 max-w-2xl text-xs leading-5 text-slate-500">
                      通过模块化能力装配来管理智能体：基础资料、数据环境、知识库、API 调用和周期运行可以独立配置，也会汇总到发布检查中。
                    </p>
                  </div>
                  <div className="flex flex-none items-center gap-2 rounded-md border border-gray-200 bg-[#FAFAFA] px-3 py-2 select-none">
                    <span className="text-[10px] font-bold text-slate-400">状态</span>
                    <span className={`rounded px-2 py-0.5 text-[10px] font-bold ${
                      isEdit ? 'bg-gray-100 text-gray-700' : 'bg-amber-50 text-amber-700 border border-amber-200'
                    }`}>
                      {isEdit ? '草稿配置中' : '待创建'}
                    </span>
                  </div>
                </div>

                <div className="mt-4 grid grid-cols-3 gap-3 select-none">
                  <div className={`${YIWEN_CARD_CLASS} px-4 py-3`}>
                    <div className="text-[11px] font-bold text-[#8fa1bb]">配置进度</div>
                    <div className="mt-1 text-xl font-extrabold text-[#020817]">{6 - pendingReadinessCount}/6</div>
                    <div className="mt-1 text-[11px] font-semibold text-[#63738a]">发布检查项完成度</div>
                  </div>
                  <div className={`${YIWEN_CARD_CLASS} px-4 py-3`}>
                    <div className="text-[11px] font-bold text-[#8fa1bb]">运行环境</div>
                    <div className="mt-1 truncate text-base font-extrabold text-[#020817]">
                      {bindingVo?.datasourceName || '未绑定数据源'}
                    </div>
                    <div className="mt-1 text-[11px] font-semibold text-[#63738a]">NL2SQL 数据环境</div>
                  </div>
                  <div className={`${YIWEN_CARD_CLASS} px-4 py-3`}>
                    <div className="text-[11px] font-bold text-[#8fa1bb]">调用方式</div>
                    <div className="mt-1 text-base font-extrabold text-[#020817]">
                      {apiKey ? 'API Key 已启用' : '仅站内调试'}
                    </div>
                    <div className="mt-1 text-[11px] font-semibold text-[#63738a]">外部系统接入状态</div>
                  </div>
                </div>

                <div className="mt-4 flex items-center justify-between select-none">
                  <div>
                    <h3 className="text-sm font-bold text-slate-900">能力装配</h3>
                    <p className="mt-0.5 text-[11px] text-slate-400">按能力模块管理 Agent，后续功能可以继续平滑扩展。</p>
                  </div>
                </div>

                <div className="mt-3 grid grid-cols-3 gap-3">
                  {capabilityCards.map(card => {
                    const Icon = card.icon;
                    const isBlocked = !card.enabled;
                    const statusClass = card.status === '已完成' || card.status === '已就绪' || card.status === '已启用'
                      ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                      : card.status === '有阻塞' || card.status === '待配置' || card.status === '已禁用'
                        ? 'bg-amber-50 text-amber-800 border-amber-200'
                        : 'bg-slate-50 text-slate-500 border-slate-200';

                    return (
                      <button
                        key={card.id}
                        type="button"
                        disabled={isBlocked}
                        onClick={card.onClick}
                        title={card.disabledReason || `进入${card.title}`}
                        className={`${YIWEN_CARD_CLASS} h-[104px] overflow-hidden p-3 text-left transition-all focus:outline-none focus:ring-2 focus:ring-[#2563eb]/20 ${
                          isBlocked
                            ? 'cursor-not-allowed opacity-75'
                            : 'cursor-pointer hover:-translate-y-0.5 hover:border-[#bfd5f1] hover:shadow-[0_12px_28px_rgba(31,74,125,0.09)] active:scale-[0.99]'
                        }`}
                      >
                        <div className="flex h-full flex-col justify-between">
                          <div className="flex items-start gap-3">
                            <div className={YIWEN_ICON_TILE_CLASS}>
                              <Icon className="h-5 w-5" strokeWidth={2.4} />
                            </div>
                            <div className="min-w-0 flex-1">
                              <div className="flex min-w-0 items-center justify-between gap-2">
                                <div className="flex min-w-0 items-center gap-1.5">
                                  <h4 className="truncate text-sm font-extrabold leading-tight text-[#020817]">{card.title}</h4>
                                  <span className={`rounded-full border px-2 py-0.5 text-[10px] font-bold ${statusClass}`}>
                                    {card.status}
                                  </span>
                                </div>
                                {!isBlocked && (
                                  <ChevronRight className="h-3.5 w-3.5 flex-none text-[#8fa1bb]" />
                                )}
                              </div>
                              <p className="mt-1.5 overflow-hidden text-[11px] font-semibold leading-4 text-[#5f718a] [display:-webkit-box] [-webkit-box-orient:vertical] [-webkit-line-clamp:2]">{card.description}</p>
                              {card.disabledReason && (
                                <p className="mt-1 truncate text-[10px] font-semibold text-[#8fa1bb]">{card.disabledReason}</p>
                              )}
                            </div>
                          </div>
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>

              <aside className={`${YIWEN_CARD_CLASS} min-h-0 overflow-hidden bg-[#fbfdff] p-4 select-none`}>
                <div className="flex items-center justify-between border-b border-[#e5eef9] pb-3">
                  <div>
                    <h3 className="text-lg font-extrabold text-[#020817]">上线检查</h3>
                    <p className="mt-0.5 text-[11px] font-semibold text-[#8fa1bb]">调试和发布前的关键依赖</p>
                  </div>
                  <span className={`rounded-full px-2.5 py-0.5 text-[10px] font-bold ${
                    pendingReadinessCount === 0
                      ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                      : 'bg-amber-50 text-amber-800 border border-amber-200'
                  }`}>
                    {pendingReadinessCount === 0 ? '可发布' : `${pendingReadinessCount} 项待处理`}
                  </span>
                </div>

                <div className="mt-3 space-y-2">
                  {readinessItems.map(item => (
                    <div key={item.label} className="flex items-start gap-2.5 rounded-[14px] border border-[#dbe8f7] bg-white px-3 py-2 shadow-[0_6px_18px_rgba(31,74,125,0.04)]">
                      <span className={`mt-0.5 flex size-4 flex-none items-center justify-center rounded-full ${
                        item.done ? 'bg-emerald-50 text-emerald-600' : 'bg-amber-50 text-amber-700'
                      }`}>
                        {item.done ? <Check className="h-2.5 w-2.5" /> : <AlertCircle className="h-2.5 w-2.5" />}
                      </span>
                      <div className="min-w-0">
                        <div className="truncate text-[11px] font-extrabold text-[#020817]">{item.label}</div>
                        <div className="mt-0.5 truncate text-[10px] font-semibold leading-4 text-[#8fa1bb]">{item.detail}</div>
                      </div>
                    </div>
                  ))}
                </div>

                <div className="mt-3 rounded-[14px] border border-dashed border-[#dbe8f7] bg-white px-3 py-3 text-[10px] font-semibold leading-4 text-[#63738a]">
                  发布检查只表达“是否具备上线条件”。数据源、知识库、周期任务仍由各自能力模块独立管理，后续新增 Controller 时可以继续增加能力卡。
                </div>
              </aside>
            </div>
          )}
          
          {/* ==================== TAB 1: 智能体配置与 Prompt 编程区 ==================== */}
          {activeTab === 'info' && (
            <div className="h-full flex flex-row gap-6 overflow-hidden animate-in fade-in duration-300">
              
              {/* 左侧：基本资料 (340px) */}
              <div className="w-[340px] flex-none flex flex-col overflow-y-auto pr-1">
                <div className={`${DETAIL_PANEL_CLASS} p-5 space-y-5`}>
                  
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
                          className={SELECT_CLASS}
                        >
                          <option value="">-- 请选择分类 --</option>
                          <option value="NL2SQL助手">NL2SQL数据助手</option>
                          <option value="经营分析助手">经营分析助手</option>
                          <option value="报表提取专家">报表提取专家</option>
                          <option value="日常业务答疑">日常业务答疑</option>
                        </select>
                        <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none h-3.5 w-3.5 text-slate-400" />
                      </div>
                    </div>

                    <div className="space-y-1.5">
                      <label className="font-bold text-[10px] text-slate-650 block">
                        SQL 最大返回行数
                      </label>
                      <input
                        type="number"
                        min={1}
                        max={1000}
                        value={form.maxResultRows}
                        onChange={(e) => setForm({
                          ...form,
                          maxResultRows: Math.min(1000, Math.max(1, Number(e.target.value) || 1)),
                        })}
                        className="flex w-full rounded-md border border-gray-200 bg-white px-3 py-1.5 text-xs h-8 placeholder:text-gray-400 focus:outline-none focus:border-[#2D336B] focus:ring-1 focus:ring-[#2D336B] transition-all"
                      />
                      <p className="text-[9px] leading-4 text-slate-400">
                        限制单次分析可返回的数据行数，范围 1–1000。
                      </p>
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
                <div className={`${DETAIL_PANEL_CLASS} flex-grow min-h-[220px] relative overflow-hidden flex flex-col bg-white`}>
                  <div className="flex-none flex items-center justify-between border-b border-[#e5eef9] px-4 py-2 bg-[#f7fbff] text-[10px] text-gray-500 select-none">
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
                <div className={`${DETAIL_PANEL_CLASS} p-4 space-y-3 flex-none select-none`}>
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
                <div className={`${DETAIL_PANEL_CLASS} flex-1 flex flex-col items-center justify-center text-center p-8 select-none`}>
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
                    <div className={`${DETAIL_PANEL_CLASS} p-5 space-y-4 h-full flex flex-col justify-between`}>
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
                              className={SELECT_CLASS}
                            >
                              <option value="">-- 未关联任何数据库 --</option>
                              {datasources.map(ds => (
                                <option key={ds.id} value={ds.id}>{ds.name} ({ds.type})</option>
                              ))}
                            </select>
                            <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none h-3.5 w-3.5 text-slate-400" />
                          </div>
                        </div>

                        {selectedDsId && !isDatasourceBound && (
                          <div className="rounded-lg border border-amber-200 bg-amber-50/70 px-3.5 py-3 text-[10px] leading-relaxed text-amber-800 select-none">
                            已选择 {selectedDatasource?.name || '目标数据源'}，但尚未绑定到当前 Agent。绑定完成后才能选择需要同步/向量化的数据表。
                          </div>
                        )}

                        {hasDatasourceSelectionChanged && (
                          <div className="rounded-lg border border-amber-200 bg-amber-50/70 px-3.5 py-3 text-[10px] leading-relaxed text-amber-800 select-none">
                            切换数据源会清空当前已选表和已有向量化结果，请先绑定新数据源。
                          </div>
                        )}

                        {/* 绑定状态卡片 */}
                        {bindingVo && (
                          <div className="rounded-[14px] border border-[#dbe8f7] bg-white p-4 space-y-3 select-none shadow-[0_8px_20px_rgba(31,74,125,0.04)] animate-in fade-in duration-300">
                            <div className="flex justify-between items-center text-xs pb-2 border-b border-[#e5eef9]">
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
                        {shouldShowBindAction ? (
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
                              hasDatasourceSelectionChanged ? '保存新数据源绑定' : '绑定数据源并初始化同步'
                            )}
                          </button>
                        ) : bindingVo ? (
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
                                  disabled={actionLoading || !canConfigureTables}
                                  onClick={handleSyncSchema}
                                  className={`flex-1 inline-flex items-center justify-center gap-1.5 rounded-md font-semibold text-xs h-9 border shadow-sm transition-all ${
                                    canConfigureTables
                                      ? 'bg-white border-gray-200 hover:bg-gray-50 text-gray-700 cursor-pointer active:scale-95'
                                      : 'bg-gray-100 border-gray-200 text-gray-400 cursor-not-allowed opacity-60'
                                  }`}
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
                        ) : (
                          <div className="rounded-lg border border-dashed border-gray-200 bg-slate-50 px-3 py-3 text-[10px] leading-relaxed text-slate-400">
                            选择一个数据源后，先完成绑定，再进入表选择和向量化配置。
                          </div>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* 右侧：表网格与元数据监控 (flex-1) */}
                  <div className={`${DETAIL_PANEL_CLASS} flex-grow flex flex-col min-h-0 p-5 overflow-hidden`}>
                    {selectedDsId ? (
                      !canConfigureTables ? (
                        <div className="flex-1 flex flex-col items-center justify-center text-center p-8 bg-[#f7fbff] border border-dashed border-[#dbe8f7] rounded-[14px] select-none">
                          <div className="size-12 rounded-full bg-white border border-gray-200 flex items-center justify-center text-slate-400 shadow-sm mb-4">
                            <Database className="w-5 h-5" />
                          </div>
                          <h4 className="text-sm font-bold text-slate-750">表选择暂未开放</h4>
                          <p className="text-[11px] text-slate-500 mt-2 max-w-[420px] leading-relaxed">
                            请先绑定数据源，绑定成功后才能选择需要同步/向量化的数据表。
                          </p>
                          <div className="mt-4 inline-flex items-center gap-1.5 rounded-md border border-amber-200 bg-amber-50 px-3 py-1.5 text-[10px] font-semibold text-amber-800">
                            <AlertCircle className="w-3.5 h-3.5" />
                            当前选择: {selectedDatasource?.name || '待绑定数据源'}
                          </div>
                        </div>
                      ) : (
                        <div className="flex-1 flex flex-col min-h-0 space-y-4">
                        <div className="flex justify-between items-center text-xs select-none flex-none">
                          <span className="font-semibold text-gray-800">同步的业务数据表 ({selectedTables.length} / {allTables.length})</span>
                          <button 
                            type="button" 
                            onClick={toggleSelectAllTables}
                            disabled={!canConfigureTables}
                            className={`border-none bg-transparent text-[10px] font-semibold transition-all focus:outline-none ${
                              canConfigureTables
                                ? 'text-[#2D336B] hover:text-[#1C214C] hover:underline cursor-pointer'
                                : 'text-slate-300 cursor-not-allowed'
                            }`}
                          >
                            {selectedTables.length === allTables.length ? '全不选' : '全选'}
                          </button>
                        </div>
                        
                        {loadingTables ? (
                          <div className="flex-1 flex flex-col items-center justify-center text-slate-400 text-xs bg-[#f7fbff] border border-[#dbe8f7] rounded-[14px]">
                            <Loader2 className="w-6 h-6 animate-spin text-[#2D336B] mb-2" />
                            分析读取数据表中...
                          </div>
                        ) : allTables.length === 0 ? (
                          <div className="text-[11px] text-slate-400 bg-[#f7fbff] border border-[#dbe8f7] rounded-[14px] p-4 text-center select-none flex-1 flex items-center justify-center">
                            该数据源下未发现任何可同步的数据表
                          </div>
                        ) : (
                          /* 网格化大表卡片 Bento Card Grid */
                          <div className="flex-grow overflow-y-auto border border-[#dbe8f7] rounded-[14px] bg-[#f7fbff] p-4 shadow-inner">
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
                      )
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
            <div className={`h-full animate-in fade-in duration-300 ${agentId ? 'overflow-hidden' : 'flex items-center justify-center'}`}>
              {!agentId ? (
                /* 锁定引导面板 */
                <div className={`${DETAIL_PANEL_CLASS} w-[480px] flex flex-col items-center justify-center text-center p-8 select-none`}>
                  <div className="size-14 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 shadow-inner mb-4">
                    <Key className="w-6 h-6 animate-pulse" />
                  </div>
                  <h4 className="text-sm font-bold text-slate-750">API Key 锁屏中</h4>
                  <p className="text-[11px] text-slate-400 mt-2 max-w-[340px] leading-relaxed">
                    当前智能体尚未保存。请先在 **智能体信息** 选项卡下输入基本信息并点击 **保存** 创建后即可解锁该项配置。
                  </p>
                </div>
              ) : (
                <div className="h-full w-full grid grid-cols-[420px_1fr] gap-5 overflow-hidden">
                  <div className={`${DETAIL_PANEL_CLASS} min-h-0 p-6 flex flex-col gap-5`}>
                    <div className="space-y-1 border-b border-[#e5eef9] pb-4 select-none">
                      <h3 className="text-sm font-semibold text-gray-800 flex items-center gap-2">
                        <Key className="w-4.5 h-4.5 text-[#2D336B]" />
                        API 调用凭证
                      </h3>
                      <p className="text-[10px] text-gray-400 leading-normal">
                        管理该智能体的外部调用凭证，调用时通过 X-API-Key 请求头完成鉴权。
                      </p>
                    </div>

                    <div className="rounded-[14px] border border-[#dbe8f7] bg-white px-4 py-3 select-none">
                      <div className="flex items-center justify-between gap-3">
                        <div>
                          <div className="text-[10px] font-bold text-[#8fa1bb]">API Key 状态</div>
                          <div className="mt-1 flex items-center gap-2">
                            <span className={`inline-flex size-2 rounded-full ${
                              apiKey ? (apiKeyEnabled ? 'bg-emerald-500' : 'bg-amber-500') : 'bg-slate-300'
                            }`} />
                            <span className="text-sm font-extrabold text-[#020817]">
                              {apiKey ? (apiKeyEnabled ? '已启用' : '已禁用') : '未生成'}
                            </span>
                          </div>
                        </div>
                        <button
                          type="button"
                          disabled={!apiKey || keyLoading}
                          onClick={handleToggleApiKeyEnabled}
                          className={`inline-flex h-8 items-center justify-center rounded-full px-4 text-xs font-bold transition-all focus:outline-none ${
                            apiKey && !keyLoading
                              ? apiKeyEnabled
                                ? 'cursor-pointer border border-amber-200 bg-amber-50 text-amber-800 hover:bg-amber-100/70 active:scale-95'
                                : 'cursor-pointer border border-emerald-200 bg-emerald-50 text-emerald-700 hover:bg-emerald-100/70 active:scale-95'
                              : 'cursor-not-allowed border border-[#dbe8f7] bg-[#f6f9fd] text-[#a8b4c4]'
                          }`}
                        >
                          {apiKeyEnabled ? '禁用' : '启用'}
                        </button>
                      </div>
                    </div>

                    {keyLoading ? (
                      <div className="flex flex-1 flex-col items-center justify-center py-10 gap-2 select-none">
                        <Loader2 className="w-6 h-6 text-[#2D336B] animate-spin" />
                        <span className="text-[10px] text-slate-400">正在与服务器同步 API Key...</span>
                      </div>
                    ) : (
                      <div className="flex flex-1 flex-col justify-between gap-5">
                        {!apiKey ? (
                          <div className="space-y-4 rounded-[14px] border border-dashed border-[#dbe8f7] bg-white px-5 py-6 text-center select-none">
                            <AlertCircle className="w-9 h-9 text-slate-355 mx-auto" />
                            <p className="text-[11px] text-slate-550 max-w-[280px] mx-auto leading-relaxed">
                              该智能体目前尚未拥有 API 密钥凭证，生成后即可使用右侧示例接入。
                            </p>
                            <button
                              type="button"
                              onClick={handleGenerateKey}
                              className="inline-flex items-center justify-center gap-1.5 rounded-md font-semibold text-xs h-9 px-6 text-white bg-[#2D336B] hover:bg-[#1C214C] cursor-pointer shadow-sm transition-all active:scale-95 border-none focus:outline-none"
                            >
                              <Key className="w-3.5 h-3.5" />
                              生成 API Key
                            </button>
                          </div>
                        ) : (
                          <div className="space-y-5">
                            <div className="space-y-2">
                              <label className="text-[10px] font-bold text-slate-500 block select-none">调用密钥 (API KEY)</label>
                              <div className="flex items-center gap-2">
                                <div className="flex-1 bg-[#f7fbff] text-gray-800 font-mono text-[11px] px-3.5 py-2.5 rounded-md select-all overflow-x-auto whitespace-nowrap scrollbar-none flex items-center justify-between border border-[#dbe8f7] shadow-inner">
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
                                  className="inline-flex items-center justify-center size-9 bg-white border border-[#dbe8f7] hover:bg-[#f7fbff] rounded-md cursor-pointer shrink-0 transition-all active:scale-95 focus:outline-none"
                                  title="复制到剪贴板"
                                >
                                  {copySuccess ? <Check className="w-4 h-4 text-emerald-500" /> : <Copy className="w-4 h-4 text-slate-650" />}
                                </button>
                              </div>
                            </div>

                            {confirmReset ? (
                              <div className="bg-red-50/50 border border-red-200 rounded-[14px] p-4 space-y-3 animate-in slide-in-from-top-1 duration-200 select-none">
                                <div className="flex items-start gap-2.5">
                                  <AlertCircle className="w-4 h-4 text-red-650 shrink-0 mt-0.5" />
                                  <div className="text-[10px] text-red-805 leading-relaxed font-semibold">
                                    警告：重置后原密钥立即失效，正在使用旧密钥的第三方应用将无法继续访问。
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
                              <div className="grid grid-cols-2 gap-2">
                                <button
                                  type="button"
                                  onClick={() => setConfirmReset(true)}
                                  className="inline-flex items-center justify-center gap-1.5 rounded-md font-semibold text-xs h-9 bg-red-50 border border-red-200 hover:bg-red-100/50 text-red-655 cursor-pointer shadow-sm transition-all active:scale-95 focus:outline-none select-none"
                                >
                                  重置 API Key
                                </button>
                                <button
                                  type="button"
                                  onClick={handleDeleteKey}
                                  className="inline-flex items-center justify-center gap-1.5 rounded-md font-semibold text-xs h-9 bg-white border border-red-200 hover:bg-red-50 text-red-655 cursor-pointer shadow-sm transition-all active:scale-95 focus:outline-none select-none"
                                >
                                  删除 API Key
                                </button>
                              </div>
                            )}
                          </div>
                        )}

                        <div className="rounded-[14px] border border-[#dbe8f7] bg-white px-4 py-3 text-[11px] font-semibold leading-5 text-[#63738a]">
                          推荐在服务端保存 API Key，避免暴露在浏览器、日志或客户端安装包中。
                        </div>
                      </div>
                    )}
                  </div>

                  <div className={`${DETAIL_PANEL_CLASS} min-h-0 overflow-hidden p-5 flex flex-col`}>
                    <div className="flex flex-none items-start justify-between gap-4 border-b border-[#e5eef9] pb-4 select-none">
                      <div>
                        <h3 className="flex items-center gap-2 text-sm font-extrabold text-[#020817]">
                          <Terminal className="h-4 w-4 text-[#2D336B]" />
                          调用示例
                        </h3>
                        <p className="mt-1 text-[11px] font-semibold text-[#8fa1bb]">
                          创建会话后发送消息，请在请求头中携带 X-API-Key。
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => handleCopyExample(apiExamples[apiExampleLanguage])}
                        className="inline-flex h-8 items-center justify-center gap-1.5 rounded-full border border-[#dbe8f7] bg-white px-3 text-[11px] font-bold text-[#2563eb] shadow-[0_6px_14px_rgba(31,74,125,0.06)] transition-all hover:bg-[#f7fbff] active:scale-95 focus:outline-none"
                      >
                        {exampleCopySuccess ? <Check className="h-3.5 w-3.5 text-emerald-500" /> : <Copy className="h-3.5 w-3.5" />}
                        {exampleCopySuccess ? '已复制' : '复制示例'}
                      </button>
                    </div>

                    <div className="mt-4 flex flex-none items-center gap-2 select-none">
                      {API_EXAMPLE_LANGUAGES.map(lang => (
                        <button
                          key={lang.id}
                          type="button"
                          onClick={() => setApiExampleLanguage(lang.id)}
                          className={`h-8 rounded-full px-3 text-[11px] font-bold transition-all focus:outline-none ${
                            apiExampleLanguage === lang.id
                              ? 'bg-[#2D336B] text-white shadow-[0_8px_18px_rgba(45,51,107,0.18)]'
                              : 'border border-[#dbe8f7] bg-white text-[#63738a] hover:bg-[#f7fbff]'
                          }`}
                        >
                          {lang.label}
                        </button>
                      ))}
                    </div>

                    <div className="mt-4 min-h-0 flex-1 overflow-hidden rounded-[14px] border border-[#111827] bg-[#0b1020] shadow-[0_16px_36px_rgba(15,23,42,0.16)]">
                      <pre className="h-full overflow-auto p-5 text-[12px] leading-6 text-slate-200">
                        <code>{apiExamples[apiExampleLanguage]}</code>
                      </pre>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}

        </div>

        {/* 底部悬浮操作栏：仅基础信息/总览保留全局保存、调试、发布动作 */}
        {(activeTab === 'overview' || activeTab === 'info') && (
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
            title={canRunAgent ? '调试运行' : '请先绑定数据源'}
            className={`inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-md font-semibold text-xs h-9 px-4 border border-transparent transition-all focus:outline-none ${
              canRunAgent
                ? 'bg-[#2D336B] hover:bg-[#1C214C] text-white cursor-pointer active:scale-95 duration-150 shadow-sm border-none'
                : isFormValid
                  ? 'bg-white border-amber-200 text-amber-800 hover:bg-amber-50 cursor-pointer active:scale-95 duration-150 shadow-sm'
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
            title={canRunAgent ? '发布上线' : '发布前请先绑定数据源'}
            className={`inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-md font-semibold text-xs h-9 px-4 border border-transparent transition-all focus:outline-none ${
              canRunAgent
                ? 'bg-[#2D336B] hover:bg-[#1C214C] text-white cursor-pointer active:scale-95 duration-150 shadow-sm border-none'
                : isFormValid
                  ? 'bg-white border-amber-200 text-amber-800 hover:bg-amber-50 cursor-pointer active:scale-95 duration-150 shadow-sm'
                : 'bg-gray-100 text-gray-400 cursor-not-allowed opacity-40'
            }`}
          >
            <Share2 className="w-3.5 h-3.5" />
            发布上线
          </button>
        </div>
        )}

      </div>
    </div>
  );
};
