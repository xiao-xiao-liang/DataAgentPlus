import React, { useState, useMemo } from 'react';
import { 
  ArrowLeft, 
  FileSpreadsheet, 
  Cloud, 
  Database, 
  Server, 
  BarChart3, 
  Layers, 
  Upload, 
  Loader2, 
  CheckCircle2, 
  ChevronsUpDown 
} from 'lucide-react';
import clsx from 'clsx';

interface AddDataPanelProps {
  onCancel: () => void;
  onConfirm: (data: {
    type: 'LOCAL_UPLOAD' | 'OSS_FILE' | 'RDS_DB' | 'POLAR_DB' | 'ANALYTIC_DB' | 'DMS_INSTANCE';
    fileName?: string;
    dbForm?: {
      name: string;
      host: string;
      port: string;
      database: string;
      username: string;
      password?: string;
    };
  }) => void;
}

export const AddDataPanel: React.FC<AddDataPanelProps> = ({ onCancel, onConfirm }) => {
  const [selectedAddType, setSelectedAddType] = useState<
    'LOCAL_UPLOAD' | 'OSS_FILE' | 'RDS_DB' | 'POLAR_DB' | 'ANALYTIC_DB' | 'DMS_INSTANCE'
  >('LOCAL_UPLOAD');

  // 文件上传状态模拟
  const [fileName, setFileName] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);

  // 数据库表单状态模拟
  const [dbForm, setDbForm] = useState({
    name: '',
    host: '',
    port: '3306',
    database: '',
    username: '',
    password: ''
  });
  const [testStatus, setTestStatus] = useState<'idle' | 'testing' | 'success' | 'failed'>('idle');

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

  // 模拟测试数据库连接
  const handleTestConnection = () => {
    if (!dbForm.name || !dbForm.host || !dbForm.database || !dbForm.username) {
      alert('请填写完整必选配置');
      return;
    }
    setTestStatus('testing');
    setTimeout(() => {
      setTestStatus('success');
    }, 1000);
  };

  // 判断确认按钮是否可用
  const isConfirmDisabled = useMemo(() => {
    if (selectedAddType === 'LOCAL_UPLOAD') {
      return !fileName || isUploading;
    }
    if (selectedAddType === 'RDS_DB' || selectedAddType === 'POLAR_DB' || selectedAddType === 'ANALYTIC_DB') {
      return !dbForm.name || !dbForm.host || !dbForm.database || !dbForm.username;
    }
    return false;
  }, [selectedAddType, fileName, isUploading, dbForm]);

  const handleConfirmSubmit = () => {
    onConfirm({
      type: selectedAddType,
      fileName,
      dbForm
    });
  };

  return (
    <div className="h-full w-full flex flex-col overflow-hidden animate-in fade-in slide-in-from-right-3 duration-300">
      
      {/* 面包屑导航 */}
      <div className="px-6 pt-5 pb-2 text-xs text-gray-400 flex items-center gap-1 select-none">
        <span>数据中心</span>
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

            {/* RDS数据库 */}
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
              <div className="size-8 rounded-lg flex items-center justify-center bg-orange-50 text-orange-600 flex-none">
                <Database className="w-4 h-4" />
              </div>
              <div className="flex flex-col text-left overflow-hidden">
                <span className="text-sm font-bold text-gray-700">RDS数据库</span>
                <span className="text-xs text-gray-400 mt-0.5 truncate font-normal">MySQL</span>
              </div>
            </div>

            {/* PolarDB数据库 */}
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
              <div className="size-8 rounded-lg flex items-center justify-center bg-cyan-50 text-cyan-600 flex-none">
                <Server className="w-4 h-4" />
              </div>
              <div className="flex flex-col text-left overflow-hidden">
                <span className="text-sm font-bold text-gray-700">PolarDB数据库</span>
                <span className="text-xs text-gray-400 mt-0.5 truncate font-normal">MySQL</span>
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

              {/* OSS Bucket - 模拟下拉输入 */}
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
        ) : (
          /* ===================== 数据库连接配置 ===================== */
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
                <label className="font-bold text-gray-600">默认Database库名 <span className="text-red-500">*</span></label>
                <input 
                  className="border border-gray-200 px-3 py-2 rounded-md focus:outline-none focus:border-[#3A78F2] h-9 w-full bg-white font-medium text-sm transition-all placeholder:text-gray-400" 
                  placeholder="例如: restaurant_db"
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
                  placeholder="rm-bp123456.mysql.rds.aliyuncs.com"
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
                "p-2.5 rounded-lg flex items-center gap-2 border text-sm animate-in slide-in-from-top-1 duration-200",
                testStatus === 'testing' && "bg-gray-50 border-gray-200 text-gray-600",
                testStatus === 'success' && "bg-green-50 border-green-200 text-green-700",
                testStatus === 'failed' && "bg-red-50 border-red-200 text-red-700"
              )}>
                {testStatus === 'testing' && (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin text-gray-400" />
                    <span>正在尝试建立测试 TCP 连接...</span>
                  </>
                )}
                {testStatus === 'success' && (
                  <>
                    <CheckCircle2 className="w-4 h-4 text-green-500" />
                    <span>连接测试成功！</span>
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
          onClick={onCancel}
          className="px-4 h-8 border border-gray-200 hover:bg-gray-50 text-gray-600 text-sm font-semibold rounded-md transition-colors cursor-pointer"
        >
          取消
        </button>
      </div>
    </div>
  );
};
