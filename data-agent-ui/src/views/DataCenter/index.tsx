import React, { useState, useMemo, useEffect } from 'react';
import { useParams, useNavigate, useOutletContext } from 'react-router-dom';
import * as Tabs from '@radix-ui/react-tabs';
import * as Tooltip from '@radix-ui/react-tooltip';
import { 
  RefreshCw, 
  Plus, 
  Search, 
  Database, 
  FileText, 
  Loader2, 
  Trash2, 
  FileSpreadsheet,
  CheckCircle2
} from 'lucide-react';
import clsx from 'clsx';
import { CollapsedSidebarMenuButton } from '../../layout/CollapsedSidebarMenuButton';
import type { LayoutOutletContext } from '../../layout/GlobalLayout';

// 引入子组件
import { AddDataPanel } from './components/AddDataPanel';
import { DataSourceDetail } from './components/DataSourceDetail';
import { FileDetail } from './components/FileDetail';
import { SubTableDetail } from './components/SubTableDetail';

// 引入类型与 Mock 数据
import type { DataSource, UploadedFile } from './types';
import { INITIAL_FILES } from './mockData';

// 引入数据库 LOGO 静态资产
import mysqlLogo from '../../assets/logos/mysql.svg';
import postgresqlLogo from '../../assets/logos/postgresql.svg';
import oracleLogo from '../../assets/logos/oracle.svg';
import hbaseLogo from '../../assets/logos/hbase.svg';

const getDatabaseLogo = (type: string) => {
  const t = type?.toLowerCase();
  if (t === 'mysql') return mysqlLogo;
  if (t === 'postgresql' || t === 'postgres' || t === 'polardb') return postgresqlLogo;
  if (t === 'oracle') return oracleLogo;
  if (t === 'hbase') return hbaseLogo;
  return null;
};

