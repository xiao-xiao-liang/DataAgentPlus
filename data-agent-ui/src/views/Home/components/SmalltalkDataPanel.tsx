import React, { useState, useRef } from 'react';
import { ChevronDown, Upload, Check, ChevronRight } from 'lucide-react';
import clsx from 'clsx';

export interface SmalltalkDataPanelProps {
  /** 智能体的回复文本 */
  reply: string;
  /** 上一次用户的请求文本 */
  latestQuery: string;
  /** 点击确认添加数据后的回调函数 */
  onConfirmData: (file: { id: string; name: string; size: string }) => void;
}

/**
 * 闲聊/无关指令友好引导及数据快速添加配置面板组件
 */
export const SmalltalkDataPanel: React.FC<SmalltalkDataPanelProps> = React.memo(({ 
  reply, 
  latestQuery, 
  onConfirmData 
}) => {
  const [isOpen, setIsOpen] = useState(true);
  const [addType, setAddType] = useState<'upload' | 'existing'>('upload');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [dbName, setDbName] = useState('内置_游戏数据');
  const [tableName, setTableName] = useState('内置_游戏数据.csv');
  const localFileRef = useRef<HTMLInputElement>(null);

  const isGreeting = /你好|您好|嗨|hello|hi/i.test(latestQuery);
  const guideText = isGreeting
    ? "请上传您的CSV或Excel文件，或者提供数据库连接信息，以便我开始为您进行智能分析。"
    : "为了能够为您提供精准的数据分析服务，请您上传CSV/Excel等格式的数据集，或者提供安全的数据库连接信息。";

  const handleLocalFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
    }
  };

  const handleConfirm = () => {
    if (addType === 'upload' && selectedFile) {
      const sizeStr = selectedFile.size > 1024 * 1024
        ? `${(selectedFile.size / (1024 * 1024)).toFixed(1)} MB`
        : `${(selectedFile.size / 1024).toFixed(1)} KB`;
      onConfirmData({
        id: `upload-${Date.now()}`,
        name: selectedFile.name,
        size: sizeStr
      });
    } else if (addType === 'existing') {
      onConfirmData({
        id: `existing-${Date.now()}`,
        name: tableName,
        size: '15.4 KB'
      });
    }
  };

  return (
    <div className="w-full flex flex-col items-start select-none">
      {/* 了解用户需求折叠面板 */}
      <div className="bg-white border border-gray-200/80 rounded-xl shadow-3xs my-2 overflow-hidden transition-all duration-200 w-full max-w-[620px]">
        <button 
          onClick={() => setIsOpen(!isOpen)}
          type="button"
          className="w-full flex items-center justify-between px-4 py-3 bg-[#FAFAFC] cursor-pointer hover:bg-gray-150/40 transition-colors border-none outline-none"
        >
          <div className="flex items-center gap-2.5">
            <span className="shrink-0 flex items-center justify-center size-5 rounded-md bg-indigo-50 border border-indigo-100">
              <Check className="size-3 text-indigo-600 stroke-[3.5]" />
            </span>
            <span className="text-xs font-bold text-gray-800">了解用户需求</span>
          </div>
          <ChevronRight className={clsx("w-4 h-4 text-gray-400 transition-transform duration-200", isOpen && "rotate-90")} />
        </button>
        {isOpen && (
          <div className="px-4 pb-4 pt-2.5 bg-white border-t border-gray-100 text-[13px] text-gray-700 leading-relaxed font-normal whitespace-pre-wrap animate-in fade-in duration-150 select-text">
            {reply || "正在思考..."}
          </div>
        )}
      </div>

      {/* 引导上传提示 */}
      {reply && (
        <div className="text-[13px] text-gray-700 font-normal leading-relaxed my-2.5 animate-in fade-in duration-300">
          {guideText}
        </div>
      )}

      {/* 添加数据表单 */}
      {reply && (
        <div className="bg-[#FAFAFC] border border-gray-200/60 rounded-xl p-5 shadow-3xs w-full max-w-[620px] my-1 flex flex-col gap-4 text-xs animate-in fade-in duration-400">
          {/* 添加方式 Radio */}
          <div className="flex flex-col gap-2">
            <span className="text-gray-600 font-bold flex items-center">
              <span className="text-red-500 mr-1">*</span> 添加方式
            </span>
            <div className="flex items-center gap-6 mt-0.5">
              {/* 本地上传 */}
              <button 
                type="button"
                onClick={() => setAddType('upload')}
                className="flex items-center gap-2 text-gray-700 hover:text-indigo-600 transition-all outline-none border-none bg-transparent cursor-pointer font-semibold text-[12px]"
              >
                <span className={clsx(
                  "size-4 rounded-full border flex items-center justify-center transition-all bg-white",
                  addType === 'upload' ? "border-indigo-600 ring-1 ring-indigo-600" : "border-gray-300"
                )}>
                  {addType === 'upload' && <span className="size-2 rounded-full bg-indigo-600 animate-in zoom-in-50 duration-150"></span>}
                </span>
                <span>本地上传</span>
              </button>

              {/* 选择已有数据 */}
              <button 
                type="button"
                onClick={() => setAddType('existing')}
                className="flex items-center gap-2 text-gray-700 hover:text-indigo-600 transition-all outline-none border-none bg-transparent cursor-pointer font-semibold text-[12px]"
              >
                <span className={clsx(
                  "size-4 rounded-full border flex items-center justify-center transition-all bg-white",
                  addType === 'existing' ? "border-indigo-600 ring-1 ring-indigo-600" : "border-gray-300"
                )}>
                  {addType === 'existing' && <span className="size-2 rounded-full bg-indigo-600 animate-in zoom-in-50 duration-150"></span>}
                </span>
                <span>选择已有数据</span>
              </button>
            </div>
          </div>

          {/* 表单主体 */}
          {addType === 'upload' ? (
            <div className="flex flex-col gap-2 animate-in fade-in duration-200">
              <span className="text-gray-600 font-bold">
                <span className="text-red-500 mr-1">*</span> 上传文件
              </span>
              <input 
                type="file" 
                ref={localFileRef} 
                onChange={handleLocalFileChange} 
                className="hidden" 
                accept=".csv,.xlsx,.xls"
              />
              <div 
                onClick={() => localFileRef.current?.click()}
                className="border border-dashed border-gray-300 hover:border-indigo-400 hover:bg-indigo-50/5 rounded-xl py-6 px-4 flex flex-col items-center justify-center gap-2 cursor-pointer transition-all bg-white"
              >
                <Upload className="size-5.5 text-gray-400 shrink-0" />
                {selectedFile ? (
                  <span className="text-indigo-600 font-bold truncate max-w-[280px]">
                    已选择：{selectedFile.name}
                  </span>
                ) : (
                  <div className="text-center space-y-1">
                    <span className="text-gray-600 font-bold block">点击或将文件拖拽至此上传</span>
                    <span className="text-gray-400 text-[10px] block font-medium">支持xlsx、xls、csv格式，文件最大200MB</span>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="flex flex-col gap-4 animate-in fade-in duration-200">
              <div className="flex flex-col gap-2">
                <span className="text-gray-600 font-bold">
                  <span className="text-red-500 mr-1">*</span> 数据库/文件
                </span>
                <div className="relative w-full">
                  <select 
                    value={dbName} 
                    onChange={(e) => {
                      setDbName(e.target.value);
                      if (e.target.value === '内置_游戏数据') {
                        setTableName('内置_游戏数据.csv');
                      } else {
                        setTableName('内置_餐厅数据.csv');
                      }
                    }}
                    className="w-full appearance-none border border-gray-200 bg-white rounded-lg h-9 px-3.5 pr-10 outline-none font-semibold focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 text-gray-700 text-[12px] transition-all cursor-pointer shadow-3xs"
                  >
                    <option value="内置_游戏数据">内置_游戏数据</option>
                    <option value="内置_餐厅数据">内置_餐厅数据</option>
                  </select>
                  <ChevronDown className="absolute right-3.5 top-2.5 size-4 text-gray-400 pointer-events-none" />
                </div>
              </div>

              <div className="flex flex-col gap-2">
                <span className="text-gray-600 font-bold">
                  <span className="text-red-500 mr-1">*</span> 表/文件
                </span>
                <div className="relative w-full">
                  <select 
                    value={tableName} 
                    onChange={(e) => setTableName(e.target.value)}
                    className="w-full appearance-none border border-gray-200 bg-white rounded-lg h-9 px-3.5 pr-10 outline-none font-semibold focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 text-gray-700 text-[12px] transition-all cursor-pointer shadow-3xs"
                  >
                    {dbName === '内置_游戏数据' ? (
                      <option value="内置_游戏数据.csv">内置_游戏数据.csv</option>
                    ) : (
                      <option value="内置_餐厅数据.csv">内置_餐厅数据.csv</option>
                    )}
                  </select>
                  <ChevronDown className="absolute right-3.5 top-2.5 size-4 text-gray-400 pointer-events-none" />
                </div>
              </div>
            </div>
          )}

          {/* 确认按钮 */}
          <div className="flex justify-end mt-1">
            <button 
              onClick={handleConfirm}
              className="px-4 py-2 rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 font-bold tracking-wide transition-all shadow-sm active:scale-95 cursor-pointer"
            >
              确认添加
            </button>
          </div>
        </div>
      )}
    </div>
  );
});

SmalltalkDataPanel.displayName = 'SmalltalkDataPanel';
