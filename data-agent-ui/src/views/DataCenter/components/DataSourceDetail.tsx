import React, { useState, useEffect, useMemo } from 'react';
import { ChevronRight } from 'lucide-react';
import clsx from 'clsx';
import type { DataSource } from '../types';
import { MOCK_DB_TABLES } from '../mockData';

interface DataSourceDetailProps {
  selectedItem: DataSource;
}

export const DataSourceDetail: React.FC<DataSourceDetailProps> = ({ selectedItem }) => {
  const [activeTableName, setActiveTableName] = useState<string | null>(null);

  // 默认选中第一个表名
  useEffect(() => {
    const typeKey = selectedItem.type.toLowerCase();
    const tables = MOCK_DB_TABLES[typeKey] || [];
    if (tables.length > 0) {
      setActiveTableName(tables[0].name);
    } else {
      setActiveTableName(null);
    }
  }, [selectedItem]);

  // 根据选中的数据库类型和表名获取数据
  const currentTableMetadata = useMemo(() => {
    const typeKey = selectedItem.type.toLowerCase();
    const tables = MOCK_DB_TABLES[typeKey] || [];
    return tables.find(t => t.name === activeTableName) || null;
  }, [selectedItem, activeTableName]);

  const tablesList = useMemo(() => {
    const typeKey = selectedItem.type.toLowerCase();
    return MOCK_DB_TABLES[typeKey] || [];
  }, [selectedItem]);

  return (
    <div className="h-full w-full flex flex-col overflow-hidden animate-in fade-in duration-300">
      {/* 头部元数据展示卡 */}
      <div className="p-5 border-b border-gray-100 flex-none bg-[#FAFAFA]/50 select-none">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className={clsx(
              "size-10 rounded-lg flex items-center justify-center text-white font-bold text-sm shadow-md",
              selectedItem.type === 'MySQL' && "bg-blue-500",
              selectedItem.type === 'PostgreSQL' && "bg-[#336791]",
              selectedItem.type === 'ClickHouse' && "bg-[#FCA326]"
            )}>
              {selectedItem.type.substring(0, 3).toUpperCase()}
            </div>
            <div>
              <h2 className="text-base font-bold text-gray-800">{selectedItem.name}</h2>
              <p className="text-xs text-gray-400 mt-0.5 font-medium">
                连接主机：{selectedItem.host}:{selectedItem.port} · 数据库：{selectedItem.database}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-green-50 text-green-600 border border-green-200/80">
              <span className="size-1.5 rounded-full bg-green-500 animate-ping"></span>
              连接正常
            </span>
          </div>
        </div>
      </div>

      {/* 数据库样式内容区：左侧表列表，右侧预览表 */}
      <div className="flex-1 flex overflow-hidden">
        <div className="w-56 border-r border-gray-100 flex flex-col flex-none p-3 bg-white select-none">
          <div className="text-xs font-bold text-gray-400 tracking-wider mb-2 px-1 uppercase">数据库表 ({tablesList.length})</div>
          <div className="flex-1 overflow-y-auto flex flex-col gap-0.5 no-scrollbar">
            {tablesList.map(tb => (
              <button
                key={tb.name}
                onClick={() => setActiveTableName(tb.name)}
                className={clsx(
                  "flex items-center justify-between text-left px-2.5 py-1.5 rounded-md text-sm font-semibold transition-all w-full cursor-pointer border-0 bg-transparent",
                  activeTableName === tb.name 
                    ? "bg-blue-50/70 text-[#3A78F2]" 
                    : "text-gray-600 hover:bg-gray-50 hover:text-gray-800"
                )}
              >
                <span className="truncate">{tb.name}</span>
                <ChevronRight className="w-3.5 h-3.5 opacity-60" />
              </button>
            ))}
          </div>
        </div>

        <div className="flex-1 flex flex-col overflow-hidden p-5">
          {currentTableMetadata ? (
            <div className="h-full flex flex-col overflow-hidden">
              <div className="flex-none mb-3 select-none">
                <h3 className="text-sm font-bold text-gray-700">
                  表名：<code className="bg-gray-100 px-1 py-0.5 rounded text-[#2D336B] font-mono">{currentTableMetadata.name}</code>
                </h3>
                <p className="text-xs text-gray-400 mt-1 font-normal">字段元数据及 10 条测试数据采样预览：</p>
              </div>
              
              <div className="flex-1 overflow-auto border border-gray-100 rounded-lg shadow-inner bg-white">
                <table className="w-full text-left border-collapse text-sm">
                  <thead className="sticky top-0 bg-gray-50/80 backdrop-blur-xs z-10 border-b border-gray-100 select-none">
                    <tr>
                      {currentTableMetadata.columns.map(col => (
                        <th key={col.name} className="px-4 py-3 font-bold text-gray-600 border-r border-gray-50 last:border-0">
                          <div className="flex flex-col">
                            <span>{col.name}</span>
                            <span className="text-[9px] text-gray-400 font-normal font-mono">{col.type}</span>
                          </div>
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {currentTableMetadata.data.map((row, idx) => (
                      <tr key={idx} className="hover:bg-gray-50/40 transition-colors">
                        {currentTableMetadata.columns.map(col => (
                          <td key={col.name} className="px-4 py-2.5 text-gray-750 font-medium truncate max-w-50 border-r border-gray-50/55 last:border-0">
                            {row[col.name]}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ) : (
            <div className="text-gray-400 text-xs text-center py-20">正在加载数据源表结构...</div>
          )}
        </div>
      </div>
    </div>
  );
};
