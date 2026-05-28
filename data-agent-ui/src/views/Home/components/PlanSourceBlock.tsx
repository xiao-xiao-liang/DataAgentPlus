import React, { useState } from 'react';

export interface PlanSourceBlockProps {
  /** 源码内容 */
  code: string;
  /** 代码语言，默认为 json */
  language?: string;
}

/**
 * 带有复制功能与标签头部的高保真计划源码展示框
 */
export const PlanSourceBlock: React.FC<PlanSourceBlockProps> = React.memo(({ 
  code, 
  language = 'json' 
}) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="w-full text-gray-800 select-text">
      {/* 代码框头部栏 */}
      <div className="text-gray-500 bg-gray-100/80 mt-2 flex items-center gap-4 rounded-t-lg border border-gray-200 px-3 py-1.5 font-semibold text-[11px] select-none">
        <div className="font-bold uppercase tracking-wider">{language}</div>
        <div className="ml-auto flex items-center">
          <button 
            onClick={handleCopy}
            className="inline-flex items-center justify-center gap-1.5 rounded-md hover:bg-gray-200/80 text-gray-500 hover:text-gray-700 text-xs border-none bg-transparent cursor-pointer size-6 p-1 transition-colors"
            type="button"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"></rect><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>
            <span className="font-bold">{copied ? "已复制" : "复制"}</span>
          </button>
        </div>
      </div>
      {/* 代码内容区域 */}
      <div className="mb-2 max-h-60 overflow-auto rounded-b-lg border border-t-0 border-gray-200 bg-white font-mono text-[11px] leading-relaxed p-4 whitespace-pre">
        {code}
      </div>
    </div>
  );
});

PlanSourceBlock.displayName = 'PlanSourceBlock';
