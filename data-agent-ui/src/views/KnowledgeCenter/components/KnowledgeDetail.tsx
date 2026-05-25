import React, { useState, useMemo, useRef } from 'react';
import { 
  ArrowLeft, 
  UploadCloud, 
  FileText, 
  Loader2, 
  Trash2, 
  CheckCircle2, 
  Search, 
  AlertCircle,
  FileSpreadsheet,
  Info
} from 'lucide-react';
import clsx from 'clsx';
import type { KnowledgeBase, KnowledgeFile } from '../types';

interface KnowledgeDetailProps {
  kb: KnowledgeBase;
  onBack: () => void;
  onUpdateKB: (updatedKB: KnowledgeBase) => void;
  showToast: (msg: string) => void;
}

export const KnowledgeDetail: React.FC<KnowledgeDetailProps> = ({
  kb,
  onBack,
  onUpdateKB,
  showToast,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isDragOver, setIsDragOver] = useState(false);

  // 模糊检索过滤当前知识库的文件
  const filteredFiles = useMemo(() => {
    return kb.files.filter(file => 
      file.name.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [kb.files, searchQuery]);

  // 生成格式化大小
  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  // 生成当前时间字符串 (yyyy-MM-dd HH:mm:ss)
  const getFormattedNow = (): string => {
    const d = new Date();
    const pad = (n: number) => n.toString().padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  };

  // 执行模拟上传
  const startMockUpload = (fileName: string, fileSizeStr: string) => {
    const newFileId = `file-mock-${Date.now()}`;
    const newFile: KnowledgeFile = {
      id: newFileId,
      name: fileName,
      size: fileSizeStr,
      status: 'uploading',
      progress: 0,
      uploadedAt: getFormattedNow()
    };

    // 1. 将新文件添加到知识库中并通知父组件
    const updatedKB = {
      ...kb,
      files: [newFile, ...kb.files],
      fileCount: kb.files.length + 1,
      updatedAt: getFormattedNow()
    };
    onUpdateKB(updatedKB);

    // 2. 模拟上传进度定时器 (每 80ms 增加 10% 左右)
    let currentProgress = 0;
    const uploadInterval = setInterval(() => {
      currentProgress += Math.floor(Math.random() * 15) + 5;
      if (currentProgress >= 100) {
        currentProgress = 100;
        clearInterval(uploadInterval);
        
        // 进入解析阶段 (parsing)
        onUpdateKB({
          ...updatedKB,
          files: updatedKB.files.map(f => 
            f.id === newFileId 
              ? { ...f, status: 'parsing', progress: 100 } 
              : f
          )
        });

        // 模拟解析耗时 1.2 秒
        setTimeout(() => {
          onUpdateKB({
            ...updatedKB,
            files: updatedKB.files.map(f => 
              f.id === newFileId 
                ? { ...f, status: 'success' } 
                : f
            )
          });
          showToast(`文件 "${fileName}" 上传并解析成功！`);
        }, 1200);

      } else {
        // 更新上传进度
        onUpdateKB({
          ...updatedKB,
          files: updatedKB.files.map(f => 
            f.id === newFileId 
              ? { ...f, progress: currentProgress } 
              : f
          )
        });
      }
    }, 80);
  };

  // 处理文件选择
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = e.target.files;
    if (!selectedFiles || selectedFiles.length === 0) return;
    
    // 校验限额
    if (kb.files.length >= 10) {
      showToast("上传失败：免费版/个人版知识库至多添加10个文件，请清理已有文件。");
      return;
    }

    const file = selectedFiles[0];
    const sizeStr = formatFileSize(file.size);
    startMockUpload(file.name, sizeStr);
    
    // 重置 input
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  // 拖拽相关事件
  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(true);
  };

  const handleDragLeave = () => {
    setIsDragOver(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    
    // 校验限额
    if (kb.files.length >= 10) {
      showToast("上传失败：免费版/个人版知识库至多添加10个文件，请清理已有文件。");
      return;
    }

    const droppedFiles = e.dataTransfer.files;
    if (droppedFiles && droppedFiles.length > 0) {
      const file = droppedFiles[0];
      const sizeStr = formatFileSize(file.size);
      startMockUpload(file.name, sizeStr);
    }
  };

  // 删除文件
  const handleDeleteFile = (fileId: string, fileName: string) => {
    if (window.confirm(`确认从知识库中删除文件 "${fileName}" 吗？`)) {
      const newFiles = kb.files.filter(f => f.id !== fileId);
      const updatedKB = {
        ...kb,
        files: newFiles,
        fileCount: newFiles.length,
        updatedAt: getFormattedNow()
      };
      onUpdateKB(updatedKB);
      showToast(`已删除文件 "${fileName}"`);
    }
  };

  const isLimitReached = kb.files.length >= 10;

  return (
    <div className="flex h-full w-full flex-col overflow-hidden select-none font-sans animate-in fade-in duration-200">
      
      {/* 顶栏面包屑与返回 */}
      <div className="flex h-[3.75rem] items-center border-b border-gray-100 px-6 py-4 flex-none">
        <button 
          onClick={onBack}
          className="mr-2 cursor-pointer p-1 rounded-lg hover:bg-gray-100 text-gray-700 transition-colors border-none bg-transparent"
        >
          <ArrowLeft className="h-5 w-5" />
        </button>
        <div className="flex items-center gap-1.5 text-sm font-semibold text-gray-500">
          <span className="hover:text-gray-800 cursor-pointer" onClick={onBack}>知识中心</span>
          <span className="text-gray-400 font-normal">&gt;</span>
          <span className="text-gray-800 font-bold">{kb.name}</span>
        </div>
      </div>

      {/* 警告横幅信息提示 */}
      <div className="px-6 pt-4 flex-none">
        <div className="flex w-fit items-center gap-2 rounded-[10px] bg-[#EEF3FC] px-3 h-9">
          <Info className="text-[#2F54EB] h-3.5 w-3.5 flex-shrink-0" />
          <span className="text-[#0A0A0B] text-sm leading-[21px] font-normal">
            Data Agent免费版和个人版每个账号支持创建1个知识库，至多添加10个文件；DataAgent企业版默认支持至多10个知识库，每个库50个文件。
          </span>
        </div>
      </div>

      <div className="flex flex-1 overflow-hidden">
        
        {/* 左侧：知识库基本属性 (占比 30%，限制宽度) */}
        <div className="w-[30%] min-w-64 max-w-[35%] border-r border-gray-100 bg-[#FAFAFA] p-5 flex flex-col justify-between overflow-y-auto no-scrollbar">
          <div className="space-y-4">
            <div>
              <h2 className="text-base font-bold text-gray-800 break-words">{kb.name}</h2>
              <div className="text-[10px] text-gray-400 font-mono mt-1">创建者: {kb.creator}</div>
            </div>
            
            <div className="space-y-2">
              <span className="text-xs font-bold text-gray-500">知识库描述</span>
              <p className="text-xs text-gray-600 leading-relaxed bg-white border border-gray-150 rounded-lg p-3 break-words font-medium">
                {kb.description || '暂无描述信息'}
              </p>
            </div>

            <div className="space-y-2">
              <div className="flex justify-between items-center text-xs">
                <span className="font-bold text-gray-500">存储容量限制</span>
                <span className="font-bold font-mono text-gray-700">{kb.files.length} / 10 个文件</span>
              </div>
              
              {/* 限额指示进度条 */}
              <div className="w-full bg-gray-200/80 rounded-full h-1.5 overflow-hidden">
                <div 
                  className={clsx(
                    "h-full rounded-full transition-all duration-500",
                    isLimitReached ? "bg-red-500" : kb.files.length >= 8 ? "bg-amber-500" : "bg-[#2D336B]"
                  )}
                  style={{ width: `${Math.min((kb.files.length / 10) * 100, 100)}%` }}
                />
              </div>
              {isLimitReached && (
                <div className="flex items-start gap-1 text-[10px] text-red-500 font-medium leading-4 pt-1 animate-pulse">
                  <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                  <span>已达到免费版 10 个文件上限，无法继续上传文件，如需扩容请联系系统管理员升级企业版。</span>
                </div>
              )}
            </div>
          </div>

          {/* 底栏服务信息 */}
          <div className="text-[10px] text-gray-400 font-mono pt-4 border-t border-gray-200/60">
            <div>更新时间：{kb.updatedAt}</div>
            <div className="mt-1">状态：已就绪 (在线解析中)</div>
          </div>
        </div>

        {/* 右侧：文档管理主列表与上传区 */}
        <div className="flex-1 p-6 bg-white flex flex-col h-full overflow-hidden">
          
          {/* 上传区域 */}
          <div className="flex-none mb-5">
            <input 
              type="file" 
              ref={fileInputRef}
              onChange={handleFileChange}
              className="hidden"
              accept=".txt,.pdf,.doc,.docx,.xls,.xlsx,.csv,.md"
              disabled={isLimitReached}
            />
            <div 
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => !isLimitReached && fileInputRef.current?.click()}
              className={clsx(
                "border-2 border-dashed rounded-xl p-6 flex flex-col items-center justify-center text-center cursor-pointer transition-all duration-200 select-none",
                isLimitReached 
                  ? "border-gray-200 bg-gray-50/50 cursor-not-allowed opacity-60" 
                  : isDragOver
                    ? "border-[#2D336B] bg-indigo-50/20"
                    : "border-gray-200 hover:border-[#2D336B] bg-[#FAFAFA]/60 hover:bg-indigo-50/5"
              )}
            >
              <UploadCloud className={clsx(
                "h-8 w-8 mb-2 transition-transform duration-300",
                isLimitReached ? "text-gray-300" : "text-gray-400 group-hover:scale-105"
              )} />
              <div className="text-xs font-bold text-gray-700">
                {isLimitReached ? '已达到文件限额限制' : '点击或拖拽文件到此区域上传'}
              </div>
              <div className="text-[10px] text-gray-400 mt-1.5 font-medium leading-4">
                支持上传 TXT, PDF, Word, Excel, CSV, Markdown 等文件格式（最大可支持 10MB）
              </div>
            </div>
          </div>

          {/* 搜索过滤与文件列表 */}
          <div className="flex-1 flex flex-col overflow-hidden">
            <div className="flex items-center justify-between mb-4 flex-none">
              <h3 className="text-sm font-bold text-gray-800">
                文件列表 ({kb.files.length})
              </h3>
              
              {/* 文件搜索 */}
              <div className="relative w-64">
                <input 
                  type="text" 
                  placeholder="搜索库内文件..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="flex w-full border border-gray-200 bg-white pl-8 pr-3 py-1.5 text-xs rounded-lg focus:outline-none focus:border-gray-300 text-gray-700 placeholder-gray-400 h-8 font-medium transition-all"
                />
                <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-gray-400 z-10 pointer-events-none" />
              </div>
            </div>

            {/* 文件滚动列表 */}
            <div className="flex-1 overflow-y-auto no-scrollbar border border-gray-100 rounded-xl bg-white">
              {filteredFiles.length === 0 ? (
                <div className="h-full w-full flex flex-col items-center justify-center p-8 text-gray-400 font-semibold text-xs py-16">
                  暂无匹配文档
                </div>
              ) : (
                <div className="divide-y divide-gray-100">
                  {filteredFiles.map((file) => {
                    const isExcel = file.name.endsWith('.xlsx') || file.name.endsWith('.xls') || file.name.endsWith('.csv');
                    return (
                      <div 
                        key={file.id} 
                        className="group flex items-center justify-between p-3.5 px-4 hover:bg-gray-50/50 transition-colors"
                      >
                        {/* 左侧：文件图标与基本信息 */}
                        <div className="flex items-center gap-3 overflow-hidden flex-1 mr-4">
                          {isExcel ? (
                            <div className="p-1.5 rounded bg-emerald-50 text-emerald-600 shrink-0">
                              <FileSpreadsheet className="size-4" />
                            </div>
                          ) : (
                            <div className="p-1.5 rounded bg-blue-50 text-blue-600 shrink-0">
                              <FileText className="size-4" />
                            </div>
                          )}
                          <div className="min-w-0 flex-1">
                            <div className="truncate text-xs font-bold text-gray-700" title={file.name}>
                              {file.name}
                            </div>
                            <div className="flex items-center gap-2 mt-1 text-[10px] text-gray-400 font-mono">
                              <span>{file.size}</span>
                              <span>•</span>
                              <span>{file.uploadedAt}</span>
                            </div>
                          </div>
                        </div>

                        {/* 右侧：状态展示与操作 */}
                        <div className="flex items-center gap-4 shrink-0 select-none">
                          
                          {/* 状态指示 */}
                          {file.status === 'uploading' && (
                            <div className="flex flex-col items-end gap-1">
                              <span className="text-[10px] font-bold text-[#2D336B] font-mono">
                                上传中 {file.progress || 0}%
                              </span>
                              <div className="w-20 bg-gray-200 rounded-full h-1 overflow-hidden">
                                <div 
                                  className="bg-[#2D336B] h-full transition-all duration-100" 
                                  style={{ width: `${file.progress || 0}%` }}
                                />
                              </div>
                            </div>
                          )}

                          {file.status === 'parsing' && (
                            <div className="flex items-center gap-1.5 text-amber-600 bg-amber-50 border border-amber-100 rounded-full px-2 py-0.5 text-[9px] font-bold">
                              <Loader2 className="h-3 w-3 animate-spin shrink-0" />
                              <span>解析中...</span>
                            </div>
                          )}

                          {file.status === 'success' && (
                            <div className="flex items-center gap-1 text-emerald-600 bg-emerald-50 border border-emerald-100 rounded-full px-2 py-0.5 text-[9px] font-bold">
                              <CheckCircle2 className="h-3 w-3 shrink-0" />
                              <span>解析成功</span>
                            </div>
                          )}

                          {file.status === 'failed' && (
                            <div className="flex items-center gap-1 text-red-600 bg-red-50 border border-red-100 rounded-full px-2 py-0.5 text-[9px] font-bold">
                              <AlertCircle className="h-3 w-3 shrink-0" />
                              <span>解析失败</span>
                            </div>
                          )}

                          {/* 删除文件按钮 */}
                          <button 
                            onClick={() => handleDeleteFile(file.id, file.name)}
                            disabled={file.status === 'uploading'}
                            className="p-1 rounded-md text-gray-400 hover:text-red-500 hover:bg-gray-100 disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-gray-400 transition-colors border-none bg-transparent cursor-pointer"
                            title="从库中删除"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
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
    </div>
  );
};
