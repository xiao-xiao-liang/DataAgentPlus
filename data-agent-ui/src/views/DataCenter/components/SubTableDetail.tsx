import React, { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import * as Tabs from '@radix-ui/react-tabs';
import * as Dialog from '@radix-ui/react-dialog';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import { 
  ChevronRight, 
  Database, 
  Sheet, 
  Copy, 
  Star, 
  Plus, 
  HelpCircle, 
  SquarePen, 
  Search, 
  RefreshCw, 
  ChevronsUpDown,
  X 
} from 'lucide-react';
import clsx from 'clsx';
import type { UploadedFile } from '../types';

interface SubTableDetailProps {
  file: UploadedFile;
  subTableName: string;
  onBackToFile: () => void;
  setToastMessage: (msg: string | null) => void;
  customColumnDescriptions: Record<string, string>;
  setCustomColumnDescriptions: React.Dispatch<React.SetStateAction<Record<string, string>>>;
}

export const SubTableDetail: React.FC<SubTableDetailProps> = ({
  file,
  subTableName,
  onBackToFile,
  setToastMessage,
  customColumnDescriptions,
  setCustomColumnDescriptions
}) => {
  const navigate = useNavigate();

  // Tab 状态
  const [subTableActiveTab, setSubTableActiveTab] = useState<'COLUMNS' | 'PREVIEW'>('COLUMNS');

  // 列信息检索与分页
  const [columnSearchQuery, setColumnSearchQuery] = useState('');
  const [columnCurrentPage, setColumnCurrentPage] = useState(1);
  const [columnPageSize, setColumnPageSize] = useState(10);

  // 编辑字段状态
  const [editingColumn, setEditingColumn] = useState<{
    fileId: string;
    columnName: string;
    originalDesc: string;
    currentDesc: string;
  } | null>(null);

  const subTableDesc = useMemo(() => {
    if (file.name.includes('餐厅')) {
      return '存储不同地域、不同餐品类别的销售和评分';
    } else if (file.name.includes('游戏')) {
      return '存储不同地域、不同游戏类别的游戏销售和评分';
    } else if (file.name.includes('信用卡')) {
      return '存储信用卡客户基本属性、授信额度和还款明细数据';
    } else {
      return '存储子数据表的具体列及行级数据';
    }
  }, [file.name]);

  // 列数据
  const allColumns = useMemo(() => {
    if (file.name.includes('餐厅')) {
      return [
        { name: 'Store_ID', desc: '门店ID', type: 'STRING', time: '2025-01-01 00:00:00' },
        { name: 'Product_Category', desc: '餐品类别', type: 'STRING', time: '2025-01-01 00:00:00' },
        { name: 'Sales_Amount', desc: '销售额', type: 'NUMBER', time: '2025-01-01 00:00:00' },
        { name: 'Rating', desc: '评分', type: 'NUMBER', time: '2025-01-01 00:00:00' },
        { name: 'Order_Date', desc: '订单日期', type: 'STRING', time: '2025-01-01 00:00:00' },
      ];
    } else {
      return [
        { name: 'Name', desc: '游戏名称', type: 'STRING', time: '2025-01-01 00:00:00' },
        { name: 'Platform', desc: '平台', type: 'STRING', time: '2025-01-01 00:00:00' },
        { name: 'Year_of_Release', desc: '发行年份', type: 'NUMBER', time: '2025-01-01 00:00:00' },
        { name: 'Genre', desc: '类型', type: 'STRING', time: '2025-01-01 00:00:00' },
        { name: 'Publisher', desc: '发行商', type: 'STRING', time: '2025-01-01 00:00:00' },
        { name: 'NA_Sales', desc: '北美销量，单位（百万份）', type: 'NUMBER', time: '2025-01-01 00:00:00' },
        { name: 'EU_Sales', desc: '欧洲销量，单位（百万份）', type: 'NUMBER', time: '2025-01-01 00:00:00' },
        { name: 'JP_Sales', desc: '日本销量，单位（百万份）', type: 'NUMBER', time: '2025-01-01 00:00:00' },
        { name: 'Other_Sales', desc: '其他地区销量，单位（百万份）', type: 'NUMBER', time: '2025-01-01 00:00:00' },
        { name: 'Global_Sales', desc: '全球总销量，单位（百万份）', type: 'NUMBER', time: '2025-01-01 00:00:00' },
        { name: 'Critic_Score', desc: '媒体评分', type: 'NUMBER', time: '2025-01-01 00:00:00' },
        { name: 'Critic_Count', desc: '参与评分的媒体数量', type: 'NUMBER', time: '2025-01-01 00:00:00' },
        { name: 'User_Score', desc: '用户评分', type: 'NUMBER', time: '2025-01-01 00:00:00' },
        { name: 'User_Count', desc: '参与评分的用户数量', type: 'NUMBER', time: '2025-01-01 00:00:00' },
        { name: 'Developer', desc: '开发商', type: 'STRING', time: '2025-01-01 00:00:00' },
        { name: 'Rating', desc: '分级', type: 'STRING', time: '2025-01-01 00:00:00' },
      ];
    }
  }, [file.name]);

  // 过滤列数据
  const filteredColumns = useMemo(() => {
    return allColumns.filter(col => 
      col.name.toLowerCase().includes(columnSearchQuery.toLowerCase()) || 
      col.desc.toLowerCase().includes(columnSearchQuery.toLowerCase())
    );
  }, [allColumns, columnSearchQuery]);

  // 分页计算
  const totalPages = useMemo(() => {
    return Math.ceil(filteredColumns.length / columnPageSize) || 1;
  }, [filteredColumns, columnPageSize]);

  const paginatedColumns = useMemo(() => {
    const start = (columnCurrentPage - 1) * columnPageSize;
    return filteredColumns.slice(start, start + columnPageSize);
  }, [filteredColumns, columnCurrentPage, columnPageSize]);

  // 预览行数据 (Mock)
  const previewData = useMemo(() => {
    if (file.name.includes('餐厅')) {
      return {
        columns: ['Store_ID', 'Product_Category', 'Sales_Amount', 'Rating', 'Order_Date'],
        rows: [
          { Store_ID: 'ST001', Product_Category: '中餐', Sales_Amount: 12500, Rating: 4.8, Order_Date: '2025-05-18' },
          { Store_ID: 'ST002', Product_Category: '西餐', Sales_Amount: 9800, Rating: 4.5, Order_Date: '2025-05-19' },
          { Store_ID: 'ST003', Product_Category: '日韩料理', Sales_Amount: 15400, Rating: 4.7, Order_Date: '2025-05-19' },
          { Store_ID: 'ST004', Product_Category: '甜品饮品', Sales_Amount: 5600, Rating: 4.9, Order_Date: '2025-05-20' },
          { Store_ID: 'ST005', Product_Category: '快餐', Sales_Amount: 11000, Rating: 4.3, Order_Date: '2025-05-20' },
        ]
      };
    } else {
      return {
        columns: ['Name', 'Platform', 'Year_of_Release', 'Genre', 'Publisher', 'NA_Sales', 'EU_Sales', 'JP_Sales', 'Other_Sales', 'Global_Sales', 'Critic_Score', 'Critic_Count', 'User_Score', 'User_Count', 'Developer', 'Rating'],
        rows: [
          { Name: 'Wii Sports', Platform: 'Wii', Year_of_Release: 2006, Genre: 'Sports', Publisher: 'Nintendo', NA_Sales: 41.36, EU_Sales: 28.96, JP_Sales: 3.77, Other_Sales: 8.45, Global_Sales: 82.53, Critic_Score: 76, Critic_Count: 51, User_Score: 8, User_Count: 322, Developer: 'Nintendo', Rating: 'E' },
          { Name: 'Super Mario Bros.', Platform: 'NES', Year_of_Release: 1985, Genre: 'Platform', Publisher: 'Nintendo', NA_Sales: 29.08, EU_Sales: 3.58, JP_Sales: 6.81, Other_Sales: 0.77, Global_Sales: 40.24, Critic_Score: '', Critic_Count: '', User_Score: '', User_Count: '', Developer: '', Rating: '' },
          { Name: 'Mario Kart Wii', Platform: 'Wii', Year_of_Release: 2008, Genre: 'Racing', Publisher: 'Nintendo', NA_Sales: 15.68, EU_Sales: 12.76, JP_Sales: 3.79, Other_Sales: 3.29, Global_Sales: 35.52, Critic_Score: 82, Critic_Count: 73, User_Score: 8.3, User_Count: 709, Developer: 'Nintendo', Rating: 'E' },
          { Name: 'Wii Sports Resort', Platform: 'Wii', Year_of_Release: 2009, Genre: 'Sports', Publisher: 'Nintendo', NA_Sales: 15.61, EU_Sales: 10.93, JP_Sales: 3.28, Other_Sales: 2.95, Global_Sales: 32.77, Critic_Score: 80, Critic_Count: 73, User_Score: 8, User_Count: 192, Developer: 'Nintendo', Rating: 'E' },
          { Name: 'Pokemon Red/Pokemon Blue', Platform: 'GB', Year_of_Release: 1996, Genre: 'Role-Playing', Publisher: 'Nintendo', NA_Sales: 11.27, EU_Sales: 8.89, JP_Sales: 10.22, Other_Sales: 1.00, Global_Sales: 31.37, Critic_Score: '', Critic_Count: '', User_Score: '', User_Count: '', Developer: '', Rating: '' },
        ]
      };
    }
  }, [file.name]);

  const copyText = (val: string) => {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(val).then(() => {
        setToastMessage('复制成功');
      }).catch(() => {
        const textarea = document.createElement('textarea');
        textarea.value = val;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
        setToastMessage('复制成功');
      });
    } else {
      const textarea = document.createElement('textarea');
      textarea.value = val;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
      setToastMessage('复制成功');
    }
  };

  const handleSaveDescription = () => {
    if (!editingColumn) return;
    setCustomColumnDescriptions(prev => ({
      ...prev,
      [`${editingColumn.fileId}_${editingColumn.columnName}`]: editingColumn.currentDesc
    }));
    setEditingColumn(null);
  };

  return (
    <div className="h-full w-full flex flex-col overflow-hidden text-gray-850 animate-in fade-in duration-300">
      {/* 顶部面包屑导航 */}
      <div className="flex h-12 w-full items-center px-4 py-2 text-sm flex-none border-b border-gray-100 select-none">
        <nav aria-label="breadcrumb">
          <ol className="flex flex-wrap items-center gap-1.5 wrap-break-word text-sm text-gray-400 sm:gap-2.5">
            <li className="inline-flex items-center gap-1.5">
              <span 
                onClick={onBackToFile}
                className="transition-colors hover:text-[#3A78F2] cursor-pointer text-gray-400 font-medium"
              >
                数据中心
              </span>
            </li>
            <li role="presentation" aria-hidden="true" className="text-gray-300">
              <ChevronRight className="w-3.5 h-3.5" />
            </li>
            <li className="inline-flex items-center gap-1.5">
              <span 
                onClick={onBackToFile}
                className="transition-colors hover:text-[#3A78F2] cursor-pointer text-gray-400 flex items-center gap-1 font-medium select-none"
              >
                <Database className="w-4 h-4 text-gray-400 mr-1" />
                {file.name}
              </span>
            </li>
            <li role="presentation" aria-hidden="true" className="text-gray-300">
              <ChevronRight className="w-3.5 h-3.5" />
            </li>
            <li className="inline-flex items-center gap-1.5">
              <span className="font-normal text-gray-700 flex items-center gap-1 font-medium select-none">
                <Sheet className="w-4 h-4 text-gray-400 mr-1" />
                {subTableName}
              </span>
            </li>
          </ol>
        </nav>
      </div>

      <div className="flex flex-1 flex-col overflow-y-auto px-4 pb-4">
        {/* 标题区与操作按钮 */}
        <div className="flex flex-none items-center justify-between py-2.5">
          <span className="flex items-center">
            <div className="bg-gray-100 text-gray-400 rounded-md p-3 flex items-center justify-center mr-2 select-none border border-gray-150">
              <Sheet className="w-6 h-6 text-gray-450" />
            </div>
            <div className="group relative flex items-center">
              <span className="text-gray-900 text-xl font-bold mx-2">{subTableName}</span>
              <span className="ml-2 hidden items-center group-hover:flex">
                <button 
                  onClick={() => copyText(subTableName)}
                  title="复制名称"
                  className="p-1 hover:bg-gray-100 rounded text-gray-400 hover:text-gray-600 transition-colors mr-1 border-0 bg-transparent"
                >
                  <Copy className="h-4 w-4 cursor-pointer" />
                </button>
                <button 
                  title="收藏"
                  className="p-1 hover:bg-gray-100 rounded text-gray-400 hover:text-yellow-500 transition-colors border-0 bg-transparent"
                >
                  <Star className="h-4 w-4 cursor-pointer" />
                </button>
              </span>
            </div>
          </span>
          <button 
            onClick={() => navigate('/chat', { 
              state: { 
                analyzeFile: {
                  id: file.id,
                  name: file.name,
                  type: file.type,
                  size: file.size
                }, 
                initialQuery: '帮我分析一下去分析按钮' 
              } 
            })}
            className="justify-center whitespace-nowrap font-semibold rounded-md text-sm flex h-8 items-center gap-1.5 px-4 bg-[#151517] hover:bg-[#252528] cursor-pointer active:scale-[0.98] transition-all text-white shadow-[0_2px_4px_rgba(21,21,23,0.12)] border border-transparent"
          >
            <Plus className="h-4 w-4 text-white" />
            去分析
          </button>
        </div>

        {/* 描述信息 */}
        <div className="flex-none mb-4 mt-1 select-none">
          <p className="flex items-center text-sm font-bold text-gray-800 mb-1.5">
            描述
            <HelpCircle className="text-gray-450 hover:text-gray-650 ml-1 h-4 w-4 cursor-pointer transition-colors" />
            <SquarePen className="text-gray-300 hover:text-gray-450 ml-2 h-4 w-4 cursor-not-allowed" />
          </p>
          <div className="flex items-center">
            <span className="text-gray-600 text-[13.5px] font-medium leading-relaxed">{subTableDesc}</span>
          </div>
        </div>

        {/* 卡片内部分隔线 */}
        <div className="h-[1px] w-full bg-gray-200/50 mb-4 select-none"></div>

        {/* 使用 Radix UI Tabs 组件重构 */}
        <Tabs.Root 
          value={subTableActiveTab} 
          onValueChange={(val) => setSubTableActiveTab(val as 'COLUMNS' | 'PREVIEW')}
          className="flex-1 flex flex-col overflow-hidden"
        >
          {/* Tab 页签触发器列表 */}
          <Tabs.List className="flex select-none mb-3.5 flex-none justify-start bg-transparent p-0">
            <div className="inline-flex h-10 items-center justify-center rounded-md bg-gray-100/80 p-1 text-gray-500">
              <Tabs.Trigger
                value="COLUMNS"
                className={clsx(
                  "inline-flex items-center justify-center whitespace-nowrap rounded-sm px-3 py-1.5 text-sm font-medium transition-all cursor-pointer border-none bg-transparent outline-none",
                  subTableActiveTab === 'COLUMNS' 
                    ? 'bg-white text-gray-900 shadow-sm font-semibold' 
                    : 'text-gray-500 hover:text-gray-900'
                )}
              >
                列信息
              </Tabs.Trigger>
              <Tabs.Trigger
                value="PREVIEW"
                className={clsx(
                  "inline-flex items-center justify-center whitespace-nowrap rounded-sm px-3 py-1.5 text-sm font-medium transition-all cursor-pointer border-none bg-transparent outline-none",
                  subTableActiveTab === 'PREVIEW' 
                    ? 'bg-white text-gray-900 shadow-sm font-semibold' 
                    : 'text-gray-500 hover:text-gray-900'
                )}
              >
                表预览
              </Tabs.Trigger>
            </div>
          </Tabs.List>

          {/* 列信息面板 */}
          <Tabs.Content value="COLUMNS" className="flex-1 flex flex-col overflow-hidden outline-none">
            {/* 工具栏 */}
            <div className="mb-3 flex justify-between items-center flex-none select-none">
              <div>
                <div className="relative">
                  <input 
                    value={columnSearchQuery}
                    onChange={(e) => {
                      setColumnSearchQuery(e.target.value);
                      setColumnCurrentPage(1);
                    }}
                    className="flex border border-gray-200 bg-white pl-8 pr-3 py-1 text-xs rounded-md h-8 w-48 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-[#3A78F2]/20 focus:border-[#3A78F2] transition-all"
                    placeholder="搜索"
                  />
                  <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-gray-400 z-10 pointer-events-none" />
                </div>
              </div>
              <button 
                onClick={() => setToastMessage('元数据已刷新')}
                className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-md border border-gray-200 hover:bg-gray-50 text-gray-500 hover:text-gray-700 transition-colors bg-white"
              >
                <RefreshCw className="h-4 w-4" />
              </button>
            </div>

            {/* 列信息表格 */}
            <div className="flex-1 overflow-auto border border-gray-200/60 rounded-md bg-white">
              <table className="w-full text-left border-collapse text-sm">
                <thead className="sticky top-0 bg-[#F7F9FA] z-10 border-b border-gray-150 select-none">
                  <tr>
                    <th className="px-4 py-2 font-semibold text-gray-600 text-xs tracking-wider border-r border-gray-150/70 last:border-0">列</th>
                    <th className="px-4 py-2 font-semibold text-gray-600 text-xs tracking-wider border-r border-gray-150/70 last:border-0">
                      <span className="flex items-center gap-1">
                        描述
                        <HelpCircle className="h-3.5 w-3.5 text-gray-400 hover:text-gray-650 cursor-pointer" />
                      </span>
                    </th>
                    <th className="px-4 py-2 font-semibold text-gray-600 text-xs tracking-wider border-r border-gray-150/70 last:border-0">类型</th>
                    <th className="px-4 py-2 font-semibold text-gray-600 text-xs tracking-wider border-r border-gray-150/70 last:border-0">更新时间</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100/70">
                  {paginatedColumns.map((col, index) => {
                    const colDescKey = `${file.id}_${col.name}`;
                    const displayDesc = customColumnDescriptions[colDescKey] !== undefined 
                      ? customColumnDescriptions[colDescKey] 
                      : col.desc;
                    return (
                      <tr key={index} className="hover:bg-gray-50/50 transition-colors">
                        <td className="px-4 py-2.5 font-bold text-gray-700 text-[13px]">{col.name}</td>
                        <td className="px-4 py-2.5 text-gray-600 text-[13px]">
                          <span className="flex items-center gap-1.5 group/desc">
                            <button 
                              onClick={() => setEditingColumn({
                                fileId: file.id,
                                columnName: col.name,
                                originalDesc: col.desc,
                                currentDesc: displayDesc,
                              })}
                              className="p-0 border-0 bg-transparent text-gray-400 hover:text-[#3A78F2] cursor-pointer flex-none transition-colors"
                            >
                              <SquarePen className="h-3.5 w-3.5" />
                            </button>
                            <span className="truncate" title={displayDesc}>{displayDesc}</span>
                          </span>
                        </td>
                        <td className="px-4 py-2.5 text-gray-500 font-mono text-xs">{col.type}</td>
                        <td className="px-4 py-2.5 text-gray-400 text-xs">{col.time}</td>
                      </tr>
                    );
                  })}
                  {paginatedColumns.length === 0 && (
                    <tr>
                      <td colSpan={4} className="text-center py-8 text-gray-400 text-xs">无匹配的列信息</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* 使用 Radix UI Dropdown Menu 重构分页下拉导航 */}
            <div className="flex items-center justify-end py-3.5 space-x-3.5 flex-none select-none text-xs text-gray-500">
              {/* 每页展示行数 */}
              <div className="flex items-center relative">
                <span className="text-gray-400">每页展示</span>
                
                <DropdownMenu.Root>
                  <DropdownMenu.Trigger asChild>
                    <button className="mx-1.5 h-6 w-14 border border-gray-200 px-1.5 flex items-center justify-between rounded bg-white text-gray-600 cursor-pointer text-xs font-semibold hover:border-gray-300 transition-colors">
                      <span>{columnPageSize}</span>
                      <ChevronsUpDown className="h-3.5 w-3.5 text-gray-400 opacity-60 flex-none" />
                    </button>
                  </DropdownMenu.Trigger>
                  
                  <DropdownMenu.Portal>
                    <DropdownMenu.Content 
                      className="bg-white border border-gray-200 rounded-md shadow-lg p-1 min-w-[56px] z-50 animate-in fade-in slide-in-from-top-1 duration-100"
                      align="end"
                    >
                      {[5, 10, 20, 50].map((size) => (
                        <DropdownMenu.Item 
                          key={size}
                          onClick={() => {
                            setColumnPageSize(size);
                            setColumnCurrentPage(1);
                          }}
                          className={clsx(
                            "text-xs px-2 py-1.5 cursor-pointer rounded-sm outline-none transition-colors",
                            columnPageSize === size 
                              ? "bg-blue-50 text-[#3A78F2] font-semibold" 
                              : "text-gray-650 hover:bg-gray-100"
                          )}
                        >
                          {size}
                        </DropdownMenu.Item>
                      ))}
                    </DropdownMenu.Content>
                  </DropdownMenu.Portal>
                </DropdownMenu.Root>
                
                <span className="text-gray-400">行</span>
              </div>

              {/* 翻页按钮 */}
              <div className="flex items-center space-x-1">
                <button 
                  onClick={() => setColumnCurrentPage(prev => Math.max(1, prev - 1))}
                  disabled={columnCurrentPage === 1}
                  className="inline-flex items-center justify-center h-6 w-6 rounded border border-gray-200 bg-white text-gray-500 hover:bg-gray-50 disabled:bg-gray-50 disabled:text-gray-300 disabled:cursor-not-allowed transition-colors"
                >
                  <ChevronRight className="w-3.5 h-3.5 rotate-180" />
                </button>
                
                {Array.from({ length: totalPages }).map((_, idx) => (
                  <button
                    key={idx}
                    onClick={() => setColumnCurrentPage(idx + 1)}
                    className={clsx(
                      "inline-flex items-center justify-center h-6 w-6 rounded border text-xs font-semibold transition-all",
                      columnCurrentPage === idx + 1
                        ? "border-blue-200 bg-blue-50 text-[#3A78F2]"
                        : "border-gray-200 bg-white text-gray-500 hover:bg-gray-50"
                    )}
                  >
                    {idx + 1}
                  </button>
                ))}

                <button 
                  onClick={() => setColumnCurrentPage(prev => Math.min(totalPages, prev + 1))}
                  disabled={columnCurrentPage === totalPages}
                  className="inline-flex items-center justify-center h-6 w-6 rounded border border-gray-200 bg-white text-gray-500 hover:bg-gray-50 disabled:bg-gray-50 disabled:text-gray-300 disabled:cursor-not-allowed transition-colors"
                >
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>
              </div>

              <div className="text-gray-400">
                <span className="text-[#3A78F2] font-semibold">{columnCurrentPage}</span> / {totalPages}
              </div>
            </div>
          </Tabs.Content>

          {/* 表数据预览面板 */}
          <Tabs.Content value="PREVIEW" className="flex-1 flex flex-col overflow-hidden outline-none">
            <div className="flex-1 overflow-auto border border-gray-200/60 rounded-md bg-white">
              <table className="w-full text-left border-collapse text-sm">
                <thead className="sticky top-0 bg-[#F7F9FA] z-10 border-b border-gray-150 select-none">
                  <tr>
                    {previewData.columns.map(col => (
                      <th key={col} className="px-4 py-3 font-semibold text-gray-600 text-xs tracking-wider border-r border-gray-150/70 last:border-0">
                        {col}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {previewData.rows.map((row: any, idx) => (
                    <tr key={idx} className="hover:bg-gray-50/40 transition-colors">
                      {previewData.columns.map(col => (
                        <td key={col} className="px-4 py-2.5 text-gray-700 font-medium truncate max-w-50 border-r border-gray-50/50 last:border-0 text-[13px]">
                          {row[col]}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Tabs.Content>
        </Tabs.Root>
      </div>

      {/* 使用 Radix UI Dialog 重构编辑列描述 Modal */}
      <Dialog.Root open={!!editingColumn} onOpenChange={(open) => { if (!open) setEditingColumn(null); }}>
        <Dialog.Portal>
          {/* 背景遮罩 */}
          <Dialog.Overlay className="fixed inset-0 z-50 bg-black/40 backdrop-blur-xs transition-opacity duration-200 animate-in fade-in" />
          
          {/* 对话框主体 */}
          <Dialog.Content className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-50 w-full max-w-[425px] rounded-xl border border-gray-150 bg-white p-5.5 shadow-xl select-none text-gray-800 animate-in fade-in zoom-in-95 duration-200 outline-none">
            {/* 头部标题 */}
            <div className="flex flex-col space-y-1.5 text-left mb-4">
              <Dialog.Title className="text-lg font-bold text-gray-900 leading-7">编辑列描述</Dialog.Title>
            </div>
            
            {/* 输入编辑区 */}
            {editingColumn && (
              <div className="grid gap-4 mb-5">
                <div>
                  <textarea 
                    value={editingColumn.currentDesc}
                    onChange={(e) => setEditingColumn({ ...editingColumn, currentDesc: e.target.value })}
                    className="flex min-h-[140px] w-full rounded-lg border border-gray-200 bg-white px-3.5 py-2.5 text-sm outline-none focus:border-[#3A78F2] focus:ring-2 focus:ring-blue-100 transition-all font-sans text-gray-700 leading-relaxed resize-none"
                    placeholder="请输入该字段的描述信息..."
                    autoFocus
                  />
                </div>
              </div>
            )}
            
            {/* 底部按钮栏 */}
            <div className="flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2.5">
              <Dialog.Close asChild>
                <button 
                  className="inline-flex items-center justify-center rounded-lg text-[13px] font-semibold border border-gray-200 bg-white hover:bg-gray-50 text-gray-600 h-9 px-4 py-2 cursor-pointer transition-colors" 
                  type="button"
                >
                  取消
                </button>
              </Dialog.Close>
              
              <button 
                onClick={handleSaveDescription}
                className="inline-flex items-center justify-center rounded-lg text-[13px] font-semibold bg-[#151517] hover:bg-[#252528] text-white h-9 px-4 py-2 cursor-pointer shadow-sm hover:shadow transition-all" 
                type="button"
              >
                保存
              </button>
            </div>
            
            {/* 右上角关闭按钮 */}
            <Dialog.Close asChild>
              <button 
                type="button" 
                className="absolute right-4 top-4 rounded-full p-1 hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-all cursor-pointer flex items-center justify-center border-none bg-transparent"
              >
                <X className="h-4 w-4" />
              </button>
            </Dialog.Close>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  );
};
