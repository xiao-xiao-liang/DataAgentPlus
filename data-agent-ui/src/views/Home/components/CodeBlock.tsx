import React, { useState } from 'react';
import { ChevronDown, ChevronRight, Copy, WrapText } from 'lucide-react';
import clsx from 'clsx';
import {
  normalizeCodeForDisplay,
  tokenizeCodeForDisplay,
  type CodeLanguage,
  type CodeToken,
} from '../workflowDisplay';

export interface CodeBlockProps {
  /** 代码语言，支持 sql 或 python */
  language: CodeLanguage;
  /** 具体的代码内容 */
  code: string;
  defaultOpen?: boolean;
}

const tokenClassName: Record<CodeToken['type'], string> = {
  plain: 'text-gray-700',
  keyword: 'text-indigo-650 font-semibold',
  string: 'text-emerald-700',
  number: 'text-amber-700',
  comment: 'text-gray-400 italic',
  operator: 'text-sky-700',
};

/**
 * SQL/Python 代码展示框，支持代码一键复制与高质感圆角卡片渲染
 */
const getLanguageLabel = (language: CodeLanguage) => {
  if (language === 'json') return 'JSON';
  return language === 'python' ? 'Python' : 'SQL';
};

export const CodeBlock: React.FC<CodeBlockProps> = React.memo(({ language, code, defaultOpen = false }) => {
  const [copied, setCopied] = useState(false);
  const [isOpen, setIsOpen] = useState(defaultOpen);
  const [isWrapped, setIsWrapped] = useState(false);
  const displayCode = normalizeCodeForDisplay(code, language);
  const tokenLines = tokenizeCodeForDisplay(code, language);
  const languageLabel = getLanguageLabel(language);

  const handleCopy = () => {
    navigator.clipboard.writeText(displayCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <div className="my-1 w-full overflow-hidden rounded-lg border border-gray-200 bg-[#F6F7F9] font-mono text-[12px] leading-relaxed shadow-2xs select-text">
      <div className={clsx(
        'flex min-h-12 items-center gap-3 px-3 py-2 select-none',
        isOpen && 'border-b border-gray-200'
      )}>
        <button
          type="button"
          onClick={() => setIsOpen(prev => !prev)}
          aria-expanded={isOpen}
          className="inline-flex min-w-0 flex-1 items-center gap-2 border-0 bg-transparent p-0 text-left text-gray-600 cursor-pointer"
        >
          {isOpen ? <ChevronDown className="size-4 shrink-0" /> : <ChevronRight className="size-4 shrink-0" />}
          <span className="truncate text-[13px] font-medium">代码块</span>
        </button>
        <div className="ml-auto flex items-center gap-2 text-gray-500">
          <span className="text-[13px] font-medium">{languageLabel}</span>
          <span className="h-5 w-px bg-gray-300" />
          <button
            type="button"
            onClick={() => setIsWrapped(prev => !prev)}
            className={clsx(
              'inline-flex h-8 items-center gap-1.5 rounded-md px-2 text-[12px] font-medium transition-colors cursor-pointer',
              isWrapped ? 'bg-white text-gray-800 shadow-2xs' : 'text-gray-500 hover:bg-white hover:text-gray-800'
            )}
            aria-pressed={isWrapped}
            title="自动换行"
          >
            <WrapText className="size-4" />
            <span>自动换行</span>
          </button>
          <button
            type="button"
            onClick={handleCopy}
            className="inline-flex h-8 items-center gap-1.5 rounded-md px-2 text-[12px] font-medium text-gray-500 transition-colors hover:bg-white hover:text-gray-800 cursor-pointer"
            title="复制代码"
          >
            <Copy className="size-4" />
            <span>{copied ? "已复制" : "复制"}</span>
          </button>
        </div>
      </div>
      {isOpen && (
        <pre className={clsx(
          'max-h-[520px] overflow-auto p-4 text-[12px] leading-relaxed',
          isWrapped ? 'whitespace-pre-wrap break-words' : 'whitespace-pre'
        )}>
          <code>
            {tokenLines.map((line, lineIndex) => (
              <React.Fragment key={`line-${lineIndex}`}>
                {line.map((token, tokenIndex) => (
                  <span key={`${lineIndex}-${tokenIndex}`} className={tokenClassName[token.type]}>
                    {token.text}
                  </span>
                ))}
                {lineIndex < tokenLines.length - 1 && '\n'}
              </React.Fragment>
            ))}
          </code>
        </pre>
      )}
    </div>
  );
});

CodeBlock.displayName = 'CodeBlock';
