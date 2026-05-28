import React, { useEffect, useMemo, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

export interface MarkdownParserProps {
  content: string;
}

type MarkdownPart =
  | { type: 'markdown'; content: string; key: string }
  | { type: 'echarts'; code: string; key: string };

const isFenceLine = (line: string) => /^\s*```/.test(line);
const isTableLine = (line: string) => {
  const trimmed = line.trim();
  return trimmed.startsWith('|') && trimmed.endsWith('|');
};
const isListItemLine = (line: string) => /^\s*(?:[-*+]|\d+\.)\s+/.test(line);

const isIndentedListContinuation = (line: string) => /^\s{2,}\S/.test(line);

const compactListSeparators = (lines: string[]) => {
  return lines.filter((line, index) => {
    if (line.trim() !== '') return true;

    const previous = lines[index - 1] || '';
    const next = lines[index + 1] || '';
    return !(isIndentedListContinuation(previous) && isListItemLine(next));
  });
};

const normalizeMarkdownContent = (value: string) => {
  const normalized = value
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .replace(/\\n/g, '\n');

  let inFence = false;

  const lines = normalized
    .split('\n')
    .map((line) => {
      if (isFenceLine(line)) {
        inFence = !inFence;
        return line.trimEnd();
      }

      if (inFence || isTableLine(line)) {
        return line;
      }

      return line
        .replace(/^(#{1,6})([^\s#])/g, '$1 $2')
        .trimEnd();
    });

  return compactListSeparators(lines)
    .join('\n')
    .trim();
};

const hashString = (value: string) => {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = ((hash << 5) - hash + value.charCodeAt(index)) | 0;
  }
  return Math.abs(hash).toString(36);
};

const splitMarkdownByEcharts = (content: string): MarkdownPart[] => {
  const parts: MarkdownPart[] = [];
  const chartRegex = /```echarts\s*\n?([\s\S]*?)```/gi;
  let lastIndex = 0;
  let chartIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = chartRegex.exec(content)) !== null) {
    if (match.index > lastIndex) {
      parts.push({
        type: 'markdown',
        content: content.slice(lastIndex, match.index),
        key: `markdown-${parts.length}`,
      });
    }

    const code = match[1].trim();
    parts.push({
      type: 'echarts',
      code,
      key: `echarts-${chartIndex}-${hashString(code)}`,
    });
    chartIndex += 1;
    lastIndex = match.index + match[0].length;
  }

  if (lastIndex < content.length) {
    parts.push({
      type: 'markdown',
      content: content.slice(lastIndex),
      key: `markdown-${parts.length}`,
    });
  }

  return parts.length > 0 ? parts : [{ type: 'markdown', content, key: 'markdown-0' }];
};

const parseEchartsOption = (code: string) => {
  try {
    return JSON.parse(code);
  } catch {
    return new Function(`"use strict"; return (${code});`)();
  }
};

const parseArrayLiteral = (value?: string) => {
  if (!value) return null;
  try {
    return JSON.parse(value);
  } catch {
    try {
      return new Function(`"use strict"; return (${value});`)();
    } catch {
      return null;
    }
  }
};

const extractEchartsText = (code: string, pattern: RegExp) => {
  const match = code.match(pattern);
  return match?.[1]?.trim();
};

const createFallbackEchartsOption = (code: string) => {
  const categories = parseArrayLiteral(
    extractEchartsText(code, /"xAxis"\s*:\s*\{[\s\S]*?"data"\s*:\s*(\[[\s\S]*?\])/)
  );
  const values = Array.from(code.matchAll(/"value"\s*:\s*(-?\d+(?:\.\d+)?)/g))
    .map((match) => Number(match[1]))
    .filter((value) => Number.isFinite(value));

  if (values.length === 0) return null;

  const labels = Array.isArray(categories) && categories.length > 0
    ? categories.map(String).slice(0, values.length)
    : values.map((_, index) => `Item ${index + 1}`);

  return {
    title: {
      text: extractEchartsText(code, /"title"\s*:\s*\{[\s\S]*?"text"\s*:\s*"([^"]+)"/) || '',
    },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value' },
    series: [
      {
        name: extractEchartsText(code, /"name"\s*:\s*"([^"]+)"/) || 'value',
        type: code.includes('"type": "line"') ? 'line' : 'bar',
        data: values,
      },
    ],
  };
};

const getEchartsOption = (code: string) => {
  try {
    return parseEchartsOption(code);
  } catch {
    return createFallbackEchartsOption(code);
  }
};

const isBlankTextNode = (child: React.ReactNode) => (
  typeof child === 'string' && child.trim() === ''
);

const renderListItemChildren = (children: React.ReactNode) => {
  const nodes = React.Children.toArray(children);
  const meaningfulNodes = nodes.filter((child) => !isBlankTextNode(child));

  if (
    meaningfulNodes.length === 1 &&
    React.isValidElement<{ children?: React.ReactNode }>(meaningfulNodes[0]) &&
    meaningfulNodes[0].type === 'p'
  ) {
    return (
      <span className="inline text-xs text-gray-650 leading-relaxed font-medium whitespace-pre-wrap">
        {meaningfulNodes[0].props.children}
      </span>
    );
  }

  return children;
};

const EchartsBlock: React.FC<{ code: string; chartKey: string }> = React.memo(({ code, chartKey }) => {
  const chartRef = useRef<HTMLDivElement | null>(null);
  const [error, setError] = useState<string | null>(null);
  const chartOption = useMemo(() => getEchartsOption(code.trim()), [code]);

  useEffect(() => {
    if (!chartRef.current || !chartOption) return undefined;

    let disposed = false;
    let chart: any = null;

    const renderChart = async () => {
      try {
        const echarts = await import('echarts');
        if (disposed || !chartRef.current) return;

        chart = echarts.init(chartRef.current);
        chart.setOption(chartOption, true);
        setError(null);

        const resize = () => chart?.resize();
        window.addEventListener('resize', resize);

        return () => {
          window.removeEventListener('resize', resize);
          chart?.dispose();
        };
      } catch (err) {
        setError(err instanceof Error ? err.message : '图表配置解析失败');
      }
    };

    let cleanup: (() => void) | undefined;
    renderChart().then((dispose) => {
      cleanup = dispose;
    });

    return () => {
      disposed = true;
      cleanup?.();
      chart?.dispose();
    };
  }, [chartOption]);

  return (
    <div className="my-4 w-full overflow-hidden rounded-lg border border-gray-150 bg-white shadow-3xs">
      <div className="flex items-center justify-between border-b border-gray-100 bg-gray-50/80 px-3 py-2 text-[11px] font-bold text-gray-500">
        <span>ECharts 图表</span>
        <span className="font-mono uppercase text-gray-400">echarts</span>
      </div>
      <div
        ref={chartRef}
        data-testid="echarts-chart"
        data-chart-key={chartKey}
        data-chart-option={chartOption ? JSON.stringify(chartOption) : ''}
        className="h-[320px] w-full"
        aria-label="ECharts 图表"
      />
      {error && (
        <div className="border-t border-red-100 bg-red-50 px-3 py-2 text-xs font-medium text-red-600">
          图表渲染失败：{error}
        </div>
      )}
    </div>
  );
});

EchartsBlock.displayName = 'EchartsBlock';

export const MarkdownParser: React.FC<MarkdownParserProps> = React.memo(({ content }) => {
  if (!content) return null;

  const cleanContent = normalizeMarkdownContent(content);
  const parts = splitMarkdownByEcharts(cleanContent);

  return (
    <div className="space-y-1 select-text w-full">
      {parts.map((part) => (
        part.type === 'echarts' ? (
          <EchartsBlock key={part.key} chartKey={part.key} code={part.code} />
        ) : (
          <ReactMarkdown
            key={part.key}
            remarkPlugins={[remarkGfm]}
            components={{
          pre: ({ children }) => <>{children}</>,
          h1: ({ children }) => (
            <h1 className="text-base font-bold text-gray-900 border-b border-gray-200 pb-1.5 mt-5 mb-3 tracking-tight">{children}</h1>
          ),
          h2: ({ children }) => (
            <h2 className="text-[14px] font-bold text-gray-800 border-b border-gray-100 pb-1 mt-4 mb-2">{children}</h2>
          ),
          h3: ({ children }) => (
            <h3 className="text-[13px] font-bold text-gray-700 mt-3 mb-1.5">{children}</h3>
          ),
          p: ({ children }) => (
            <p className="text-xs text-gray-650 leading-relaxed font-medium my-1.5 whitespace-pre-wrap">{children}</p>
          ),
          ul: ({ children }) => (
            <ul className="list-disc pl-5 my-2.5 space-y-1 text-xs text-gray-655 leading-relaxed font-medium">{children}</ul>
          ),
          ol: ({ children }) => (
            <ol className="list-decimal pl-5 my-2.5 space-y-1 text-xs text-gray-655 leading-relaxed font-medium">{children}</ol>
          ),
          li: ({ children }) => (
            <li className="leading-relaxed whitespace-pre-wrap">{renderListItemChildren(children)}</li>
          ),
          a: ({ href, children }) => (
            <a href={href} target="_blank" rel="noopener noreferrer" className="text-indigo-650 hover:underline font-bold">{children}</a>
          ),
          table: ({ children }) => (
            <div className="my-3 overflow-x-auto rounded-lg border border-gray-150 shadow-3xs w-full">
              <table className="w-full text-left border-collapse text-xs leading-normal">{children}</table>
            </div>
          ),
          thead: ({ children }) => (
            <thead className="bg-gray-50/80 border-b border-gray-150 font-bold text-gray-700 select-none">{children}</thead>
          ),
          tbody: ({ children }) => (
            <tbody className="divide-y divide-gray-100">{children}</tbody>
          ),
          tr: ({ children }) => (
            <tr className="hover:bg-gray-50/30 transition-colors">{children}</tr>
          ),
          th: ({ children }) => (
            <th className="px-3 py-2 border-r border-gray-100 last:border-r-0 whitespace-nowrap">{children}</th>
          ),
          td: ({ children }) => (
            <td className="px-3 py-1.5 border-r border-gray-100 last:border-r-0 whitespace-nowrap font-medium text-gray-655">{children}</td>
          ),
          code: ({ className, children, ...props }) => {
            const match = /language-(\w+)/.exec(className || '');
            const lang = match?.[1]?.toLowerCase();
            const codeStr = String(children).replace(/\n$/, '');

            if (!lang) {
              return (
                <code className="px-1 py-0.5 bg-gray-150/70 text-indigo-750 font-mono rounded text-[11px]" {...props}>
                  {children}
                </code>
              );
            }

            if (lang === 'echarts') {
              return <EchartsBlock chartKey={`inline-${hashString(codeStr)}`} code={codeStr} />;
            }

            return (
              <div className="my-2 p-3 bg-gray-50 border border-gray-150 rounded-lg text-xs font-mono text-gray-500 shadow-3xs w-full">
                <div className="flex items-center justify-between mb-1.5 pb-1 border-b border-gray-150 text-[10px] text-gray-400 font-bold select-none uppercase tracking-wide">
                  <span>代码片段 ({lang.toUpperCase()})</span>
                </div>
                <pre className="overflow-x-auto whitespace-pre pr-1 max-h-48 scrollbar-thin text-[11px] leading-relaxed select-text">{codeStr}</pre>
              </div>
            );
          }
            }}
          >
            {part.content}
          </ReactMarkdown>
        )
      ))}
    </div>
  );
});

MarkdownParser.displayName = 'MarkdownParser';
