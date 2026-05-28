import React, { useState } from 'react';

export interface CodeBlockProps {
  /** 代码语言，支持 sql 或 python */
  language: 'sql' | 'python';
  /** 具体的代码内容 */
  code: string;
}

/**
 * SQL/Python 代码展示框，支持代码一键复制与高质感圆角卡片渲染
 */
export const CodeBlock: React.FC<CodeBlockProps> = React.memo(({ language, code }) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="my-1 rounded-xl border border-gray-150 bg-[#FAFAFC] overflow-hidden w-full font-mono text-[12px] leading-relaxed select-text shadow-2xs">
      {/* 头部控制栏 */}
      <div className="flex items-center justify-between px-4 py-2 bg-gray-100/60 border-b border-gray-150 select-none">
        <span className="text-[10px] font-bold text-gray-500 uppercase tracking-wider">{language} 代码</span>
        <button 
          onClick={handleCopy}
          className="text-[10px] text-indigo-650 hover:text-indigo-800 font-bold border border-indigo-200 rounded px-2 py-0.5 hover:bg-indigo-50 cursor-pointer active:scale-95 transition-all"
        >
          {copied ? "已复制" : "复制"}
        </button>
      </div>
      {/* 代码正文 */}
      <pre className="p-4 overflow-x-auto text-gray-700 whitespace-pre">
        <code>{code}</code>
      </pre>
    </div>
  );
});

CodeBlock.displayName = 'CodeBlock';
