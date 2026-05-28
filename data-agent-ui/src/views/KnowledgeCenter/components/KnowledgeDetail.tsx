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
  Plus
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
  const [selectedFileId, setSelectedFileId] = useState<string | null>(null);
  const [selectedChunkId, setSelectedChunkId] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isDragOver, setIsDragOver] = useState(false);

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

  // 获取当前选中的文件对象
  const selectedFile = useMemo(() => {
    if (!selectedFileId) return null;
    return kb.files.find(f => f.id === selectedFileId) || null;
  }, [kb.files, selectedFileId]);

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
    
    // 校验限额 (此处沿用免费版/个人版10个文件限制逻辑，以防越界)
    if (kb.files.length >= 10) {
      showToast("上传失败：知识库至多添加10个文件，请先清理已有文件。");
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
      showToast("上传失败：知识库至多添加10个文件，请先清理已有文件。");
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

      // 安全退避：重置选中状态以防报错
      if (selectedFileId === fileId) {
        setSelectedFileId(null);
        setSelectedChunkId(null);
      }
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

  // 仿真的知识分块列表数据源
  const mockChunks = useMemo(() => {
    const chunks: Array<{
      id: string;
      fileId: string;
      fileName: string;
      seq: number;
      content: string;
      length: number;
      time: string;
    }> = [];
    kb.files.forEach(f => {
      if (f.status === 'success') {
        const nameUpper = f.name.toUpperCase();
        if (nameUpper.includes('游戏') || nameUpper.includes('GAME')) {
          chunks.push({
            id: `chunk-${f.id}-1`,
            fileId: f.id,
            fileName: f.name,
            seq: 1,
            content: `【${f.name} #分块1】二季度游戏大盘活跃度与付费模型分析：年轻化受众群体对即时语音社交、战队定制道具以及社群轻量化竞技玩法的偏好出现显著爬升（YoY 增长约 28.5%）。推荐在下阶段投放时主攻短视频平台，以创意交互广告（Playable Ads）作为核心承载页，降低买量成本。`,
            length: 154,
            time: f.uploadedAt
          });
          chunks.push({
            id: `chunk-${f.id}-2`,
            fileId: f.id,
            fileName: f.name,
            seq: 2,
            content: `【${f.name} #分块2】KOL 矩阵联动的转化漏斗归因：在以特定电竞主播和泛娱乐博主为主的推广合集里，流失率多集中在“落地页加载”到“首存转化”阶段（流失达 44%）。拟优化游戏首包加载压缩率，降低冷启动等待时间，并在新客注册的前 3 日通过游戏内邮件赠送高性价比限时体验礼包。`,
            length: 155,
            time: f.uploadedAt
          });
          chunks.push({
            id: `chunk-${f.id}-3`,
            fileId: f.id,
            fileName: f.name,
            seq: 3,
            content: `【${f.name} #分块3】长线留存召回方案：对活跃衰退周期进行区间建模。用户连续不登录超过 7 日即列为“高度流失倾向”，自动触发定制推送通知或短信召回。在此阶段建议搭配游戏版本更新进行福利诱导，召回优惠额度控制在单位买量成本（CAC）的 30% 以内，以防挤占利润边际。`,
            length: 153,
            time: f.uploadedAt
          });
        } else if (nameUpper.includes('流失') || nameUpper.includes('CUSTOMER') || nameUpper.includes('USER') || nameUpper.includes('ORDERS') || nameUpper.includes('CSV')) {
          chunks.push({
            id: `chunk-${f.id}-1`,
            fileId: f.id,
            fileName: f.name,
            seq: 1,
            content: `【${f.name} #分块1】新老客群销售归因与交叉购买分析：流失模型输出显示，单次购买型新用户（首单 30 天内无二次复购）的流失率极高，中位数接近 55%。其核心诱因为“缺乏持续购买动机”。建议针对此类客群配置智能卡片推送，在首单派发后第 5 天匹配相似产品线优惠券。`,
            length: 152,
            time: f.uploadedAt
          });
          chunks.push({
            id: `chunk-${f.id}-2`,
            fileId: f.id,
            fileName: f.name,
            seq: 2,
            content: `【${f.name} #分块2】复购模型参数调优：利用关联规则分析（Apriori）挖掘 products.xlsx 底层购买偏好，确定“高客单价电子产品”与“耗材配件/保护壳”存在 72% 的关联置信度。未来应当将此规则内置为系统推荐策略，在结算台和购物车浮窗内实施定向搭配交叉售卖。`,
            length: 154,
            time: f.uploadedAt
          });
          chunks.push({
            id: `chunk-${f.id}-3`,
            fileId: f.id,
            fileName: f.name,
            seq: 3,
            content: `【${f.name} #分块3】系统状态与异常降级策略：流失监测后台进行远程推送时，如遇远程短信网关调用超时（超时阈值设为 1200ms），系统将默认退避为“应用内 push 通知排队”，并将错误码记录在后端统一监控埋点中（日志标识 TraceId 联动），避免阻碍正常的结算前台事务。`,
            length: 161,
            time: f.uploadedAt
          });
        } else {
          chunks.push({
            id: `chunk-${f.id}-1`,
            fileId: f.id,
            fileName: f.name,
            seq: 1,
            content: `【${f.name} #分块1】文档元数据基础要素提炼。当前切片包含了此文书的第一层章节树，包含了引言、系统基本定义以及业务范围描述。通过构建该切分实体，可以辅助 LLM 在推理生成时快速获取并拼装文档上下文，避免模型因信息稀疏导致泛化幻觉。`,
            length: 140,
            time: f.uploadedAt
          });
          chunks.push({
            id: `chunk-${f.id}-2`,
            fileId: f.id,
            fileName: f.name,
            seq: 2,
            content: `【${f.name} #分块2】核心规范与排版细节：本节详细提炼了文档中的关键公式、代码示例及格式排版规范。提取出重要业务指引参数如：多行方程组的自适应渲染、数学矩阵的对齐样式以及 Web 端 KaTeX 解析覆盖层配置，用于上下文的高效召回和样式兜底。`,
            length: 146,
            time: f.uploadedAt
          });
        }
      }
    });
    return chunks;
  }, [kb.files]);

  // 根据选中的文件ID过滤分块
  const selectedFileChunks = useMemo(() => {
    if (!selectedFileId) return [];
    return mockChunks.filter(chunk => chunk.fileId === selectedFileId);
  }, [mockChunks, selectedFileId]);

  // 根据检索过滤分块列表
  const filteredSelectedFileChunks = useMemo(() => {
    return selectedFileChunks.filter(chunk => 
      chunk.content.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [selectedFileChunks, searchQuery]);

  // 获取当前选中的知识块对象
  const selectedChunk = useMemo(() => {
    if (!selectedChunkId) return null;
    return mockChunks.find(c => c.id === selectedChunkId) || null;
  }, [mockChunks, selectedChunkId]);

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
                <span className="bg-gray-100 text-gray-600 px-2 py-0.5 rounded text-[10px] font-semibold">{mockChunks.length} 个知识块</span>
                <span className="bg-gray-100 text-gray-600 px-2 py-0.5 rounded text-[10px] font-semibold">{selectedFileId ? '1' : '0'} 选中</span>
              </div>
            </div>
          </div>

          {/* 添加内容按钮 */}
          <button
            onClick={() => !isLimitReached && fileInputRef.current?.click()}
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
        <div className="w-[55%] flex flex-col border-r border-gray-100 overflow-hidden bg-white">
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
                          if (file.status === 'uploading' || file.status === 'parsing') {
                            showToast('文件正在处理中，请解析完成后再选定查看分块。');
                            return;
                          }
                          setSelectedFileId(file.id);
                          setSelectedChunkId(null);
                        }}
                        className={clsx(
                          "hover:bg-gray-50/30 transition-colors group cursor-pointer border-l-2",
                          selectedFileId === file.id 
                            ? "border-l-[#2D336B] bg-[#F2F3FC]/30" 
                            : "border-l-transparent bg-transparent"
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
                            <span className={clsx(
                              "truncate font-bold block leading-tight",
                              selectedFileId === file.id ? "text-gray-900" : "text-gray-700"
                            )} title={file.name}>
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
                        </td>

                        {/* 描述 */}
                        <td className="py-3 pr-2 text-gray-400 font-medium truncate max-w-[8rem] align-middle">
                          -
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
                              disabled={file.status === 'uploading'}
                              className="p-1 rounded text-gray-400 hover:text-gray-700 hover:bg-gray-100 disabled:opacity-35 transition-colors border-none bg-transparent cursor-pointer"
                              title="重命名"
                            >
                              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" d="m16.862 4.487 1.687-1.688a1.875 1.875 0 1 1 2.652 2.652L6.83 20.013a4.5 4.5 0 0 1-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 0 1 1.13-1.897l8.932-8.931Zm0 0L19.5 7.125M18 14v4.75A2.25 2.25 0 0 1 15.75 21H5.25A2.25 2.25 0 0 1 3 18.75V8.25A2.25 2.25 0 0 1 5.25 6H10" />
                              </svg>
                            </button>

                            {/* 删除 */}
                            <button 
                              onClick={() => handleDeleteFile(file.id, file.name)}
                              disabled={file.status === 'uploading'}
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
        <div className="w-[45%] flex flex-col overflow-hidden bg-gray-50/30">
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
                点击左侧文件表格行，即可在此区域提取该文档的仿真分块列表
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
                  <p className="text-xs text-gray-600 line-clamp-2 leading-relaxed font-semibold">
                    {chunk.content}
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

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
                <span className="text-[10px] text-gray-400 mt-1 font-semibold truncate max-w-[15rem]" title={selectedChunk.fileName}>
                  来源：{selectedChunk.fileName}
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
                  <div className="text-gray-400 text-[10px] font-bold uppercase tracking-wider">切分时间</div>
                  <div className="text-gray-600 font-bold font-mono mt-1">{selectedChunk.time}</div>
                </div>
              </div>

              <div className="space-y-2 flex flex-col">
                <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">分块原文</span>
                <p className="text-xs text-gray-700 leading-relaxed bg-[#FAFAFA]/80 border border-gray-150 rounded-xl p-4 font-semibold break-words select-text">
                  {selectedChunk.content}
                </p>
              </div>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};
