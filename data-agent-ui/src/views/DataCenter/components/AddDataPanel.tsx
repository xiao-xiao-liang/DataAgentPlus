import React, { useState, useMemo, useEffect } from 'react';
import { 
  ArrowLeft, 
  FileSpreadsheet, 
  Cloud, 
  BarChart3, 
  Layers, 
  Upload, 
  Loader2, 
  CheckCircle2, 
  AlertCircle,
  ChevronsUpDown,
  ShieldCheck
} from 'lucide-react';
import clsx from 'clsx';

// 引入数据库 LOGO 静态资产
import mysqlLogo from '../../../assets/logos/mysql.svg';
import postgresqlLogo from '../../../assets/logos/postgresql.svg';

type DatabaseEngineType = 'mysql' | 'postgresql';

const DATABASE_DEFAULT_PORTS: Record<DatabaseEngineType, number> = {
  mysql: 3306,
  postgresql: 5432
};

const getDefaultDatabasePort = (type: DatabaseEngineType) => DATABASE_DEFAULT_PORTS[type];
const getDefaultDatabasePortText = (type: DatabaseEngineType) => String(getDefaultDatabasePort(type));

interface AddDataPanelProps {
  onCancel: () => void;
  onConfirm: (data: {
    type: 'LOCAL_UPLOAD' | 'OSS_FILE' | 'RDS_DB' | 'POLAR_DB' | 'ANALYTIC_DB' | 'DMS_INSTANCE';
    fileName?: string;
    tempId?: string;
    dbForm?: {
      type: DatabaseEngineType;
      name: string;
      host: string;
      port: string;
      database: string;
      username: string;
      password?: string;
      description?: string;
      importedTables?: string[];
    };
  }) => void;
  onBackToCenter?: () => void;
}

