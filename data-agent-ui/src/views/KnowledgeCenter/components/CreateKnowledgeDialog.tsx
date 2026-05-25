import React, { useState, useRef } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { X, UploadCloud, FileText, Maximize2 } from 'lucide-react';
import clsx from 'clsx';

interface CreateKnowledgeDialogProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: (
    name: string, 
    description: string, 
    initialFile: { name: string; size: string; status: 'success' } | null
  ) => void;
}

export const CreateKnowledgeDialog: React.FC<CreateKnowledgeDialogProps> = ({
  isOpen,
  onOpenChange,
  onConfirm,
}) => {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  
  // 初始化导入相关状态 (二选一，选填)
  const [inputMode, setInputMode] = useState<'file' | 'text'>('file');
  const [selectedFile, setSelectedFile] = useState<{ name: string; size: string } | null>(null);
  const [textInput, setTextInput] = useState('');
  
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isDragOver, setIsDragOver] = useState(false);

  // 放大编辑大弹窗状态
  const [isMaximized, setIsMaximized] = useState(false);

  // 格式化文件大小
  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  // 处理本地文件选择
  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      const file = files[0];
      setSelectedFile({
        name: file.name,
        size: formatFileSize(file.size)
      });
      // 一旦选择了文件，清空文本录入以保证二选一
      setTextInput('');
    }
  };

  // 拖拽文件进入
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
    const files = e.dataTransfer.files;
    if (files && files.length > 0) {
      const file = files[0];
      setSelectedFile({
        name: file.name,
        size: formatFileSize(file.size)
      });
      setTextInput('');
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    let initialFileObj: { name: string; size: string; status: 'success' } | null = null;
    
    // 根据当前激活的 Tab 确定初始载入文件
    if (inputMode === 'file' && selectedFile) {
      initialFileObj = {
        name: selectedFile.name,
        size: selectedFile.size,
        status: 'success'
      };
    } else if (inputMode === 'text' && textInput.trim()) {
      const bytes = new Blob([textInput]).size;
      const sizeStr = bytes > 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${bytes} Bytes`;
      initialFileObj = {
        name: '手动录入文本.txt',
        size: sizeStr,
        status: 'success'
      };
    }

    onConfirm(name.trim(), description.trim(), initialFileObj);
    
    // 重置状态
    setName('');
    setDescription('');
    setInputMode('file');
    setSelectedFile(null);
    setTextInput('');
    setIsMaximized(false);
  };

  // 切换选项卡时，自动清除另一边的输入，实现强二选一规则
  const handleModeChange = (mode: 'file' | 'text') => {
    setInputMode(mode);
    if (mode === 'file') {
      setTextInput('');
    } else {
      setSelectedFile(null);
    }
  };

  return (
    <>
      <Dialog.Root open={isOpen} onOpenChange={onOpenChange}>
        <Dialog.Portal>
          {/* 背景遮罩 */}
          <Dialog.Overlay className="fixed inset-0 bg-black/40 z-50 backdrop-blur-xs animate-overlayShow" />
          
          {/* 弹窗核心区域 */}
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <Dialog.Content className="bg-white border border-gray-200 rounded-2xl shadow-xl p-8 w-170 max-h-[95vh] overflow-y-auto relative animate-contentShow text-left flex flex-col focus:outline-none font-sans">
              
              {/* 顶部标题栏 */}
              <div className="flex items-center justify-between mb-4.5">
                <Dialog.Title className="text-lg font-bold text-gray-900 leading-none">
                  新建知识库
                </Dialog.Title>
                <Dialog.Close className="rounded-md hover:bg-gray-100 text-gray-400 hover:text-gray-600 p-1.5 transition-colors border-none bg-transparent cursor-pointer">
                  <X className="h-4.5 w-4.5" />
                  <span className="sr-only">关闭</span>
                </Dialog.Close>
              </div>

              {/* 表单输入区 */}
              <form onSubmit={handleSubmit} className="space-y-5">

                {/* 知识库名称 */}
                <div className="space-y-2">
                  <label className="text-sm font-bold text-gray-700 flex items-center gap-1">
                    <span className="text-red-500">*</span>知识库名称
                  </label>
                  <div className="relative flex items-center">
                    <input 
                      type="text" 
                      maxLength={20}
                      placeholder="请输入知识库名称，例如：我的核心产品知识库"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      className="w-full rounded-lg border border-gray-200 bg-white pl-3.5 pr-14 py-2.5 text-sm focus:outline-none focus:border-gray-400 focus:ring-1 focus:ring-[#2D336B] text-gray-800 placeholder-gray-400 h-10 transition-all"
                      required
                    />
                    <span className="absolute right-3.5 text-xs text-gray-400 font-mono pointer-events-none">
                      {name.length}/20
                    </span>
                  </div>
                </div>

                {/* 描述说明 */}
                <div className="space-y-2">
                  <label className="text-sm font-bold text-gray-700 flex items-center gap-1">
                    描述
                  </label>
                  <div className="relative">
                    <textarea 
                      maxLength={100}
                      placeholder="请提供该知识库的补充描述（选填，用于帮助 Agent 识别数据范围）。"
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                      className="flex w-full rounded-lg border border-gray-200 bg-white px-3.5 pt-2.5 pb-8 text-sm focus:outline-none focus:border-gray-400 focus:ring-1 focus:ring-[#2D336B] text-gray-800 placeholder-gray-400 min-h-20 resize-none transition-all"
                    />
                    <span className="absolute right-3.5 bottom-2.5 text-xs text-gray-400 font-mono pointer-events-none">
                      {description.length}/100
                    </span>
                  </div>
                </div>

                {/* 初始化文档数据（二选一，选填） */}
                <div className="space-y-3 border-t border-gray-100 pt-4">
                  <div className="flex items-center justify-between">
                    <label className="text-sm font-bold text-gray-700">
                      导入初始文档 (可选)
                    </label>
                    
                    {/* 胶囊式二选一 Tab 切换 */}
                    <div className="flex bg-gray-100 p-0.5 rounded-lg w-fit text-xs font-bold">
                      <button 
                        type="button" 
                        onClick={() => handleModeChange('file')}
                        className={clsx(
                          "px-3 py-1 rounded-md transition-colors border-none cursor-pointer outline-none",
                          inputMode === 'file' ? "bg-white shadow-2xs text-gray-800" : "text-gray-400 hover:text-gray-600 bg-transparent"
                        )}
                      >
                        本地文件
                      </button>
                      <button 
                        type="button" 
                        onClick={() => handleModeChange('text')}
                        className={clsx(
                          "px-3 py-1 rounded-md transition-colors border-none cursor-pointer outline-none",
                          inputMode === 'text' ? "bg-white shadow-2xs text-gray-800" : "text-gray-400 hover:text-gray-600 bg-transparent"
                        )}
                      >
                        纯文本录入
                      </button>
                    </div>
                  </div>

                  {/* 文件上传区域 */}
                  {inputMode === 'file' && (
                    <div className="animate-in fade-in duration-150">
                      <input 
                        type="file"
                        ref={fileInputRef}
                        onChange={handleFileSelect}
                        className="hidden"
                        accept=".txt,.pdf,.doc,.docx,.xls,.xlsx,.csv,.md"
                      />
                      
                      {!selectedFile ? (
                        <div 
                          onDragOver={handleDragOver}
                          onDragLeave={handleDragLeave}
                          onDrop={handleDrop}
                          onClick={() => fileInputRef.current?.click()}
                          className={clsx(
                            "border border-dashed rounded-lg p-5 flex flex-col items-center justify-center text-center cursor-pointer transition-all bg-gray-50/50 hover:bg-indigo-50/5",
                            isDragOver ? "border-[#2D336B] bg-indigo-50/15" : "border-gray-200 hover:border-[#2D336B]"
                          )}
                        >
                          <UploadCloud className="h-7 w-7 text-gray-400 mb-1.5" />
                          <span className="text-sm font-bold text-gray-700">点击或拖拽文件进行初始上传</span>
                          <span className="text-xs text-gray-400 mt-1">支持 TXT, PDF, Excel, CSV 等</span>
                        </div>
                      ) : (
                        <div className="flex items-center justify-between p-3.5 px-4 bg-indigo-50/20 border border-indigo-100 rounded-lg">
                          <div className="flex items-center gap-2.5 overflow-hidden flex-1 mr-3">
                            <FileText className="size-5 text-[#2D336B] shrink-0" />
                            <div className="min-w-0 flex-1">
                              <div className="text-sm font-bold text-gray-700 truncate">{selectedFile.name}</div>
                              <div className="text-xs text-gray-400 font-mono mt-0.5">{selectedFile.size}</div>
                            </div>
                          </div>
                          <button
                            type="button"
                            onClick={() => setSelectedFile(null)}
                            className="p-1 rounded-md text-gray-400 hover:text-gray-600 hover:bg-gray-100 border-none bg-transparent cursor-pointer"
                          >
                            <X className="size-4" />
                          </button>
                        </div>
                      )}
                    </div>
                  )}

                  {/* 文本录入区域 */}
                  {inputMode === 'text' && (
                    <div className="animate-in fade-in duration-150 relative">
                      <textarea 
                        maxLength={10000}
                        placeholder="请在此处输入或粘贴纯文本知识内容..."
                        value={textInput}
                        onChange={(e) => setTextInput(e.target.value)}
                        className="flex w-full rounded-lg border border-gray-200 bg-white pl-3.5 pr-10 pt-8 pb-8 text-sm focus:outline-none focus:border-gray-400 focus:ring-1 focus:ring-[#2D336B] text-gray-700 placeholder-gray-400 min-h-24 resize-none transition-all"
                      />
                      {/* 右上角常驻放大按钮 */}
                      <button
                        type="button"
                        onClick={() => setIsMaximized(true)}
                        className="absolute right-3.5 top-3 p-1 rounded hover:bg-gray-100 text-gray-400 hover:text-[#2D336B] transition-colors border-none bg-transparent cursor-pointer flex items-center justify-center"
                        title="放大编辑"
                      >
                        <Maximize2 className="size-4 text-gray-400" />
                      </button>
                      {/* 右下角字数统计 */}
                      <span className="absolute right-3.5 bottom-2.5 text-xs text-gray-400 font-mono pointer-events-none">
                        {textInput.length}/10000
                      </span>
                    </div>
                  )}

                </div>

                {/* 底部操作区 */}
                <div className="flex justify-end items-center gap-3 pt-4 border-t border-gray-100 mt-3">
                  <Dialog.Close asChild>
                    <button 
                      type="button"
                      className="inline-flex items-center justify-center rounded-lg text-sm font-bold bg-gray-100 hover:bg-gray-200/80 active:scale-95 text-gray-600 h-9 px-5 border-none cursor-pointer transition-all"
                    >
                      取消
                    </button>
                  </Dialog.Close>
                  <button 
                    type="submit"
                    disabled={!name.trim()}
                    className={clsx(
                      "inline-flex items-center justify-center rounded-lg text-sm font-bold h-9 px-5 border-none transition-all shadow-sm cursor-pointer",
                      !name.trim()
                        ? "bg-gray-100 text-gray-400 cursor-not-allowed"
                        : "bg-[#151517] text-white hover:bg-gray-900 active:scale-95"
                    )}
                  >
                    确定
                  </button>
                </div>

              </form>
            </Dialog.Content>
          </div>
        </Dialog.Portal>
      </Dialog.Root>

      {/* 嵌套在最上层的大文本放大编辑器弹窗 */}
      <Dialog.Root open={isMaximized} onOpenChange={setIsMaximized}>
        <Dialog.Portal>
          {/* 超高层级 z-[60] */}
          <Dialog.Overlay className="fixed inset-0 bg-black/40 z-60 backdrop-blur-xs animate-overlayShow" />
          <div className="fixed inset-0 z-60 flex items-center justify-center p-4">
            <Dialog.Content className="bg-white border border-gray-200 rounded-2xl shadow-2xl p-6 w-200 h-[80vh] flex flex-col relative animate-contentShow text-left focus:outline-none font-sans">
              
              {/* 标题栏 */}
              <div className="flex items-center justify-between mb-4">
                <Dialog.Title className="text-base font-bold text-gray-900 leading-none">
                  添加文本
                </Dialog.Title>
                <Dialog.Close className="rounded-md hover:bg-gray-100 text-gray-400 hover:text-gray-600 p-1.5 transition-colors border-none bg-transparent cursor-pointer">
                  <X className="h-4.5 w-4.5" />
                  <span className="sr-only">关闭</span>
                </Dialog.Close>
              </div>

              {/* 巨型输入框 */}
              <div className="flex-1 relative min-h-0">
                <textarea
                  maxLength={10000}
                  placeholder="请在此处输入或粘贴纯文本知识内容..."
                  value={textInput}
                  onChange={(e) => setTextInput(e.target.value)}
                  className="w-full h-full rounded-lg border border-gray-200 bg-white p-4 pb-10 text-sm focus:outline-none focus:border-gray-400 focus:ring-1 focus:ring-[#2D336B] text-gray-700 placeholder-gray-400 resize-none transition-all overflow-y-auto"
                />
                {/* 右下角字数统计 */}
                <span className="absolute right-4 bottom-3 text-xs text-gray-400 font-mono pointer-events-none">
                  {textInput.length}/10000
                </span>
              </div>

            </Dialog.Content>
          </div>
        </Dialog.Portal>
      </Dialog.Root>
    </>
  );
};