export const DataCenter: React.FC = () => {
  const { database } = useParams();
  const navigate = useNavigate();
  const {
    isSidebarCollapsed,
    isSidebarVisible,
    expandSidebar,
  } = useOutletContext<LayoutOutletContext>();
  const [datasources, setDatasources] = useState<DataSource[]>([]);
  const [files, setFiles] = useState<UploadedFile[]>(INITIAL_FILES);

  // 获取后端真实数据源列表
  const fetchDatasources = async () => {
    try {
      const response = await fetch('/api/datasource');
      if (response.ok) {
        const result = await response.json();
        if (result.code === '0') {
          const list = (result.data || []).map((ds: any) => ({
            id: String(ds.id),
            name: ds.name,
            type: ds.type === 'mysql' ? 'MySQL' : ds.type === 'postgresql' ? 'PostgreSQL' : ds.type,
            host: ds.host,
            port: ds.port,
            database: ds.databaseName || '',
            username: ds.username,
            status: ds.status === 'active' ? 'online' : 'offline',
            createdAt: ds.createTime ? ds.createTime.replace('T', ' ').substring(0, 19) : '',
            // 缓存已选表，在后端表关系建立前提供良好的体验
            importedTables: JSON.parse(localStorage.getItem(`ds_imported_${ds.id}`) || '[]')
          }));
          setDatasources(list);
        }
      }
    } catch (e) {
      console.error('加载数据源列表失败', e);
    }
  };

  useEffect(() => {
    fetchDatasources();
  }, []);

  // 基础交互状态
  const [activeTab, setActiveTab] = useState<'RDS' | 'FILE'>('FILE'); // 默认切换到上传的数据
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [addPanelKey, setAddPanelKey] = useState(0);

  // 二级子表下钻详情状态
  const [selectedSubTableName, setSelectedSubTableName] = useState<string | null>(null);
  const [customColumnDescriptions, setCustomColumnDescriptions] = useState<Record<string, string>>({});

  // 每次选中项改变，重置下钻状态
  useEffect(() => {
    setSelectedSubTableName(null);
  }, [selectedItemId]);


  // Toast 轻量气泡通知
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  useEffect(() => {
    if (toastMessage) {
      const timer = setTimeout(() => {
        setToastMessage(null);
      }, 2000);
      return () => clearTimeout(timer);
    }
  }, [toastMessage]);

  // 右侧是否处于“添加数据”状态
  const [isAddingData, setIsAddingData] = useState(false);

  // 真实刷新动作
  const handleRefresh = async () => {
    setIsLoading(true);
    await fetchDatasources();
    setIsLoading(false);
  };

  // 返回数据中心主页
  const handleBackToCenter = () => {
    setSelectedItemId(null);
    setIsAddingData(false);
    navigate('/data');
  };

  // 模糊检索过滤
  const filteredDataSources = useMemo(() => {
    return datasources.filter(ds => 
      ds.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      ds.database.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [datasources, searchQuery]);

  const filteredFiles = useMemo(() => {
    return files.filter(f => 
      f.name.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [files, searchQuery]);

  // 获取当前选中的项
  const selectedItem = useMemo(() => {
    if (!selectedItemId) return null;
    if (activeTab === 'RDS') {
      return datasources.find(ds => ds.id === selectedItemId) || null;
    } else {
      return files.find(f => f.id === selectedItemId) || null;
    }
  }, [selectedItemId, activeTab, datasources, files]);

  // 监听路由参数变化，同步选中状态（支持浏览器前进后退、刷新不丢失状态）
  useEffect(() => {
    if (database) {
      const matched = datasources.find(ds => ds.database === database);
      if (matched) {
        setSelectedItemId(matched.id);
        setActiveTab('RDS');
        setIsAddingData(false);
      }
    } else {
      if (activeTab === 'RDS' && selectedItemId) {
        setSelectedItemId(null);
      }
    }
  }, [database, datasources, activeTab, selectedItemId]);

  // 删除操作 (对接后端物理删除 API)
  const handleDeleteItem = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation();
    if (activeTab === 'RDS') {
      if (!confirm('确定要删除该数据源连接吗？这将从系统中彻底移除它。')) return;
      try {
        const response = await fetch(`/api/datasource/${id}`, {
          method: 'DELETE'
        });
        if (response.ok) {
          const result = await response.json();
          if (result.code === '0') {
            setToastMessage('数据源删除成功');
            // 清理本地已导入表列表缓存
            localStorage.removeItem(`ds_imported_${id}`);
            if (selectedItemId === id) {
              setSelectedItemId(null);
            }
            await fetchDatasources();
          } else {
            setToastMessage(`删除失败: ${result.message}`);
          }
        }
      } catch (err: any) {
        setToastMessage(`删除失败: ${err.message || '网络异常'}`);
      }
    } else {
      setFiles(prev => prev.filter(f => f.id !== id));
      if (selectedItemId === id) {
        setSelectedItemId(null);
      }
    }
  };

  // 处理添加确认
  const handleConfirmAdd = async (data: {
    type: 'LOCAL_UPLOAD' | 'OSS_FILE' | 'RDS_DB' | 'POLAR_DB' | 'ANALYTIC_DB' | 'DMS_INSTANCE';
    fileName?: string;
    tempId?: string;
    selectedTables?: string[];
    dbForm?: {
      name: string;
      host: string;
      port: string;
      database: string;
      username: string;
      password?: string;
      importedTables?: string[];
    };
  }) => {
    if (data.type === 'LOCAL_UPLOAD') {
      if (!data.fileName) return;
      const newFile: UploadedFile = {
        id: `file-${Date.now()}`,
        name: data.fileName,
        size: '56.4 KB',
        type: data.fileName.endsWith('.xlsx') ? 'Excel' : 'CSV',
        createdAt: new Date().toISOString().replace('T', ' ').substring(0, 19),
        status: 'parsed'
      };
      setFiles(prev => [newFile, ...prev]);
      setActiveTab('FILE');
      setSelectedItemId(newFile.id);
      setIsAddingData(false);
    } else if (data.type === 'RDS_DB' || data.type === 'POLAR_DB' || data.type === 'ANALYTIC_DB') {
      // 如果存在静默创建保存好的 tempId
      if (data.tempId) {
        // 保存选中的表关系到本地 LocalStorage 供后续使用
        if (data.selectedTables) {
          localStorage.setItem(`ds_imported_${data.tempId}`, JSON.stringify(data.selectedTables));
        }
        // 重新拉取后端最新数据源列表
        await fetchDatasources();
        // 切换 Tab 以让用户在左侧看到新加的数据库连接
        setActiveTab('RDS');
        setToastMessage('数据库连接成功，可以继续添加');
        // 强行改变 key，重置 AddDataPanel 面板的输入和测试连接状态
        setAddPanelKey(prev => prev + 1);
      } else {
        // 兜底防御：若无 tempId 直接提交（极少数由于测试连接异常），报错提示
        alert('请先进行“测试连接”并通过后再试');
      }
    } else {
      setToastMessage('已连接该数据源 (前端模拟)');
      setIsAddingData(false);
    }
  };

  return (
    <div className="relative m-2 h-[calc(100%-1rem)] w-[calc(100%-1rem)] rounded-lg border border-gray-200/80 shadow-sm bg-white overflow-hidden flex flex-col font-sans">
      
      <div className="flex h-full flex-1">
        {/* 左侧数据中心列表面板 (占比 30%，添加宽度限制) */}
        <div className="w-[30%] min-w-72.5 max-w-[38%] border-r border-gray-200/80 flex flex-col h-full p-3 pt-0 select-none relative bg-[#FAFAFA]">
          
          {/* 标题栏 */}
          <div className="z-1 flex h-12 items-center justify-between font-bold text-gray-800">
            <div className="flex min-w-0 items-center gap-3">
              <CollapsedSidebarMenuButton
                isSidebarCollapsed={isSidebarCollapsed}
                isSidebarVisible={isSidebarVisible}
                expandSidebar={expandSidebar}
              />
              <span className="truncate text-[14px]">数据中心</span>
            </div>
            <div className="flex items-center gap-1.5">
              <button 
                onClick={handleRefresh}
                title="刷新"
                className="flex items-center justify-center size-6 rounded-md hover:bg-gray-200/50 text-gray-500 hover:text-gray-700 transition-colors bg-transparent border-0"
              >
                <RefreshCw className={clsx("h-3.5 w-3.5 cursor-pointer", isLoading && "animate-spin text-[#2D336B]")} />
              </button>
              <button 
                onClick={() => { setIsAddingData(true); }}
                title="添加数据"
                className="flex items-center justify-center size-6 rounded-md hover:bg-gray-200/50 text-gray-500 hover:text-gray-700 transition-colors bg-transparent border-0"
              >
                <Plus className="h-4 w-4 cursor-pointer" />
              </button>
            </div>
          </div>

          {/* 搜索 */}
          <div className="relative mb-3">
            <input 
              className="flex border border-gray-200 bg-white pl-8 pr-3 py-1.5 text-sm rounded-md h-8 w-full placeholder:text-gray-400 focus:outline-none focus:ring-1 focus:ring-[#2D336B] transition-all"
              placeholder="搜索" 
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
            <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-gray-400 z-10 pointer-events-none" />
          </div>

          {/* 全局 Loading */}
          {isLoading && (
            <div className="absolute inset-0 bg-white/60 z-30 flex items-center justify-center">
              <div className="flex flex-col items-center gap-2 animate-in fade-in duration-150">
                <Loader2 className="h-6 w-6 animate-spin text-[#2D336B]" />
                <span className="text-sm text-gray-500 font-medium">正在读取元数据...</span>
              </div>
            </div>
          )}

          {/* Radix UI Tabs 选项卡重构 */}
          <Tabs.Root 
            value={activeTab} 
            onValueChange={(val) => { 
              setActiveTab(val as 'RDS' | 'FILE'); 
              setSelectedItemId(null); 
              setIsAddingData(false); 
            }}
            className="flex flex-col flex-1 overflow-hidden"
          >
            <Tabs.List className="inline-flex h-9 items-center justify-center rounded-lg bg-gray-100 p-0.5 text-gray-400 w-full mb-3 border-none">
              <Tabs.Trigger
                value="RDS"
                className={clsx(
                  "inline-flex items-center justify-center whitespace-nowrap rounded-md px-3 py-1 text-sm font-semibold transition-all cursor-pointer flex-1 border-none bg-transparent outline-none",
                  activeTab === 'RDS' ? "bg-white text-gray-800 shadow-sm" : "hover:text-gray-600"
                )}
              >
                数据源
              </Tabs.Trigger>
              <Tabs.Trigger
                value="FILE"
                className={clsx(
                  "inline-flex items-center justify-center whitespace-nowrap rounded-md px-3 py-1 text-sm font-semibold transition-all cursor-pointer flex-1 border-none bg-transparent outline-none",
                  activeTab === 'FILE' ? "bg-white text-gray-800 shadow-sm" : "hover:text-gray-600"
                )}
              >
                上传的数据
              </Tabs.Trigger>
            </Tabs.List>

            {/* 数据源 Tab 面板 */}
            <Tabs.Content value="RDS" className="flex-1 overflow-y-auto no-scrollbar pb-4 outline-none">
              {filteredDataSources.length === 0 ? (
                <div className="text-gray-400 text-sm text-center py-8">暂无数据</div>
              ) : (
                <div className="flex flex-col">
                  {filteredDataSources.map(ds => (
                    <Tooltip.Provider key={ds.id} delayDuration={650}>
                      <Tooltip.Root>
                        <Tooltip.Trigger asChild>
                          <div 
                            onClick={() => { 
                              setSelectedItemId(ds.id); 
                              setIsAddingData(false); 
                              navigate(`/data/${ds.database}`);
                            }}
                            className={clsx(
                              "group relative flex items-center justify-between px-3 py-2 text-sm font-medium rounded transition-colors cursor-pointer",
                              (selectedItemId === ds.id && !isAddingData)
                                ? "bg-gray-200/60 text-gray-900 font-semibold" 
                                : "text-gray-600 hover:bg-gray-100/60 hover:text-gray-900"
                            )}
                          >
                            <div className="flex items-center gap-2 overflow-hidden flex-1 min-w-0 select-none">
                              {(() => {
                                const logo = getDatabaseLogo(ds.type);
                                if (logo) {
                                  return <img src={logo} className="w-3.5 h-3.5 object-contain flex-none" alt={ds.type} />;
                                }
                                return <Database className="w-3.5 h-3.5 text-gray-400 flex-none" />;
                              })()}
                              <span className="truncate group-hover:underline">{ds.name}</span>
                            </div>
                            <button 
                              onClick={(e) => handleDeleteItem(e, ds.id)}
                              className="opacity-0 group-hover:opacity-100 hover:text-red-500 text-gray-400 p-0.5 rounded transition-opacity border-0 bg-transparent"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </Tooltip.Trigger>
                        <Tooltip.Portal>
                          <Tooltip.Content 
                            className="z-50 border border-gray-100 bg-white px-2 py-1 text-xs text-gray-850 rounded shadow-md pointer-events-none select-none font-normal animate-in fade-in duration-100"
                            side="right"
                            sideOffset={10}
                          >
                            {ds.name}
                          </Tooltip.Content>
                        </Tooltip.Portal>
                      </Tooltip.Root>
                    </Tooltip.Provider>
                  ))}
                </div>
              )}
            </Tabs.Content>

            {/* 上传的数据 Tab 面板 */}
            <Tabs.Content value="FILE" className="flex-1 overflow-y-auto no-scrollbar pb-4 outline-none">
              {filteredFiles.length === 0 ? (
                <div className="text-gray-400 text-sm text-center py-8">暂无数据</div>
              ) : (
                <div className="flex flex-col">
                  {filteredFiles.map(f => (
                    <Tooltip.Provider key={f.id} delayDuration={650}>
                      <Tooltip.Root>
                        <Tooltip.Trigger asChild>
                          <div 
                            onClick={() => { setSelectedItemId(f.id); setIsAddingData(false); }}
                            className={clsx(
                              "group relative flex items-center justify-between px-3.5 py-1.5 text-sm rounded transition-colors cursor-pointer mb-px",
                              (selectedItemId === f.id && !isAddingData)
                                ? "bg-gray-200/50 text-gray-900 font-semibold" 
                                : "text-gray-600 hover:bg-gray-150/50 hover:text-gray-900"
                            )}
                          >
                            <div className="flex items-center gap-2 overflow-hidden flex-1 min-w-0">
                              {f.name.endsWith('.xlsx') || f.name.endsWith('.xls') ? (
                                <FileSpreadsheet className="w-3.5 h-3.5 text-green-600/90 flex-none animate-in fade-in duration-100" />
                              ) : (
                                <FileText className="w-3.5 h-3.5 text-[#3A78F2]/90 flex-none animate-in fade-in duration-100" />
                              )}
                              <span className="truncate leading-5 group-hover:underline">{f.name}</span>
                            </div>
                            <button 
                              onClick={(e) => handleDeleteItem(e, f.id)}
                              className="opacity-0 group-hover:opacity-100 hover:text-red-500 text-gray-400 p-0.5 rounded transition-opacity border-0 bg-transparent"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </Tooltip.Trigger>
                        <Tooltip.Portal>
                          <Tooltip.Content 
                            className="z-50 border border-gray-100 bg-white px-2 py-1 text-xs text-gray-850 rounded shadow-md pointer-events-none select-none font-normal animate-in fade-in duration-100"
                            side="right"
                            sideOffset={10}
                          >
                            {f.name}
                          </Tooltip.Content>
                        </Tooltip.Portal>
                      </Tooltip.Root>
                    </Tooltip.Provider>
                  ))}
                </div>
              )}
            </Tabs.Content>
          </Tabs.Root>

        </div>

        {/* 分隔线 */}
        <div className="w-px h-full bg-gray-200/80 flex-none" />

        {/* 右侧主工作区 (占比 70%) */}
        <div className="w-[70%] flex-1 h-full overflow-hidden flex flex-col bg-white">
          {isAddingData ? (
            <AddDataPanel 
              key={addPanelKey}
              onCancel={() => setIsAddingData(false)} 
              onConfirm={handleConfirmAdd} 
              onBackToCenter={handleBackToCenter}
            />
          ) : !selectedItem ? (
            /* 空白页布局 */
            <div className="h-full w-full flex flex-col items-center justify-center p-4 select-none">
              <div className="-mb-20 flex h-full flex-col items-center justify-center gap-4">
                <img 
                  className="w-106.5 h-auto opacity-95 transition-transform duration-500 hover:scale-[1.01]"
                  src="https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/datasource-home.svg" 
                  alt="请点击添加数据新增数据，从点击左侧已有数据源查看" 
                />
                <p className="text-gray-500 -mt-20 text-[13px] font-medium tracking-wide">
                  请点击添加数据新增数据，从点击左侧已有数据源查看
                </p>
                <div className="flex items-center justify-center gap-4 relative z-10">
                  <button 
                    onClick={() => { setIsAddingData(true); }}
                    className="inline-flex items-center justify-center gap-2 whitespace-nowrap text-sm font-semibold transition-all border border-gray-300 bg-white hover:bg-gray-50 text-gray-700 h-9 rounded-md px-4 shadow-sm active:scale-95 cursor-pointer"
                  >
                    添加数据
                  </button>
                </div>
              </div>
            </div>
          ) : 'host' in selectedItem ? (
            /* 选中数据库详情视图 */
            <DataSourceDetail 
              selectedItem={selectedItem} 
              onNotice={setToastMessage}
              onUpdateImportedTables={(dsId, updatedTables) => {
                setDatasources(prev => prev.map(ds => {
                  if (ds.id === dsId) {
                    return { ...ds, importedTables: updatedTables };
                  }
                  return ds;
                }));
              }}
              onBackToCenter={handleBackToCenter}
            />
          ) : selectedSubTableName ? (
            /* 选中二级子数据表详情视图 */
            <SubTableDetail 
              file={selectedItem}
              subTableName={selectedSubTableName}
              onBackToFile={() => setSelectedSubTableName(null)}
              setToastMessage={setToastMessage}
              customColumnDescriptions={customColumnDescriptions}
              setCustomColumnDescriptions={setCustomColumnDescriptions}
              onBackToCenter={handleBackToCenter}
            />
          ) : (
            /* 选中本地文件详情视图 */
            <FileDetail 
              file={selectedItem}
              onSelectSubTable={setSelectedSubTableName}
              onDelete={(id) => {
                setFiles(prev => prev.filter(f => f.id !== id));
                setSelectedItemId(null);
              }}
              setToastMessage={setToastMessage}
              onBackToCenter={handleBackToCenter}
            />
          )}
        </div>
      </div>

      {/* 全局 Toast 通知 */}
      {toastMessage && (
        <div className="fixed top-5 left-1/2 -translate-x-1/2 z-9999 flex items-center gap-2 bg-gray-900/90 backdrop-blur-xs text-white px-4 py-2 rounded-lg text-sm font-medium shadow-lg animate-in fade-in slide-in-from-top-4 duration-200 select-none">
          <CheckCircle2 className="h-4 w-4 text-green-400 animate-in fade-in" />
          <span>{toastMessage}</span>
        </div>
      )}
    </div>
  );
};
