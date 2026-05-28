import React from 'react';

export interface ResultSetTableProps {
  /** 结果集 JSON 字符串（可以是数组，或者包含 resultSet 键的对象） */
  dataJson: string;
}

const extractJsonPayload = (value: string) => {
  const stripped = value
    .replace('$$$result_set', '')
    .replace(/\$\$\$/g, '')
    .trim();
  const objectStart = stripped.indexOf('{');
  const arrayStart = stripped.indexOf('[');
  const starts = [objectStart, arrayStart].filter((pos) => pos >= 0);
  if (starts.length === 0) return stripped;
  const start = Math.min(...starts);
  const open = stripped[start];
  const close = open === '[' ? ']' : '}';
  const end = stripped.lastIndexOf(close);
  return end >= start ? stripped.slice(start, end + 1).trim() : stripped.slice(start).trim();
};

const normalizeResultRows = (columns: string[], data: any[]): any[] => {
  if (!Array.isArray(data)) return [];
  return data.map((row) => {
    if (Array.isArray(row)) {
      return columns.reduce<Record<string, any>>((acc, column, index) => {
        acc[column] = row[index] ?? '';
        return acc;
      }, {});
    }
    return row;
  });
};

const parseResultSetData = (dataJson: string): { columns: string[]; rows: any[] } | null => {
  try {
    const cleanJson = extractJsonPayload(dataJson);
    if (cleanJson.startsWith('[') && cleanJson.endsWith(']')) {
      const rows = JSON.parse(cleanJson);
      if (!Array.isArray(rows) || rows.length === 0) return null;
      const columns = Object.keys(rows[0] || {});
      return columns.length > 0 ? { columns, rows } : null;
    }

    if (!cleanJson.startsWith('{') || !cleanJson.endsWith('}')) return null;

    const parsedObj = JSON.parse(cleanJson);
    const resultSet = parsedObj?.resultSet || parsedObj;
    const rawData = Array.isArray(resultSet?.data) ? resultSet.data : [];
    const rawColumns = Array.isArray(resultSet?.columns) ? resultSet.columns : [];

    const columns = rawColumns.length > 0
      ? rawColumns.map(String)
      : rawData.length > 0 && !Array.isArray(rawData[0])
        ? Object.keys(rawData[0] || {})
        : [];
    const rows = normalizeResultRows(columns, rawData);

    return columns.length > 0 && rows.length > 0 ? { columns, rows } : null;
  } catch (e) {
    return null;
  }
};

/**
 * SQL 查询结果集的数据表格展示组件，支持流式动态编译与异常防御
 */
export const ResultSetTable: React.FC<ResultSetTableProps> = React.memo(({ dataJson }) => {
  const parsed = parseResultSetData(dataJson);
  const rows = parsed?.rows || [];
  const columns = parsed?.columns || [];

  if (rows.length === 0) {
    return (
      <div className="my-2 p-3 text-xs text-gray-400 font-semibold animate-pulse">
        加载数据结果集中...
      </div>
    );
  }

  return (
    <div className="my-1 overflow-hidden rounded-xl border border-gray-150 bg-white w-full select-text shadow-2xs">
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse text-[11px] leading-relaxed">
          <thead>
            <tr className="border-b border-gray-150 bg-gray-50/70 text-gray-500 font-bold uppercase select-none">
              {columns.map((col) => (
                <th key={col} className="px-3 py-2 border-r border-gray-100 last:border-r-0 whitespace-nowrap">{col}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 font-medium text-gray-700">
            {rows.map((row, rIdx) => (
              <tr key={rIdx} className="hover:bg-gray-50/50 transition-colors">
                {columns.map((col) => (
                  <td key={col} className="px-3 py-1.5 border-r border-gray-100 last:border-r-0 whitespace-nowrap">{row[col]}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
});

ResultSetTable.displayName = 'ResultSetTable';
