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
  Plus,
  X,
  ChevronDown,
  Check
} from 'lucide-react';
import clsx from 'clsx';
import type { KnowledgeBase, KnowledgeChunk, KnowledgeFile } from '../types';

interface KnowledgeDetailProps {
  kb: KnowledgeBase;
  agentId: string;
  onBack: () => void;
  onUpdateKB: (updatedKB: KnowledgeBase) => void;
  showToast: (msg: string, type?: 'success' | 'error') => void;
  onOpenFile: (file: KnowledgeFile) => void;
}

const splitterOptions = [
  { value: 'smart', label: '智能切分', description: '优先保留标题、段落等自然边界' },
  { value: 'title', label: '按标题', description: '适合 Markdown、说明书、章节型文档' },
  { value: 'separator', label: '按段落', description: '适合段落边界清晰的普通文本' },
  { value: 'length', label: '按长度', description: '适合结构不明显的长文本' },
  { value: 'token', label: '按Token', description: '按模型上下文长度控制切片' },
] as const;

type SplitterType = typeof splitterOptions[number]['value'];

export const KnowledgeDetail: React.FC<KnowledgeDetailProps> = ({
  kb,
  agentId,
  onBack,
  onUpdateKB,
  showToast,
  onOpenFile,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedFileId, setSelectedFileId] = useState<string | null>(null);
  const [selectedChunkId, setSelectedChunkId] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const [chunksByFileId, setChunksByFileId] = useState<Record<string, KnowledgeChunk[]>>({});
  const [splitterType, setSplitterType] = useState<SplitterType>('smart');
  const [uploadDialogOpen, setUploadDialogOpen] = useState(false);
  const [uploadDialogDragOver, setUploadDialogDragOver] = useState(false);
  const [selectedUploadFile, setSelectedUploadFile] = useState<File | null>(null);
  const [isUploadingDialog, setIsUploadingDialog] = useState(false);
  const [strategyMenuOpen, setStrategyMenuOpen] = useState(false);
  const [pendingDeleteFile, setPendingDeleteFile] = useState<KnowledgeFile | null>(null);
  const [isDeletingFile, setIsDeletingFile] = useState(false);
  const activeSplitterOption = splitterOptions.find((option) => option.value === splitterType) || splitterOptions[0];

  // 模糊检索过滤当前知识库的文件
  const filteredFiles = useMemo(() => {
    return kb.files.filter(file => 
      file.name.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [kb.files, searchQuery]);

  // 计算文件总容量大小
  const totalSizeStr = useMemo(() => {
    let totalBytes = 0;
    kb.files.forEach(f => {
      const sizeStr = f.size.toUpperCase();
      const val = parseFloat(sizeStr);
      if (isNaN(val)) return;
      if (sizeStr.includes('MB')) {
        totalBytes += val * 1024 * 1024;
      } else if (sizeStr.includes('KB')) {
        totalBytes += val * 1024;
      } else {
        totalBytes += val;
      }
    });
    if (totalBytes === 0) return '0.00 KB';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB'];
    const i = Math.floor(Math.log(totalBytes) / Math.log(k));
    return parseFloat((totalBytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }, [kb.files]);

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

  const selectedFile = useMemo(() => {
    if (!selectedFileId) return null;
    return kb.files.find(f => f.id === selectedFileId) || null;
  }, [kb.files, selectedFileId]);

  // 获取当前选中的文件对象
  const mapBackendStatus = (status?: string): KnowledgeFile['status'] => {
    if (status === 'COMPLETED') return 'success';
    if (status === 'FAILED') return 'failed';
    if (status === 'DELETING') return 'deleting';
    if (status === 'DELETE_FAILED') return 'delete_failed';
    if (status === 'PROCESSING' || status === 'PENDING') return 'parsing';
    return 'success';
  };

  const mapUploadedFile = (item: {
    id: number;
    sourceFilename?: string;
    title?: string;
    fileSize?: number;
    splitterType?: string;
    errorMsg?: string;
    embeddingStatus?: string;
  }): KnowledgeFile => ({
    id: `knowledge-${item.id}`,
    backendId: item.id,
    name: item.sourceFilename || item.title || `knowledge-${item.id}`,
    size: formatFileSize(item.fileSize || 0),
    status: mapBackendStatus(item.embeddingStatus),
    progress: mapBackendStatus(item.embeddingStatus) === 'success' ? 100 : undefined,
    uploadedAt: getFormattedNow(),
    splitterType: item.splitterType,
    errorMsg: item.errorMsg,
  });

  React.useEffect(() => {
    const hasPendingFile = kb.files.some((file) => file.status === 'parsing' || file.status === 'deleting');
    if (!hasPendingFile) {
      return;
    }
    const timer = window.setInterval(() => {
      fetch(`/api/v1/agent-knowledge?agentId=${agentId}`)
        .then((response) => response.json())
        .then((result) => {
          if (!Array.isArray(result.data)) {
            return;
          }
          onUpdateKB({
            ...kb,
            files: result.data.map(mapUploadedFile),
            fileCount: result.data.length,
            updatedAt: getFormattedNow(),
          });
        })
        .catch((error) => {
          console.error('刷新知识文件状态失败', error);
        });
    }, 5000);
    return () => window.clearInterval(timer);
  }, [agentId, kb, onUpdateKB]);

  // 执行真实上传，并等待后端完成存储、切分与向量化
  const uploadFile = async (file: File, currentSplitterType: SplitterType) => {
    const newFileId = `file-upload-${Date.now()}`;
    const newFile: KnowledgeFile = {
      id: newFileId,
      name: file.name,
      size: formatFileSize(file.size),
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

    const formData = new FormData();
    formData.append('agentId', agentId);
    formData.append('title', file.name);
    formData.append('splitterType', currentSplitterType);
    formData.append('file', file);

    try {
      onUpdateKB({
        ...updatedKB,
        files: updatedKB.files.map(f =>
          f.id === newFileId ? { ...f, status: 'parsing', progress: 100 } : f
        )
      });
      const response = await fetch('/api/v1/agent-knowledge/upload', {
        method: 'POST',
        body: formData,
      });
      const result = await response.json();
      if (!response.ok || result.code !== '0') {
        throw new Error(result.message || '知识文件上传失败');
      }
      const uploadedFile = mapUploadedFile(result.data);
      onUpdateKB({
        ...updatedKB,
        files: updatedKB.files.map(f => f.id === newFileId ? uploadedFile : f),
        updatedAt: getFormattedNow()
      });
      setUploadDialogOpen(false);
      setSelectedUploadFile(null);
      showToast(`文件 "${file.name}" 已上传，正在后台解析和向量化`);
    } catch (error) {
      const message = error instanceof Error ? error.message : '知识文件上传失败';
      onUpdateKB({
        ...updatedKB,
        files: updatedKB.files.map(f =>
          f.id === newFileId ? { ...f, status: 'failed', errorMsg: message } : f
        )
      });
      showToast(message, 'error');
    }
  };

  // 处理文件选择
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = e.target.files;
    if (!selectedFiles || selectedFiles.length === 0) return;
    
    // 校验限额 (此处沿用免费版/个人版10个文件限制逻辑，以防越界)
    if (kb.files.length >= 10) {
      showToast("上传失败：知识库至多添加10个文件，请先清理已有文件。");
      return;
    }

    const file = selectedFiles[0];
    setSelectedUploadFile(file);
    
    // 重置 input
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const openUploadDialog = () => {
    if (isLimitReached) {
      return;
    }
    setUploadDialogOpen(true);
  };

  const closeUploadDialog = () => {
    if (isUploadingDialog) {
      return;
    }
    setUploadDialogOpen(false);
    setUploadDialogDragOver(false);
    setStrategyMenuOpen(false);
    setSelectedUploadFile(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const submitUploadDialog = async () => {
    if (isUploadingDialog) {
      return;
    }
    if (!selectedUploadFile) {
      showToast('请选择要上传的文件', 'error');
      return;
    }
    setIsUploadingDialog(true);
    setStrategyMenuOpen(false);
    try {
      await uploadFile(selectedUploadFile, splitterType);
    } finally {
      setIsUploadingDialog(false);
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
      showToast("上传失败：知识库至多添加10个文件，请先清理已有文件。");
      return;
    }

    const droppedFiles = e.dataTransfer.files;
    if (droppedFiles && droppedFiles.length > 0) {
      setSelectedUploadFile(droppedFiles[0]);
      setUploadDialogOpen(true);
    }
  };

  const handleDialogDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setUploadDialogDragOver(false);
    const droppedFiles = e.dataTransfer.files;
    if (droppedFiles && droppedFiles.length > 0) {
      setSelectedUploadFile(droppedFiles[0]);
    }
  };

  // 删除文件
  const handleDeleteFile = (fileId: string) => {
    const targetFile = kb.files.find(f => f.id === fileId);
    if (!targetFile) return;
    setPendingDeleteFile(targetFile);
  };

  const confirmDeleteFile = async () => {
    if (!pendingDeleteFile || isDeletingFile) return;

    setIsDeletingFile(true);
    try {
      if (pendingDeleteFile.backendId) {
        const response = await fetch(`/api/v1/agent-knowledge/${pendingDeleteFile.backendId}?agentId=${agentId}`, { method: 'DELETE' });
        const result = await response.json();
        if (!response.ok || result.code !== '0') {
          throw new Error(result.message || '删除知识文件失败');
        }
      }
      const newFiles = kb.files.filter(f => f.id !== pendingDeleteFile.id);
      const updatedKB = {
        ...kb,
        files: newFiles,
        fileCount: newFiles.length,
        updatedAt: getFormattedNow()
      };
      onUpdateKB(updatedKB);
      if (selectedFileId === pendingDeleteFile.id) {
        setSelectedFileId(null);
        setSelectedChunkId(null);
      }
      showToast(`已删除文件 "${pendingDeleteFile.name}"`);
      setPendingDeleteFile(null);
    } catch (error) {
      const message = error instanceof Error ? error.message : '删除知识文件失败';
      showToast(message, 'error');
    } finally {
      setIsDeletingFile(false);
    }
  };

  // 文件重命名
  const handleRenameFile = (fileId: string, currentName: string) => {
    const newName = window.prompt(`重命名文件 "${currentName}" 为：`, currentName);
    if (newName === null) return;
    const trimmed = newName.trim();
    if (!trimmed) {
      showToast('文件名不能为空！');
      return;
    }
    if (kb.files.some(f => f.id !== fileId && f.name === trimmed)) {
      showToast('同名文件已存在，请换一个名称！');
      return;
    }

    const updatedKB = {
      ...kb,
      files: kb.files.map(f => f.id === fileId ? { ...f, name: trimmed } : f),
      updatedAt: getFormattedNow()
    };
    onUpdateKB(updatedKB);
    showToast(`文件已成功重命名为 "${trimmed}"`);
  };

  const isLimitReached = kb.files.length >= 10;

  React.useEffect(() => {
    if (!selectedFile || !selectedFile.backendId || selectedFile.status !== 'success') return;
    if (chunksByFileId[selectedFile.id]) return;

    fetch(`/api/v1/agent-knowledge/${selectedFile.backendId}/chunks?agentId=${agentId}`)
      .then((response) => response.json())
      .then((result) => {
        const chunks = Array.isArray(result.data) ? result.data : [];
        setChunksByFileId((prev) => ({ ...prev, [selectedFile.id]: chunks }));
      })
      .catch((error) => {
        console.error('加载知识分块失败', error);
        showToast('加载知识分块失败', 'error');
      });
  }, [chunksByFileId, selectedFile, showToast]);

  // 根据选中的文件ID过滤分块
  const selectedFileChunks = useMemo(() => {
    if (!selectedFileId) return [];
    return chunksByFileId[selectedFileId] || [];
  }, [chunksByFileId, selectedFileId]);

  // 根据检索过滤分块列表
  const filteredSelectedFileChunks = useMemo(() => {
    return selectedFileChunks.filter(chunk => 
      chunk.content.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [selectedFileChunks, searchQuery]);

  // 获取当前选中的知识块对象
  const selectedChunk = useMemo(() => {
    if (!selectedChunkId) return null;
    return selectedFileChunks.find(c => c.id === selectedChunkId) || null;
  }, [selectedFileChunks, selectedChunkId]);

  return (
    <div 
      className="flex h-full w-full flex-col overflow-hidden select-none font-sans bg-white animate-in fade-in duration-200 relative"
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      {/* 隐藏的上传选择器 */}
      <input 
        type="file" 
        ref={fileInputRef}
        onChange={handleFileChange}
        className="hidden"
        accept=".txt,.pdf,.doc,.docx,.xls,.xlsx,.csv,.md"
        disabled={isLimitReached}
      />

      {/* 拖拽全屏覆盖层提示 */}
      {isDragOver && (
        <div className="absolute inset-0 bg-[#2D336B]/15 backdrop-blur-xs flex flex-col items-center justify-center border-4 border-dashed border-[#2D336B] rounded-lg z-50 transition-all pointer-events-none animate-in fade-in duration-150">
          <UploadCloud className="h-16 w-16 text-[#2D336B] animate-bounce mb-3" />
          <p className="text-sm font-bold text-[#2D336B]">释放文件以上传到当前知识库</p>
          <p className="text-xs text-gray-500 mt-1">支持 TXT, PDF, Word, Excel, Markdown 等格式</p>
        </div>
      )}

      {/* 1. 顶部面包屑与基本信息行 */}
      <div className="flex-none px-6 pt-4 pb-2">
        {/* 面包屑 */}
        <div className="flex items-center gap-1.5 text-xs text-gray-400 font-semibold mb-3">
          <span className="hover:text-gray-700 cursor-pointer" onClick={onBack}>知识库</span>
          <span className="text-gray-300">/</span>
          <span className="text-gray-700 font-bold">{kb.name}</span>
        </div>

        {/* 标题 & 信息 & 操作按钮列 */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            {/* 返回按钮 */}
            <button 
              onClick={onBack}
              className="cursor-pointer p-1.5 rounded-lg hover:bg-gray-100 text-gray-500 hover:text-gray-800 transition-colors border-none bg-transparent"
              title="返回知识中心"
            >
              <ArrowLeft className="h-5 w-5" />
            </button>
            
            {/* 书本图标背景 */}
            <div className="size-10 rounded-lg bg-gray-50 border border-gray-100 text-gray-600 flex items-center justify-center shrink-0 shadow-xs">
              <svg className="w-5 h-5 text-gray-500" fill="none" stroke="currentColor" strokeWidth="2.2" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
              </svg>
            </div>

            {/* 基本汇总信息 */}
            <div className="flex flex-col">
              <h2 className="text-base font-bold text-gray-900 leading-tight">{kb.name}</h2>
              <div className="flex items-center gap-1.5 mt-1.5">
                <span className="bg-gray-100 text-gray-600 px-2 py-0.5 rounded text-[10px] font-semibold">{totalSizeStr}</span>
                <span className="bg-gray-100 text-gray-600 px-2 py-0.5 rounded text-[10px] font-semibold">{kb.files.length} 个文件</span>
                <span className="bg-gray-100 text-gray-600 px-2 py-0.5 rounded text-[10px] font-semibold">点击文件进入分块工作台</span>
              </div>
            </div>
          </div>

          {/* 添加内容按钮 */}
          <button
            onClick={openUploadDialog}
            disabled={isLimitReached}
            className={clsx(
              "flex items-center gap-1.5 text-xs font-bold px-4 py-2 rounded-lg cursor-pointer transition-all border border-transparent shadow-xs active:scale-97 outline-none",
              isLimitReached
                ? "bg-gray-100 text-gray-400 cursor-not-allowed"
                : "bg-gray-900 text-white hover:bg-gray-800"
            )}
          >
            <Plus className="w-4 h-4" />
            添加内容
          </button>
        </div>
      </div>

      {uploadDialogOpen && (
        <div className="absolute inset-0 z-[70] flex items-center justify-center bg-gray-950/20 px-4 backdrop-blur-xs">
          <div
            className="w-full max-w-xl rounded-xl border border-gray-200 bg-white p-5 shadow-2xl animate-in fade-in zoom-in-95 duration-150"
            onDragOver={(e) => {
              e.preventDefault();
              e.stopPropagation();
              setUploadDialogDragOver(true);
            }}
            onDragLeave={(e) => {
              e.stopPropagation();
              if (!e.currentTarget.contains(e.relatedTarget as Node)) {
                setUploadDialogDragOver(false);
              }
            }}
            onDrop={handleDialogDrop}
          >
            <div className="mb-4 flex items-start justify-between gap-3">
              <div>
                <h3 className="text-base font-bold text-gray-900">上传文档</h3>
                <p className="mt-1 text-xs font-medium text-gray-500">选择文件并配置切分策略后开始上传</p>
              </div>
              <button
                type="button"
                onClick={closeUploadDialog}
                disabled={isUploadingDialog}
                className="rounded-md border-none bg-transparent p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-700"
                title="关闭"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <div className="mb-1.5 text-xs font-bold text-gray-700">本地文件</div>
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  className={clsx(
                    "flex w-full cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed p-6 transition-colors",
                    uploadDialogDragOver
                      ? "border-gray-900 bg-gray-50"
                      : selectedUploadFile
                        ? "border-gray-300 bg-gray-50/70"
                        : "border-gray-200 bg-white hover:border-gray-300 hover:bg-gray-50"
                  )}
                >
                  {selectedUploadFile ? (
                    <>
                      <UploadCloud className="h-7 w-7 text-gray-800" />
                      <span className="max-w-full break-all text-center text-sm font-bold text-gray-800">
                        {selectedUploadFile.name}
                      </span>
                      <span className="text-xs font-mono text-gray-400">{formatFileSize(selectedUploadFile.size)}</span>
                    </>
                  ) : (
                    <>
                      <UploadCloud className="h-7 w-7 text-gray-400" />
                      <span className="text-sm font-bold text-gray-700">拖拽文件到此处，或点击选择</span>
                      <span className="text-xs font-medium text-gray-400">支持 TXT、PDF、Word、Excel、CSV、Markdown</span>
                    </>
                  )}
                </button>
                {selectedUploadFile && (
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedUploadFile(null);
                      if (fileInputRef.current) {
                        fileInputRef.current.value = '';
                      }
                    }}
                    className="mt-2 border-none bg-transparent px-0 text-xs font-bold text-gray-400 transition-colors hover:text-gray-700"
                  >
                    重新选择
                  </button>
                )}
              </div>

              <div className="rounded-xl border border-gray-200 bg-gray-50/50 p-3">
                <div className="mb-2 flex items-center justify-between">
                  <div>
                    <div className="text-xs font-bold text-gray-800">切分方式</div>
                    <div className="mt-0.5 text-[11px] font-medium text-gray-400">决定文档如何切成可检索知识块</div>
                  </div>
                  <span className="rounded-full bg-white px-2 py-1 text-[10px] font-bold text-gray-400 ring-1 ring-gray-200">
                    {activeSplitterOption.label}
                  </span>
                </div>

                <div className="relative">
                  <button
                    type="button"
                    onClick={() => setStrategyMenuOpen((open) => !open)}
                    disabled={isUploadingDialog}
                    className={clsx(
                      "flex w-full items-center justify-between rounded-xl border bg-white px-3 py-3 text-left shadow-xs transition-all",
                      strategyMenuOpen
                        ? "border-gray-900 ring-2 ring-gray-900/5"
                        : "border-gray-200 hover:border-gray-300 hover:shadow-sm",
                      isUploadingDialog && "cursor-not-allowed opacity-60"
                    )}
                  >
                    <span className="min-w-0">
                      <span className="block text-sm font-bold text-gray-900">{activeSplitterOption.label}</span>
                      <span className="mt-0.5 block truncate text-xs font-medium text-gray-400">
                        {activeSplitterOption.description}
                      </span>
                    </span>
                    <ChevronDown
                      className={clsx(
                        "ml-3 h-4 w-4 shrink-0 text-gray-400 transition-transform",
                        strategyMenuOpen && "rotate-180 text-gray-700"
                      )}
                    />
                  </button>

                  {strategyMenuOpen && (
                    <div className="absolute left-0 right-0 top-[calc(100%+0.5rem)] z-[90] overflow-hidden rounded-xl border border-gray-200 bg-white p-1.5 shadow-xl ring-1 ring-gray-950/5">
                      {splitterOptions.map((option) => {
                        const selected = option.value === splitterType;
                        return (
                          <button
                            key={option.value}
                            type="button"
                            onClick={() => {
                              setSplitterType(option.value);
                              setStrategyMenuOpen(false);
                            }}
                            className={clsx(
                              "flex w-full items-start gap-3 rounded-lg px-3 py-2.5 text-left transition-colors",
                              selected ? "bg-gray-900 text-white" : "bg-white text-gray-700 hover:bg-gray-50"
                            )}
                          >
                            <span
                              className={clsx(
                                "mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-full border",
                                selected ? "border-white bg-white text-gray-900" : "border-gray-300 text-transparent"
                              )}
                            >
                              <Check className="h-3 w-3" />
                            </span>
                            <span className="min-w-0">
                              <span className="block text-xs font-bold">{option.label}</span>
                              <span className={clsx("mt-0.5 block text-[11px] leading-4", selected ? "text-white/70" : "text-gray-400")}>
                                {option.description}
                              </span>
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>
            </div>

            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={closeUploadDialog}
                disabled={isUploadingDialog}
                className="rounded-lg border border-gray-200 bg-white px-4 py-2 text-xs font-bold text-gray-600 transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:text-gray-300"
              >
                取消
              </button>
              <button
                type="button"
                onClick={submitUploadDialog}
                disabled={!selectedUploadFile || isUploadingDialog}
                className="rounded-lg border border-transparent bg-gray-900 px-4 py-2 text-xs font-bold text-white transition-colors hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-200 disabled:text-gray-400"
              >
                {isUploadingDialog ? '上传中...' : '上传'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 限额预警提醒 */}
      {isLimitReached && (
        <div className="mx-6 mt-1 mb-2 px-3 py-2 bg-red-50 border border-red-100 text-red-700 text-xs rounded-lg flex items-center gap-2 animate-pulse flex-none">
          <AlertCircle className="w-4 h-4 shrink-0 text-red-500" />
          <span>当前知识库已达到最大容量限制 (10/10个文件)，请先删除多余文件再进行上传。</span>
        </div>
      )}

      {/* 2. 双栏主要功能视图区 */}
      <div className="flex-1 flex overflow-hidden border-t border-gray-100 mt-3 bg-white">
        
        {/* 左栏：文件列表 (占比 55%) */}
        <div className="w-full flex flex-col overflow-hidden bg-white">
          {/* 文件搜索 */}
          <div className="flex items-center gap-2 px-4 py-3 flex-none bg-white border-b border-gray-50">
            <div className="relative flex-1">
              <input 
                type="text" 
                placeholder="搜索文件名称..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="flex border border-gray-200 bg-white pl-8 pr-3 py-1.5 rounded-md text-xs focus:outline-none focus:ring-1 focus:ring-gray-300 text-gray-700 placeholder-gray-400 h-8 w-full transition-all font-medium"
              />
              <Search className="absolute left-2.5 top-2.25 h-3.5 w-3.5 text-gray-400 pointer-events-none" />
            </div>
          </div>

          {/* 文件列表 Table */}
          <div className="flex-1 overflow-auto no-scrollbar pb-4">
            {filteredFiles.length === 0 ? (
              <div className="h-full w-full flex flex-col items-center justify-center py-16 text-gray-400 font-bold text-xs">
                暂无匹配文件
              </div>
            ) : (
              <table className="min-w-full divide-y divide-gray-100 text-left border-collapse">
                <thead>
                  <tr className="text-[10px] text-gray-400 font-bold border-b border-gray-50 bg-gray-50/20">
                    <th className="pb-2 pt-2 pl-4 font-semibold uppercase tracking-wider w-[50%]">文件</th>
                    <th className="pb-2 pt-2 font-semibold uppercase tracking-wider w-[20%]">状态</th>
                    <th className="pb-2 pt-2 font-semibold uppercase tracking-wider w-[20%]">描述</th>
                    <th className="pb-2 pt-2 text-right pr-4 font-semibold uppercase tracking-wider w-[10%]">操作</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50 bg-white text-xs text-gray-700">
                  {filteredFiles.map((file) => {
                    const isExcel = file.name.endsWith('.xlsx') || file.name.endsWith('.xls') || file.name.endsWith('.csv');
                    return (
                      <tr 
                        key={file.id} 
                        onClick={() => {
                          if (file.status === 'uploading' || file.status === 'parsing' || file.status === 'deleting' || file.status === 'delete_failed') {
                            showToast('文件正在处理中，请解析完成后再选定查看分块。');
                            return;
                          }
                          onOpenFile(file);
                        }}
                        className={clsx(
                          "hover:bg-gray-50/70 transition-colors group cursor-pointer border-l-2 border-l-transparent bg-transparent"
                        )}
                      >
                        {/* 文件图表与名 (大小、时间在名字下方) */}
                        <td className="py-3 pl-4 pr-3 flex items-start gap-3 overflow-hidden min-w-0">
                          {isExcel ? (
                            <div className="p-1.5 rounded bg-emerald-50 text-emerald-600 shrink-0 mt-0.5">
                              <FileSpreadsheet className="size-4" />
                            </div>
                          ) : (
                            <div className="p-1.5 rounded bg-blue-50 text-blue-600 shrink-0 mt-0.5">
                              <FileText className="size-4" />
                            </div>
                          )}
                          <div className="min-w-0 flex-1">
                            <span className="truncate font-bold block leading-tight text-gray-700 group-hover:text-gray-900" title={file.name}>
                              {file.name}
                            </span>
                            <span className="text-[10px] text-gray-400 font-mono font-medium mt-1 block">
                              {file.size} • {file.uploadedAt}
                            </span>
                          </div>
                        </td>

                        {/* 解析状态 */}
                        <td className="py-3 pr-3 align-middle">
                          {file.status === 'success' && (
                            <div className="flex items-center gap-1 text-emerald-600 bg-emerald-50 border border-emerald-100 rounded-full px-2 py-0.5 text-[9px] font-bold w-fit">
                              <CheckCircle2 className="h-3 w-3 shrink-0" />
                              <span>解析完成</span>
                            </div>
                          )}
                          {file.status === 'parsing' && (
                            <div className="flex items-center gap-1 text-amber-600 bg-amber-50 border border-amber-100 rounded-full px-2 py-0.5 text-[9px] font-bold w-fit">
                              <Loader2 className="h-3 w-3 animate-spin shrink-0" />
                              <span>解析中</span>
                            </div>
                          )}
                          {file.status === 'uploading' && (
                            <div className="flex items-center gap-1 text-[#2D336B] bg-indigo-50 border border-indigo-100 rounded-full px-2 py-0.5 text-[9px] font-bold w-fit">
                              <Loader2 className="h-3 w-3 animate-spin shrink-0" />
                              <span>上传中 {file.progress || 0}%</span>
                            </div>
                          )}
                          {file.status === 'failed' && (
                            <div className="flex items-center gap-1 text-red-600 bg-red-50 border border-red-100 rounded-full px-2 py-0.5 text-[9px] font-bold w-fit">
                              <AlertCircle className="h-3 w-3 shrink-0" />
                              <span>解析失败</span>
                            </div>
                          )}
                          {file.status === 'deleting' && (
                            <div className="flex items-center gap-1 text-gray-600 bg-gray-50 border border-gray-100 rounded-full px-2 py-0.5 text-[9px] font-bold w-fit">
                              <Loader2 className="h-3 w-3 animate-spin shrink-0" />
                              <span>删除中</span>
                            </div>
                          )}
                          {file.status === 'delete_failed' && (
                            <div className="flex items-center gap-1 text-red-600 bg-red-50 border border-red-100 rounded-full px-2 py-0.5 text-[9px] font-bold w-fit">
                              <AlertCircle className="h-3 w-3 shrink-0" />
                              <span>删除失败</span>
                            </div>
                          )}
                        </td>

                        {/* 描述 */}
                        <td className="py-3 pr-2 text-gray-400 font-medium truncate max-w-[8rem] align-middle">
                          {file.errorMsg || file.splitterType || '-'}
                        </td>

                        {/* 操作栏 */}
                        <td className="py-3 text-right pr-4 align-middle">
                          <div 
                            className="opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-end gap-1 select-none"
                            onClick={(e) => e.stopPropagation()}
                          >
                            {/* 重命名 */}
                            <button 
                              onClick={() => handleRenameFile(file.id, file.name)}
                              disabled={file.status === 'uploading' || file.status === 'parsing' || file.status === 'deleting'}
                              className="p-1 rounded text-gray-400 hover:text-gray-700 hover:bg-gray-100 disabled:opacity-35 transition-colors border-none bg-transparent cursor-pointer"
                              title="重命名"
                            >
                              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" d="m16.862 4.487 1.687-1.688a1.875 1.875 0 1 1 2.652 2.652L6.83 20.013a4.5 4.5 0 0 1-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 0 1 1.13-1.897l8.932-8.931Zm0 0L19.5 7.125M18 14v4.75A2.25 2.25 0 0 1 15.75 21H5.25A2.25 2.25 0 0 1 3 18.75V8.25A2.25 2.25 0 0 1 5.25 6H10" />
                              </svg>
                            </button>

                            {/* 删除 */}
                            <button 
                              onClick={() => handleDeleteFile(file.id)}
                              disabled={file.status === 'uploading' || file.status === 'deleting'}
                              className="p-1 rounded text-gray-400 hover:text-red-500 hover:bg-red-50 disabled:opacity-35 transition-colors border-none bg-transparent cursor-pointer"
                              title="从库中删除"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
        </div>

        {/* 右栏：分块展示区 */}
        <div className="hidden">
          {/* 右栏头部 */}
          <div className="px-4 py-3 flex items-center justify-between border-b border-gray-100 bg-white flex-none">
            <span className="text-xs font-bold text-gray-800">
              {selectedFile ? `关联分块 (${filteredSelectedFileChunks.length})` : '知识分块'}
            </span>
            {selectedFileId && (
              <button 
                onClick={() => {
                  setSelectedFileId(null);
                  setSelectedChunkId(null);
                }}
                className="text-[10px] text-gray-400 hover:text-gray-600 transition-colors cursor-pointer border-none bg-transparent font-semibold"
              >
                取消选定
              </button>
            )}
          </div>

          {/* 知识块列表内容 */}
          {!selectedFileId ? (
            <div className="flex-1 flex flex-col items-center justify-center p-8 text-gray-400 text-xs text-center select-none bg-white/40">
              <div className="size-14 rounded-full bg-gray-50 border border-gray-100 text-gray-300 flex items-center justify-center mb-3">
                <svg className="w-6 h-6 text-gray-400" fill="none" stroke="currentColor" strokeWidth="1.8" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
                </svg>
              </div>
              <span className="font-bold text-gray-400">选中文档以查看分块</span>
              <span className="text-[10px] text-gray-300 mt-1.5 leading-normal max-w-[15rem] font-medium">
                点击左侧文件表格行，即可在此区域查看后端真实切分的知识块
              </span>
            </div>
          ) : filteredSelectedFileChunks.length === 0 ? (
            <div className="flex-1 flex flex-col items-center justify-center p-8 text-gray-400 text-xs text-center select-none bg-white/40">
              暂无匹配的分块内容
            </div>
          ) : (
            <div className="flex-1 overflow-y-auto p-4 space-y-3 no-scrollbar">
              {filteredSelectedFileChunks.map((chunk) => (
                <div 
                  key={chunk.id}
                  onClick={() => setSelectedChunkId(chunk.id)}
                  className={clsx(
                    "p-3.5 rounded-xl border transition-all cursor-pointer select-none bg-white relative overflow-hidden",
                    selectedChunkId === chunk.id 
                      ? "border-gray-900 shadow-sm ring-1 ring-gray-900" 
                      : "border-gray-200/70 hover:border-gray-300 hover:shadow-xs"
                  )}
                >
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-[10px] font-bold text-gray-400 font-mono">#{chunk.seq} 分块</span>
                    <span className="text-[9px] bg-gray-50 text-gray-500 px-1.5 py-0.5 rounded font-mono font-bold">{chunk.length} 字符</span>
                  </div>
                  <p className="text-xs text-gray-600 line-clamp-2 leading-relaxed font-semibold whitespace-pre-wrap break-words">
                    {chunk.content}
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {pendingDeleteFile && (
        <div
          className="absolute inset-0 z-[70] flex items-center justify-center bg-gray-950/20 px-4 backdrop-blur-[2px] animate-in fade-in duration-150"
          onClick={() => {
            if (!isDeletingFile) setPendingDeleteFile(null);
          }}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-file-dialog-title"
            tabIndex={-1}
            onClick={(e) => e.stopPropagation()}
            onKeyDown={(e) => {
              if (e.key === 'Escape' && !isDeletingFile) {
                setPendingDeleteFile(null);
              }
            }}
            className="w-full max-w-[380px] overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-2xl animate-in zoom-in-95 slide-in-from-bottom-2 duration-200"
          >
            <div className="flex items-start gap-3 border-b border-gray-100 px-5 py-4">
              <div className="grid size-9 shrink-0 place-items-center rounded-full bg-red-50 text-red-600">
                <Trash2 className="size-4" />
              </div>
              <div className="min-w-0 flex-1">
                <h3 id="delete-file-dialog-title" className="m-0 text-sm font-bold text-gray-900">
                  删除知识文件？
                </h3>
                <p className="mt-1 text-xs leading-5 text-gray-500">
                  文件将从当前知识库中移除，相关解析结果也会一并失效。
                </p>
              </div>
              <button
                type="button"
                aria-label="关闭删除确认"
                disabled={isDeletingFile}
                onClick={() => setPendingDeleteFile(null)}
                className="grid size-7 shrink-0 place-items-center rounded-md border-none bg-transparent text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 disabled:cursor-not-allowed disabled:opacity-40"
              >
                <X className="size-4" />
              </button>
            </div>

            <div className="px-5 py-4">
              <div className="rounded-xl border border-red-100 bg-red-50/60 px-3.5 py-3">
                <div className="flex items-start gap-2">
                  <AlertCircle className="mt-0.5 size-4 shrink-0 text-red-500" />
                  <div className="min-w-0">
                    <div className="truncate text-xs font-bold text-red-700" title={pendingDeleteFile.name}>
                      {pendingDeleteFile.name}
                    </div>
                    <div className="mt-1 text-[11px] leading-4 text-red-600/80">
                      此操作不可撤销，请确认不再需要该文件。
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 bg-gray-50 px-5 py-3">
              <button
                type="button"
                disabled={isDeletingFile}
                onClick={() => setPendingDeleteFile(null)}
                className="h-9 rounded-lg border border-gray-200 bg-white px-3.5 text-xs font-bold text-gray-600 transition-colors hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-50"
              >
                取消
              </button>
              <button
                type="button"
                disabled={isDeletingFile}
                onClick={confirmDeleteFile}
                className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-red-600 bg-red-600 px-3.5 text-xs font-bold text-white shadow-sm transition-colors hover:bg-red-700 disabled:cursor-wait disabled:opacity-75"
              >
                {isDeletingFile && <Loader2 className="size-3.5 animate-spin" />}
                <span>{isDeletingFile ? '删除中' : '确认删除'}</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 3. 右侧滑出抽屉面板 */}
      {selectedChunk && (
        <div className="absolute inset-0 z-50 flex justify-end">
          <div 
            onClick={() => setSelectedChunkId(null)}
            className="absolute inset-0 bg-gray-900/10 backdrop-blur-xs transition-opacity animate-in fade-in duration-200"
          />
          <div className="relative w-96 h-full bg-white shadow-2xl border-l border-gray-100 flex flex-col overflow-hidden animate-in slide-in-from-right duration-300">
            <div className="p-4 border-b border-gray-100 flex items-center justify-between flex-none bg-white">
              <div className="flex flex-col min-w-0">
                <h3 className="text-sm font-bold text-gray-800">分块详情</h3>
                <span className="text-[10px] text-gray-400 mt-1 font-semibold truncate max-w-[15rem]" title={selectedFile?.name || '知识文件'}>
                  来源：{selectedFile?.name || '知识文件'}
                </span>
              </div>
              <button 
                onClick={() => setSelectedChunkId(null)}
                className="p-1 rounded-md hover:bg-gray-100 text-gray-400 hover:text-gray-600 cursor-pointer transition-colors border-none bg-transparent"
                title="关闭详情"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2.2" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            
            <div className="flex-1 p-5 overflow-y-auto space-y-5 no-scrollbar bg-white">
              <div className="grid grid-cols-2 gap-3 text-xs bg-gray-50 border border-gray-100 rounded-xl p-4">
                <div>
                  <div className="text-gray-400 text-[10px] font-bold uppercase tracking-wider">分块序号</div>
                  <div className="text-gray-700 font-extrabold font-mono mt-1 text-sm">#{selectedChunk.seq}</div>
                </div>
                <div>
                  <div className="text-gray-400 text-[10px] font-bold uppercase tracking-wider">字符长度</div>
                  <div className="text-gray-700 font-extrabold font-mono mt-1 text-sm">{selectedChunk.length}</div>
                </div>
                <div className="col-span-2 pt-3 border-t border-gray-200/50">
                  <div className="text-gray-400 text-[10px] font-bold uppercase tracking-wider">切分策略</div>
                  <div className="text-gray-600 font-bold font-mono mt-1">{selectedChunk.splitterType}</div>
                </div>
              </div>

              <div className="space-y-2 flex flex-col">
                <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">分块原文</span>
                <pre className="text-xs text-gray-700 leading-relaxed bg-[#FAFAFA]/80 border border-gray-150 rounded-xl p-4 font-semibold whitespace-pre-wrap break-words select-text font-mono overflow-x-auto">
                  {selectedChunk.content}
                </pre>
              </div>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};