export const AddDataPanel: React.FC<AddDataPanelProps> = ({ onCancel, onConfirm, onBackToCenter }) => {
  const [selectedAddType, setSelectedAddType] = useState<
    'LOCAL_UPLOAD' | 'OSS_FILE' | 'RDS_DB' | 'POLAR_DB' | 'ANALYTIC_DB' | 'DMS_INSTANCE'
  >('LOCAL_UPLOAD');

  // 文件上传状态
  const [fileName, setFileName] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);

  // 关系型数据库通用表单状态
  const [dbForm, setDbForm] = useState({
    name: '',
    type: 'mysql' as DatabaseEngineType,
    host: '',
    port: getDefaultDatabasePortText('mysql'),
    username: '',
    password: '',
    database: '',
    description: ''
  });

  const [dbConnected, setDbConnected] = useState(false);
  const [tempId, setTempId] = useState<string | null>(null); // 保存静默创建保存成功后的临时数据源 ID
  const [testStatus, setTestStatus] = useState<'idle' | 'testing' | 'success' | 'failed'>('idle');
  const [testMessage, setTestMessage] = useState<string>('');

  // 标识是否已最终点下确认，防 Unmount 卸载时误删
  const hasConfirmedRef = React.useRef(false);

  // 监听选项卡切换，自动修正端口与清空测试缓存
  useEffect(() => {
    if (selectedAddType === 'RDS_DB' || selectedAddType === 'ANALYTIC_DB') {
      setDbForm({
        name: '',
        type: 'mysql',
        host: '',
        port: getDefaultDatabasePortText('mysql'),
        username: '',
        password: '',
        database: '',
        description: ''
      });
      setDbConnected(false);
      setTempId(null);
      setTestStatus('idle');
      setTestMessage('');
    } else if (selectedAddType === 'POLAR_DB') {
      setDbForm({
        name: '',
        type: 'postgresql',
        host: '',
        port: getDefaultDatabasePortText('postgresql'),
        username: '',
        password: '',
        database: '',
        description: ''
      });
      setDbConnected(false);
      setTempId(null);
      setTestStatus('idle');
      setTestMessage('');
    }
  }, [selectedAddType]);

  // 组件卸载时，若已静默创建但用户未最终确认，自动发送 DELETE 清理临时脏数据
  useEffect(() => {
    return () => {
      if (tempId && !hasConfirmedRef.current) {
        fetch(`/api/datasource/${tempId}`, {
          method: 'DELETE'
        }).catch(err => console.error('组件卸载清理临时数据源失败:', err));
      }
    };
  }, [tempId]);

  // 模拟文件选择
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setFileName(file.name);
    setIsUploading(true);
    setUploadProgress(0);

    const interval = setInterval(() => {
      setUploadProgress(prev => {
        if (prev >= 100) {
          clearInterval(interval);
          setIsUploading(false);
          return 100;
        }
        return prev + 25;
      });
    }, 100);
  };

  // 真正对接后端：测试并静默创建数据源 (方案一)
  const handleTestConnection = async () => {
    if (!dbForm.name || !dbForm.host || !dbForm.port || !dbForm.database || !dbForm.username || !dbForm.password) {
      setTestStatus('failed');
      setTestMessage('请填齐必选配置（数据源别名、默认库名、Host、端口、账号、密码）');
      return;
    }
    setTestStatus('testing');
    setTestMessage('正在测试连接并保存数据源配置...');

    const reqBody = {
      name: dbForm.name,
      type: dbForm.type,
      host: dbForm.host,
      port: Number.parseInt(dbForm.port, 10) || getDefaultDatabasePort(dbForm.type),
      databaseName: dbForm.database || '', // 测试时数据库名若空后端可使用默认连接
      username: dbForm.username,
      password: dbForm.password,
      description: dbForm.description.trim()
    };

    try {
      // 1. 预检测连接连通性
      const response = await fetch('/api/datasource/test', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(reqBody)
      });

      if (response.ok) {
        const result = await response.json();
        // data 为 null 表示 ping 连通成功
        if (result.code === '0' && result.data === null) {
          // 2. 连通成功，立刻在后台静默保存数据源以获取临时 ID
          const createResponse = await fetch('/api/datasource', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json'
            },
            body: JSON.stringify(reqBody)
          });

          if (createResponse.ok) {
            const createResult = await createResponse.json();
            if (createResult.code === '0') {
              const newId = String(createResult.data);
              setTempId(newId);

              setTestStatus('success');
              setTestMessage('连接成功，数据源已保存。表范围请在“自定义Agent”的数据环境中按 Agent 配置。');
              setDbConnected(true);
              return;
            }
          }
        }

        // 失败回显
        setTestStatus('failed');
        setTestMessage(result.data || result.message || '连接失败，请检查数据库配置、安全组或账号密码是否正确');
      } else {
        setTestStatus('failed');
        setTestMessage(`接口响应异常: HTTP ${response.status}`);
      }
    } catch (e: any) {
      setTestStatus('failed');
      setTestMessage(`网络请求失败: ${e.message || '未知错误'}`);
    }
  };

  // 取消操作并进行静默清理
  const handleCancelAndCleanup = async () => {
    if (tempId) {
      try {
        await fetch(`/api/datasource/${tempId}`, {
          method: 'DELETE'
        });
      } catch (e) {
        console.error('取消删除临时数据源失败:', e);
      }
    }
    onCancel();
  };

  // 判断确认按钮是否可用
  const isConfirmDisabled = useMemo(() => {
    if (selectedAddType === 'LOCAL_UPLOAD') {
      return !fileName || isUploading;
    }
    if (selectedAddType === 'RDS_DB' || selectedAddType === 'POLAR_DB' || selectedAddType === 'ANALYTIC_DB') {
      if (!dbConnected) {
        // 未连通时不可直接点确认提交
        return true;
      }
      return !dbForm.database;
    }
    return false;
  }, [selectedAddType, fileName, isUploading, dbConnected, dbForm]);

  const handleConfirmSubmit = () => {
    // 标注为确认状态，避免 unmount 析构函数里的垃圾清除
    hasConfirmedRef.current = true;

    if (selectedAddType === 'RDS_DB' || selectedAddType === 'POLAR_DB' || selectedAddType === 'ANALYTIC_DB') {
      onConfirm({
        type: selectedAddType,
        tempId: tempId || undefined,
        dbForm: {
          type: dbForm.type,
          name: dbForm.name,
          host: dbForm.host,
          port: dbForm.port,
          database: dbForm.database,
          username: dbForm.username,
          password: dbForm.password,
          description: dbForm.description.trim()
        }
      });
      return;
    }
    onConfirm({
      type: selectedAddType,
      fileName,
      dbForm: undefined
    });
  };

  const databaseLabel = dbForm.type === 'postgresql' ? '默认 Schema/Database' : '默认 Database 库名';
  const databasePlaceholder = dbForm.type === 'postgresql' ? '例如: public 或 analytics' : '例如: restaurant_db';
  const hostPlaceholder = dbForm.type === 'postgresql'
    ? 'pg.example.com'
    : 'rm-bp123456.mysql.rds.aliyuncs.com';

  return (
    <div className="h-full w-full flex flex-col overflow-hidden animate-in fade-in slide-in-from-right-3 duration-300">
      
      {/* 面包屑导航 */}
      <div className="px-6 pt-5 pb-2 text-xs text-gray-400 flex items-center gap-1 select-none">
        <span 
          onClick={onBackToCenter}
          className="transition-colors hover:text-gray-800 cursor-pointer font-medium"
        >
          数据中心
        </span>
        <span>&gt;</span>
        <span className="text-gray-600 font-medium">添加数据</span>
      </div>

      {/* 头部标题 */}
      <div className="px-6 pb-4 border-b border-gray-100 flex items-center gap-2">
        <button 
          onClick={onCancel}
          className="p-1 hover:bg-gray-100 text-gray-600 rounded-md transition-colors cursor-pointer"
          title="返回"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>
        <h2 className="text-base font-bold text-gray-800 select-none">添加数据</h2>
      </div>

      {/* 中间表单区域 */}
      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        
        {/* 1. 添加方式卡片组 */}
        <div className="space-y-2">
          <div className="text-sm font-semibold text-gray-700 flex items-center gap-1 select-none">
            <span className="text-red-500 font-bold">*</span>
            <span>添加方式</span>
          </div>
          
          {/* 一行4列卡片 */}
          <div className="grid grid-cols-4 gap-3">
            {/* 本地上传 */}
            <div 
              onClick={() => setSelectedAddType('LOCAL_UPLOAD')}
              className={clsx(
                "flex items-center gap-2.5 p-3 rounded-lg border cursor-pointer transition-all",
                selectedAddType === 'LOCAL_UPLOAD' 
                  ? "bg-[#EBF1FF] border-[#3A78F2] shadow-xs" 
                  : "bg-white border-gray-200 hover:border-gray-300"
              )}
            >
              <input 
                type="radio" 
                checked={selectedAddType === 'LOCAL_UPLOAD'}
                onChange={() => setSelectedAddType('LOCAL_UPLOAD')}
                className="cursor-pointer accent-[#3A78F2] flex-none"
              />
              <div className="size-8 rounded-lg flex items-center justify-center bg-emerald-50 text-emerald-600 flex-none">
                <FileSpreadsheet className="w-4 h-4" />
              </div>
              <div className="flex flex-col text-left overflow-hidden">
                <span className="text-sm font-bold text-gray-700">本地上传</span>
                <span className="text-xs text-gray-400 mt-0.5 truncate font-normal">支持CSV/Excel文件格式</span>
              </div>
            </div>

            {/* OSS文件 */}
            <div 
              onClick={() => setSelectedAddType('OSS_FILE')}
              className={clsx(
                "flex items-center gap-2.5 p-3 rounded-lg border cursor-pointer transition-all",
                selectedAddType === 'OSS_FILE' 
                  ? "bg-[#EBF1FF] border-[#3A78F2] shadow-xs" 
                  : "bg-white border-gray-200 hover:border-gray-300"
              )}
            >
              <input 
                type="radio" 
                checked={selectedAddType === 'OSS_FILE'}
                onChange={() => setSelectedAddType('OSS_FILE')}
                className="cursor-pointer accent-[#3A78F2] flex-none"
              />
              <div className="size-8 rounded-lg flex items-center justify-center bg-sky-50 text-sky-600 flex-none">
                <Cloud className="w-4 h-4" />
              </div>
              <div className="flex flex-col text-left overflow-hidden">
                <span className="text-sm font-bold text-gray-700">OSS文件</span>
                <span className="text-xs text-gray-400 mt-0.5 truncate font-normal">支持CSV/Excel文件格式</span>
              </div>
            </div>

            {/* RDS数据库 (MySQL) */}
            <div 
              onClick={() => setSelectedAddType('RDS_DB')}
              className={clsx(
                "flex items-center gap-2.5 p-3 rounded-lg border cursor-pointer transition-all",
                selectedAddType === 'RDS_DB' 
                  ? "bg-[#EBF1FF] border-[#3A78F2] shadow-xs" 
                  : "bg-white border-gray-200 hover:border-gray-300"
              )}
            >
              <input 
                type="radio" 
                checked={selectedAddType === 'RDS_DB'}
                onChange={() => setSelectedAddType('RDS_DB')}
                className="cursor-pointer accent-[#3A78F2] flex-none"
              />
              <div className="size-8 rounded-lg flex items-center justify-center bg-gray-50 border border-gray-150/60 flex-none select-none">
                <img src={mysqlLogo} className="w-5 h-5 object-contain" alt="MySQL" />
              </div>
              <div className="flex flex-col text-left overflow-hidden">
                <span className="text-sm font-bold text-gray-700">MySQL</span>
                <span className="text-xs text-gray-400 mt-0.5 truncate font-normal">关系型数据库</span>
              </div>
            </div>

            {/* PolarDB数据库 (PostgreSQL) */}
            <div 
              onClick={() => setSelectedAddType('POLAR_DB')}
              className={clsx(
                "flex items-center gap-2.5 p-3 rounded-lg border cursor-pointer transition-all",
                selectedAddType === 'POLAR_DB' 
                  ? "bg-[#EBF1FF] border-[#3A78F2] shadow-xs" 
                  : "bg-white border-gray-200 hover:border-gray-300"
              )}
            >
              <input 
                type="radio" 
                checked={selectedAddType === 'POLAR_DB'}
                onChange={() => setSelectedAddType('POLAR_DB')}
                className="cursor-pointer accent-[#3A78F2] flex-none"
              />
              <div className="size-8 rounded-lg flex items-center justify-center bg-gray-50 border border-gray-150/60 flex-none select-none">
                <img src={postgresqlLogo} className="w-5 h-5 object-contain" alt="PostgreSQL" />
              </div>
              <div className="flex flex-col text-left overflow-hidden">
                <span className="text-sm font-bold text-gray-700">PostgreSQL</span>
                <span className="text-xs text-gray-400 mt-0.5 truncate font-normal">关系型数据库</span>
              </div>
            </div>
          </div>

          {/* 第二行 */}
          <div className="grid grid-cols-4 gap-3 pt-1">
            {/* AnalyticDB数据库 */}
            <div 
              onClick={() => setSelectedAddType('ANALYTIC_DB')}
              className={clsx(
                "flex items-center gap-2.5 p-3 rounded-lg border cursor-pointer transition-all",
                selectedAddType === 'ANALYTIC_DB' 
                  ? "bg-[#EBF1FF] border-[#3A78F2] shadow-xs" 
                  : "bg-white border-gray-200 hover:border-gray-300"
              )}
            >
              <input 
                type="radio" 
                checked={selectedAddType === 'ANALYTIC_DB'}
                onChange={() => setSelectedAddType('ANALYTIC_DB')}
                className="cursor-pointer accent-[#3A78F2] flex-none"
              />
              <div className="size-8 rounded-lg flex items-center justify-center bg-purple-50 text-purple-600 flex-none">
                <BarChart3 className="w-4 h-4" />
              </div>
              <div className="flex flex-col text-left overflow-hidden">
                <span className="text-sm font-bold text-gray-700">AnalyticDB数据库</span>
                <span className="text-xs text-gray-400 mt-0.5 truncate font-normal">MySQL</span>
              </div>
            </div>

            {/* DMS实例管理 */}
            <div 
              onClick={() => setSelectedAddType('DMS_INSTANCE')}
              className={clsx(
                "flex items-center gap-2.5 p-3 rounded-lg border cursor-pointer transition-all col-span-2",
                selectedAddType === 'DMS_INSTANCE' 
                  ? "bg-[#EBF1FF] border-[#3A78F2] shadow-xs" 
                  : "bg-white border-gray-200 hover:border-gray-300"
              )}
            >
              <input 
                type="radio" 
                checked={selectedAddType === 'DMS_INSTANCE'}
                onChange={() => setSelectedAddType('DMS_INSTANCE')}
                className="cursor-pointer accent-[#3A78F2] flex-none"
              />
              <div className="size-8 rounded-lg flex items-center justify-center bg-rose-50 text-rose-600 flex-none">
                <Layers className="w-4 h-4" />
              </div>
              <div className="flex flex-col text-left overflow-hidden">
                <span className="text-sm font-bold text-gray-700">DMS实例管理</span>
                <span className="text-xs text-gray-400 mt-0.5 truncate font-normal">
                  支持MaxCompute/Hologres/PostgreSQL/ClickHouse等40+数据...
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* 2. 下属表单区（动态条件渲染） */}
        {selectedAddType === 'LOCAL_UPLOAD' ? (
          /* ===================== 本地文件上传 ===================== */
          <div className="space-y-3">
            <div className="text-sm font-semibold text-gray-700 flex items-center gap-1 select-none">
              <span className="text-red-500 font-bold">*</span>
              <span>上传文件</span>
            </div>

            {/* 1:1 风格的大拖拽文件上传框 */}
            <div className="border-2 border-dashed border-gray-200 hover:border-[#3A78F2] rounded-lg p-10 text-center cursor-pointer transition-all bg-[#FAFAFA]/50 hover:bg-blue-50/10 relative">
              <input 
                type="file" 
                accept=".csv,.xlsx,.xls" 
                onChange={handleFileChange}
                className="absolute inset-0 opacity-0 cursor-pointer"
              />
              <div className="flex flex-col items-center justify-center gap-3">
                <Upload className="w-10 h-10 text-gray-400 stroke-[1.5]" />
                <span className="text-sm font-bold text-gray-600">点击或将文件拖拽至此上传</span>
                <span className="text-xs text-gray-400 font-normal">支持xlsx、xls、csv格式，文件最大200MB</span>
              </div>
            </div>

            {/* 选择后的上传进度反馈 */}
            {fileName && (
              <div className="bg-[#EBF1FF]/30 border border-blue-100 rounded-lg p-3.5 flex flex-col gap-2 animate-in fade-in duration-200">
                <div className="flex justify-between items-center text-sm">
                  <span className="font-semibold text-gray-700 truncate pr-4">{fileName}</span>
                  <span className="text-[#3A78F2] font-semibold">{uploadProgress}%</span>
                </div>
                <div className="w-full bg-gray-150 rounded-full h-1.5 overflow-hidden">
                  <div 
                    className="bg-[#3A78F2] h-1.5 rounded-full transition-all duration-300"
                    style={{ width: `${uploadProgress}%` }}
                  ></div>
                </div>
              </div>
            )}
          </div>
        ) : selectedAddType === 'OSS_FILE' ? (
          /* ===================== OSS 路径配置 ===================== */
          <div className="space-y-5 max-w-3xl border border-gray-100 rounded-lg p-6 bg-[#FAFAFA]/50 animate-in fade-in duration-200">
            <div className="text-sm font-bold text-gray-400 uppercase tracking-wide border-b border-gray-100 pb-2">OSS 配置</div>
            
            <div className="grid grid-cols-2 gap-4 text-sm">
              {/* 地域 - 置灰演示 */}
              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-gray-600 select-none">地域</label>
                <div className="relative flex items-center w-full">
                  <input 
                    type="text"
                    disabled
                    value="华东1（杭州）"
                    className="border border-gray-200 pl-3 pr-9 py-2 rounded-md h-9 w-full font-semibold text-sm bg-gray-100/60 text-gray-450 cursor-not-allowed" 
                  />
                  <ChevronsUpDown className="absolute right-2.5 w-4 h-4 text-gray-400 opacity-50 pointer-events-none" />
                </div>
              </div>

              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-gray-600 select-none">OSS Bucket <span className="text-red-500">*</span></label>
                <div className="relative flex items-center w-full">
                  <input 
                    type="text"
                    placeholder="请选择 Bucket"
                    className="border border-gray-200 pl-3 pr-9 py-2 rounded-md focus:outline-none focus:border-[#3A78F2] h-9 w-full bg-white font-medium text-sm transition-all placeholder:text-gray-400" 
                  />
                  <ChevronsUpDown className="absolute right-2.5 w-4 h-4 text-gray-400 opacity-60 pointer-events-none" />
                </div>
              </div>
            </div>

            {/* OSS 资源路径 */}
            <div className="flex flex-col gap-1.5 text-sm">
              <label className="font-bold text-gray-600 select-none">OSS 资源路径 <span className="text-red-500">*</span></label>
              <input 
                className="flex border border-gray-200 bg-white px-3 py-2 text-sm rounded-md h-9 w-full placeholder:text-gray-400 focus:outline-none focus:border-[#3A78F2] transition-all font-medium"
                placeholder="输入 OSS Bucket 路径，如 oss://my-bucket/dataset/sales.csv" 
              />
            </div>
          </div>
        ) : (selectedAddType === 'RDS_DB' || selectedAddType === 'POLAR_DB' || selectedAddType === 'ANALYTIC_DB') ? (
          /* ===================== 统一关系型数据库连接配置 (MySQL & PostgreSQL) ===================== */
          <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm animate-in fade-in duration-200">
            <div className="flex items-start justify-between gap-4 border-b border-slate-100 bg-gradient-to-r from-slate-50 to-white px-6 py-5">
              <div className="flex items-start gap-3">
                <div className="flex size-10 items-center justify-center rounded-lg border border-slate-200 bg-white shadow-xs">
                  {dbForm.type === 'postgresql' ? (
                    <img src={postgresqlLogo} className="h-6 w-6 object-contain" alt="PostgreSQL" />
                  ) : (
                    <img src={mysqlLogo} className="h-6 w-6 object-contain" alt="MySQL" />
                  )}
                </div>
                <div>
                  <div className="text-base font-bold text-slate-900">
                    {selectedAddType === 'RDS_DB' ? 'MySQL' : selectedAddType === 'POLAR_DB' ? 'PostgreSQL' : 'AnalyticDB'} 数据源配置
                  </div>
                  <div className="mt-1 text-sm text-slate-500">
                    保存可复用的数据源连接，Agent 绑定与表范围请在自定义 Agent 中配置。
                  </div>
                </div>
              </div>
              {dbConnected && (
                <div className="inline-flex h-8 shrink-0 items-center gap-1.5 rounded-md border border-emerald-200 bg-emerald-50 px-3 text-sm font-semibold text-emerald-700">
                  <ShieldCheck className="h-4 w-4" />
                  已保存
                </div>
              )}
            </div>

            <div className="space-y-5 p-6">
              <div className="grid grid-cols-2 gap-5 text-sm">
                <div className="flex flex-col gap-1.5">
                  <label className="font-bold text-slate-700">数据源别名 <span className="text-red-500">*</span></label>
                  <input 
                    disabled={dbConnected}
                    className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50/40 px-3 text-sm font-medium text-slate-800 transition-all placeholder:text-slate-400 focus:border-[#3A78F2] focus:bg-white focus:outline-none focus:ring-3 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400" 
                    placeholder="例如: Prod_Sales_DB"
                    value={dbForm.name}
                    onChange={(e) => setDbForm(prev => ({ ...prev, name: e.target.value }))}
                  />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="font-bold text-slate-700">{databaseLabel} <span className="text-red-500">*</span></label>
                  <input 
                    disabled={dbConnected}
                    className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50/40 px-3 text-sm font-medium text-slate-800 transition-all placeholder:text-slate-400 focus:border-[#3A78F2] focus:bg-white focus:outline-none focus:ring-3 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400" 
                    placeholder={databasePlaceholder}
                    value={dbForm.database}
                    onChange={(e) => setDbForm(prev => ({ ...prev, database: e.target.value }))}
                  />
                </div>
              </div>

            <div className="grid grid-cols-4 gap-5 text-sm">
              <div className="flex flex-col gap-1.5 col-span-3">
                <label className="font-bold text-slate-700">Host主机地址 <span className="text-red-500">*</span></label>
                <input 
                  disabled={dbConnected}
                  className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50/40 px-3 text-sm font-medium text-slate-800 transition-all placeholder:text-slate-400 focus:border-[#3A78F2] focus:bg-white focus:outline-none focus:ring-3 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400" 
                  placeholder={hostPlaceholder}
                  value={dbForm.host}
                  onChange={(e) => setDbForm(prev => ({ ...prev, host: e.target.value }))}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-slate-700">端口号 <span className="text-red-500">*</span></label>
                <input 
                  disabled={dbConnected}
                  className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50/40 px-3 text-sm font-medium text-slate-800 transition-all placeholder:text-slate-400 focus:border-[#3A78F2] focus:bg-white focus:outline-none focus:ring-3 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400" 
                  value={dbForm.port}
                  onChange={(e) => setDbForm(prev => ({ ...prev, port: e.target.value }))}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-5 text-sm">
              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-slate-700">数据库用户名 <span className="text-red-500">*</span></label>
                <input 
                  disabled={dbConnected}
                  className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50/40 px-3 text-sm font-medium text-slate-800 transition-all placeholder:text-slate-400 focus:border-[#3A78F2] focus:bg-white focus:outline-none focus:ring-3 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400" 
                  placeholder="username"
                  value={dbForm.username}
                  onChange={(e) => setDbForm(prev => ({ ...prev, username: e.target.value }))}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-slate-700">密码 <span className="text-red-500">*</span></label>
                <input 
                  disabled={dbConnected}
                  type="password"
                  className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50/40 px-3 text-sm font-medium text-slate-800 transition-all placeholder:text-slate-400 focus:border-[#3A78F2] focus:bg-white focus:outline-none focus:ring-3 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400" 
                  placeholder="password"
                  value={dbForm.password}
                  onChange={(e) => setDbForm(prev => ({ ...prev, password: e.target.value }))}
                />
              </div>
            </div>

            <div className="flex flex-col gap-1.5 text-sm">
              <label className="font-bold text-slate-700">数据源描述</label>
              <textarea
                disabled={dbConnected}
                className="min-h-18 w-full resize-y rounded-lg border border-slate-200 bg-slate-50/40 px-3 py-2 text-sm font-medium text-slate-800 transition-all placeholder:text-slate-400 focus:border-[#3A78F2] focus:bg-white focus:outline-none focus:ring-3 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400"
                placeholder="例如: 地铁运行轨迹 PostgreSQL 数据源"
                value={dbForm.description}
                onChange={(e) => setDbForm(prev => ({ ...prev, description: e.target.value.slice(0, 200) }))}
              />
            </div>

            {/* 测试连接按钮 */}
            {!dbConnected && (
              <div className="pt-1 flex items-center gap-3">
                <button 
                  type="button"
                  onClick={handleTestConnection}
                  disabled={testStatus === 'testing'}
                  className="inline-flex h-9 items-center gap-2 rounded-lg border border-blue-200 bg-blue-50 px-4 text-sm font-semibold text-[#3A78F2] transition-all hover:bg-blue-100 cursor-pointer disabled:opacity-50"
                >
                  {testStatus === 'testing' && <Loader2 className="h-4 w-4 animate-spin" />}
                  {testStatus === 'testing' ? '正在建立连接...' : '测试连接'}
                </button>
              </div>
            )}

            {/* 连接反馈提示 */}
            {testStatus !== 'idle' && (
              <div className={clsx(
                "p-2.5 rounded-lg flex items-start gap-2 border text-sm leading-5 animate-in slide-in-from-top-1 duration-200",
                testStatus === 'testing' && "bg-gray-50 border-gray-200 text-gray-600",
                testStatus === 'success' && "bg-green-50 border-green-200 text-green-700",
                testStatus === 'failed' && "bg-red-50 border-red-200 text-red-700"
              )}>
                {testStatus === 'testing' && (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin text-gray-400 flex-none mt-0.5" />
                    <span className="min-w-0 break-words">{testMessage}</span>
                  </>
                )}
                {testStatus === 'success' && (
                  <>
                    <CheckCircle2 className="w-4 h-4 text-green-500 flex-none mt-0.5" />
                    <span className="min-w-0 break-words">{testMessage}</span>
                  </>
                )}
                {testStatus === 'failed' && testMessage && (
                  <>
                    <AlertCircle className="w-4 h-4 text-red-500 flex-none mt-0.5" />
                    <span className="min-w-0 break-words">{testMessage}</span>
                  </>
                )}
              </div>
            )}
            </div>
          </div>
        ) : (
          /* ===================== 数据库连接配置 (其他) ===================== */
          <div className="space-y-5 max-w-3xl border border-gray-100 rounded-lg p-6 bg-[#FAFAFA]/50 animate-in fade-in duration-200">
            <div className="text-sm font-bold text-gray-400 uppercase tracking-wide border-b border-gray-100 pb-2">数据源配置</div>
            
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-gray-600">数据源别名 <span className="text-red-500">*</span></label>
                <input 
                  className="border border-gray-200 px-3 py-2 rounded-md focus:outline-none focus:border-[#3A78F2] h-9 w-full bg-white font-medium text-sm transition-all placeholder:text-gray-400" 
                  placeholder="例如: Prod_Sales_MySQL"
                  value={dbForm.name}
                  onChange={(e) => setDbForm(prev => ({ ...prev, name: e.target.value }))}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-gray-600">{databaseLabel} <span className="text-red-500">*</span></label>
                <input 
                  className="border border-gray-200 px-3 py-2 rounded-md focus:outline-none focus:border-[#3A78F2] h-9 w-full bg-white font-medium text-sm transition-all placeholder:text-gray-400" 
                  placeholder={databasePlaceholder}
                  value={dbForm.database}
                  onChange={(e) => setDbForm(prev => ({ ...prev, database: e.target.value }))}
                />
              </div>
            </div>

            <div className="grid grid-cols-4 gap-4 text-sm">
              <div className="flex flex-col gap-1.5 col-span-3">
                <label className="font-bold text-gray-600">Host主机地址 <span className="text-red-500">*</span></label>
                <input 
                  className="border border-gray-200 px-3 py-2 rounded-md focus:outline-none focus:border-[#3A78F2] h-9 w-full bg-white font-medium text-sm transition-all placeholder:text-gray-400" 
                  placeholder={hostPlaceholder}
                  value={dbForm.host}
                  onChange={(e) => setDbForm(prev => ({ ...prev, host: e.target.value }))}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-gray-600">端口号 <span className="text-red-500">*</span></label>
                <input 
                  className="border border-gray-200 px-3 py-2 rounded-md focus:outline-none focus:border-[#3A78F2] h-9 w-full bg-white font-medium text-sm transition-all placeholder:text-gray-400" 
                  value={dbForm.port}
                  onChange={(e) => setDbForm(prev => ({ ...prev, port: e.target.value }))}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4 text-sm">
              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-gray-600">数据库用户名 <span className="text-red-500">*</span></label>
                <input 
                  className="border border-gray-200 px-3 py-2 rounded-md focus:outline-none focus:border-[#3A78F2] h-9 w-full bg-white font-medium text-sm transition-all placeholder:text-gray-400" 
                  placeholder="username"
                  value={dbForm.username}
                  onChange={(e) => setDbForm(prev => ({ ...prev, username: e.target.value }))}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="font-bold text-gray-600">密码</label>
                <input 
                  type="password"
                  className="border border-gray-200 px-3 py-2 rounded-md focus:outline-none focus:border-[#3A78F2] h-9 w-full bg-white font-medium text-sm transition-all placeholder:text-gray-400" 
                  placeholder="password"
                  value={dbForm.password}
                  onChange={(e) => setDbForm(prev => ({ ...prev, password: e.target.value }))}
                />
              </div>
            </div>

            {/* 测试连接模拟显示 */}
            {testStatus !== 'idle' && (
              <div className={clsx(
                "p-2.5 rounded-lg flex items-start gap-2 border text-sm leading-5 animate-in slide-in-from-top-1 duration-200",
                testStatus === 'testing' && "bg-gray-50 border-gray-200 text-gray-600",
                testStatus === 'success' && "bg-green-50 border-green-200 text-green-700",
                testStatus === 'failed' && "bg-red-50 border-red-200 text-red-700"
              )}>
                {testStatus === 'testing' && (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin text-gray-400 flex-none mt-0.5" />
                    <span className="min-w-0 break-words">{testMessage}</span>
                  </>
                )}
                {testStatus === 'success' && (
                  <>
                    <CheckCircle2 className="w-4 h-4 text-green-500 flex-none mt-0.5" />
                    <span className="min-w-0 break-words">{testMessage}</span>
                  </>
                )}
                {testStatus === 'failed' && testMessage && (
                  <>
                    <AlertCircle className="w-4 h-4 text-red-500 flex-none mt-0.5" />
                    <span className="min-w-0 break-words">{testMessage}</span>
                  </>
                )}
              </div>
            )}

            <div className="pt-1">
              <button 
                onClick={handleTestConnection}
                disabled={testStatus === 'testing'}
                className="px-3.5 h-8 border border-blue-200 bg-blue-50/50 hover:bg-blue-50 text-[#3A78F2] text-sm font-semibold rounded-md transition-all cursor-pointer disabled:opacity-50"
              >
                测试连接
              </button>
            </div>
          </div>
        )}
      </div>

      {/* 底部按钮栏 */}
      <div className="px-6 py-4 border-t border-gray-100 flex items-center gap-2 flex-none">
        <button 
          onClick={handleConfirmSubmit}
          disabled={isConfirmDisabled}
          className={clsx(
            "px-4 h-8 text-sm font-semibold rounded-md shadow-xs transition-all cursor-pointer",
            isConfirmDisabled
              ? "bg-gray-100 text-gray-400 cursor-not-allowed"
              : "bg-[#2D336B] hover:bg-[#1E2248] text-white"
          )}
        >
          确认
        </button>
        <button 
          onClick={handleCancelAndCleanup}
          className="px-4 h-8 border border-gray-200 hover:bg-gray-50 text-gray-600 text-sm font-semibold rounded-md transition-colors cursor-pointer"
        >
          取消
        </button>
      </div>
    </div>
  );
};
