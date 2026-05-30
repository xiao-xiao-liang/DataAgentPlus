import React, { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  Sheet, 
  Timer, 
  Plus, 
  Star, 
  Copy, 
  CircleHelp, 
  Search, 
  RefreshCcw, 
  ChevronDown, 
  ChevronLeft, 
  ChevronRight,
  Key,
  Link2
} from 'lucide-react';
import clsx from 'clsx';
import type { DataSource } from '../types';
import { LogicalRelationConfig } from './LogicalRelationConfig';

// 引入数据库 LOGO 静态资产
import mysqlLogo from '../../../assets/logos/mysql.svg';
import postgresqlLogo from '../../../assets/logos/postgresql.svg';
import oracleLogo from '../../../assets/logos/oracle.svg';
import hbaseLogo from '../../../assets/logos/hbase.svg';

const getDatabaseLogo = (type: string) => {
  const t = type?.toLowerCase();
  if (t === 'mysql') return mysqlLogo;
  if (t === 'postgresql' || t === 'postgres' || t === 'polardb') return postgresqlLogo;
  if (t === 'oracle') return oracleLogo;
  if (t === 'hbase') return hbaseLogo;
  return null;
};

interface DataSourceDetailProps {
  selectedItem: DataSource;
  onUpdateImportedTables?: (dsId: string, updatedTables: string[]) => void;
  onBackToCenter?: () => void;
  onNotice?: (message: string) => void;
}

export const DataSourceDetail: React.FC<DataSourceDetailProps> = ({ 
  selectedItem, 
  onUpdateImportedTables,
  onBackToCenter,
  onNotice
}) => {
  const { tableName } = useParams();
  const navigate = useNavigate();
  // activeSubTable 用于存放下钻后查看的具体表名，为 null 表示查看库本身
  const [activeSubTable, setActiveSubTable] = useState<string | null>(null);

  // 物理表列表
  const [tables, setTables] = useState<{ name: string; comment?: string }[]>([]);

  // 获取该库物理表列表 (对接后端接口)
  const fetchTables = async () => {
    try {
      const response = await fetch(`/api/datasource/${selectedItem.id}/tables`);
      if (response.ok) {
        const result = await response.json();
        if (result.code === '0') {
          const list = (result.data || []).map((t: any) => ({
            name: t.tableName,
            comment: t.comment || '数据表'
          }));
          setTables(list);
        }
      }
    } catch (e) {
      console.error('加载物理表列表失败:', e);
    }
  };

  useEffect(() => {
    fetchTables();
  }, [selectedItem]);

  // 获取当前下钻的表的详细属性
  const currentTableDetail = useMemo(() => {
    if (!activeSubTable) return null;
    const currentTable = tables.find(t => t.name === activeSubTable);
    return {
      description: currentTable?.comment || '数据表',
      size: '128 KB',
      rows: 0,
      updateTime: selectedItem.createdAt || '2026-05-26 09:41:00'
    };
  }, [activeSubTable, tables, selectedItem]);

  // 受控的列选中状态
  const [selectedColumns, setSelectedColumns] = useState<string[]>([]);

  // 本地字段列表数据（用于支持编辑更新）
  const [columnsData, setColumnsData] = useState<any[]>([]);

  // 获取表的列字段列表 (对接后端接口)
  const fetchColumns = async () => {
    if (!activeSubTable) return;
    try {
      const response = await fetch(`/api/datasource/${selectedItem.id}/tables/${activeSubTable}/columns`);
      if (response.ok) {
        const result = await response.json();
        if (result.code === '0') {
          const unanalyticCols = JSON.parse(localStorage.getItem(`ds_cols_unanalytic_${selectedItem.id}_${activeSubTable}`) || '[]');
          const list = (result.data || []).map((col: any) => {
            const cacheKey = `col_asset_${selectedItem.id}_${activeSubTable}_${col.columnName}`;
            const localDesc = localStorage.getItem(cacheKey);
            const displayDesc = localDesc !== null ? localDesc : (col.comment || '—');
            return {
              name: col.columnName,
              type: col.dataType,
              desc: displayDesc,
              isAnalytic: !unanalyticCols.includes(col.columnName),
              nullable: col.nullable !== false,
              primaryKey: col.primaryKey === true || String(col.primaryKey) === 'true' || col.primaryKey === 1 || String(col.primaryKey) === '1' || col.columnName?.trim().toLowerCase() === 'id'
            };
          });
          setColumnsData(list);
          setSelectedColumns(list.map((c: any) => c.name));
          console.log('[Debug] fetchColumns columnsData:', list);
        }
      }
    } catch (e) {
      console.error('加载字段列表失败:', e);
    }
  };

  // 真实表数据采样预览
  const [tablePreviewData, setTablePreviewData] = useState<{ columns: string[]; data: any[] } | null>(null);

  // 获取表的物理采样预览 (对接后端接口)
  const fetchTablePreview = async () => {
    if (!activeSubTable) return;
    try {
      const response = await fetch(`/api/datasource/${selectedItem.id}/tables/${activeSubTable}/preview`);
      if (response.ok) {
        const result = await response.json();
        if (result.code === '0') {
          setTablePreviewData(result.data);
        }
      }
    } catch (e) {
      console.error('加载物理表数据采样失败:', e);
    }
  };

  // 设置参与/不参与分析状态 (本地持久化方案)
  const handleSetAnalytic = (analytic: boolean) => {
    if (!activeSubTable || selectedColumns.length === 0) return;
    const cacheKey = `ds_cols_unanalytic_${selectedItem.id}_${activeSubTable}`;
    let unanalyticCols = JSON.parse(localStorage.getItem(cacheKey) || '[]') as string[];
    
    if (analytic) {
      // 参与分析：从不参与列表中移除选中的列
      unanalyticCols = unanalyticCols.filter(col => !selectedColumns.includes(col));
    } else {
      // 不参与分析：将选中的列加入到不参与列表中
      selectedColumns.forEach(col => {
        if (!unanalyticCols.includes(col)) {
          unanalyticCols.push(col);
        }
      });
    }
    
    localStorage.setItem(cacheKey, JSON.stringify(unanalyticCols));
    
    // 更新 columnsData 状态以重新渲染
    setColumnsData(prev => prev.map(col => {
      if (selectedColumns.includes(col.name)) {
        return { ...col, isAnalytic: analytic };
      }
      return col;
    }));
  };

  useEffect(() => {
    if (activeSubTable) {
      fetchColumns();
    } else {
      setColumnsData([]);
      setSelectedColumns([]);
    }
    // 切换表名或选中项时，重置之前的采样数据
    setTablePreviewData(null);
  }, [activeSubTable, selectedItem]);



  // 当前正在编辑的字段对象，包含 name 和 assetDesc
  const [editingColumn, setEditingColumn] = useState<{ name: string; assetDesc: string } | null>(null);

  // 保存资产描述修改
  const handleSaveAssetDesc = (colName: string, newDesc: string) => {
    const cacheKey = `col_asset_${selectedItem.id}_${activeSubTable}_${colName}`;
    localStorage.setItem(cacheKey, newDesc);

    setColumnsData(prev => prev.map(col => {
      if (col.name === colName) {
        return { ...col, desc: newDesc };
      }
      return col;
    }));
    setEditingColumn(null);
  };

  // 数据表描述本地状态（用于编辑更新）
  const [tableDesc, setTableDesc] = useState('');
  const [tempTableDesc, setTempTableDesc] = useState('');
  const [isEditingTableDesc, setIsEditingTableDesc] = useState(false);

  // 当下钻表名改变时，同步数据表描述并检查本地缓存
  useEffect(() => {
    if (activeSubTable) {
      const cacheKey = `tb_desc_${selectedItem.id}_${activeSubTable}`;
      const cached = localStorage.getItem(cacheKey);
      if (cached) {
        setTableDesc(cached);
        setTempTableDesc(cached);
      } else {
        const desc = currentTableDetail?.description || '数据表';
        setTableDesc(desc);
        setTempTableDesc(desc);
      }
    } else {
      setTableDesc('');
      setTempTableDesc('');
    }
  }, [activeSubTable, currentTableDetail, selectedItem]);

  const handleStartEditTableDesc = () => {
    setTempTableDesc(tableDesc);
    setIsEditingTableDesc(true);
  };

  const handleSaveTableDesc = () => {
    if (activeSubTable) {
      const cacheKey = `tb_desc_${selectedItem.id}_${activeSubTable}`;
      localStorage.setItem(cacheKey, tempTableDesc);
      setTableDesc(tempTableDesc);
      setIsEditingTableDesc(false);
    }
  };

  // 全选/取消全选
  const handleToggleAllColumns = () => {
    if (selectedColumns.length === columnsData.length) {
      setSelectedColumns([]);
    } else {
      setSelectedColumns(columnsData.map(c => c.name));
    }
  };

  // 切换单个列的勾选状态
  const handleToggleColumn = (colName: string) => {
    setSelectedColumns(prev => {
      if (prev.includes(colName)) {
        return prev.filter(name => name !== colName);
      } else {
        return [...prev, colName];
      }
    });
  };
  
  // 表详情选项卡：列信息 'columns' | 表预览 'preview'
  const [activeDetailTab, setActiveDetailTab] = useState<'columns' | 'preview'>('columns');
  const [activeLibraryTab, setActiveLibraryTab] = useState<'tables' | 'relations'>('tables');

  // 懒加载真实采样数据
  useEffect(() => {
    if (activeSubTable && activeDetailTab === 'preview' && !tablePreviewData) {
      fetchTablePreview();
    }
  }, [activeDetailTab, activeSubTable, tablePreviewData]);
  
  // 表格内部模糊过滤
  const [searchQuery, setSearchQuery] = useState('');
  
  // 用于复制反馈的状态
  const [isCopied, setIsCopied] = useState(false);

  // 每一级切换或数据源改变，重置二级状态
  useEffect(() => {
    setActiveSubTable(null);
    setActiveDetailTab('columns');
    setActiveLibraryTab('tables');
    setSearchQuery('');
  }, [selectedItem]);

  // 监听路由参数变化，同步子表下钻状态（支持浏览器前进后退）
  useEffect(() => {
    if (tableName) {
      setActiveSubTable(tableName);
    } else {
      setActiveSubTable(null);
    }
  }, [tableName]);

  // 根据数据源里的已选择表缓存，获得过滤后的表列表
  const tablesList = useMemo(() => {
    const imported = JSON.parse(localStorage.getItem(`ds_imported_${selectedItem.id}`) || '[]') as string[];
    // 如果有勾选过，只显示勾选过的表。如果没有，默认显示该物理数据库下的所有表
    if (imported.length > 0) {
      return tables.filter(t => imported.includes(t.name));
    }
    return tables;
  }, [tables, selectedItem]);

  // 模糊检索过滤后的表列表（显示在库详情里的表表格中）
  const filteredTables = useMemo(() => {
    return tablesList.filter(t => 
      t.name.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [tablesList, searchQuery]);

  // 复制表名
  const handleCopyText = (text: string) => {
    navigator.clipboard.writeText(text);
    setIsCopied(true);
    setTimeout(() => setIsCopied(false), 1500);
  };

  // 面包屑返回上一级
  const handleBackToLibrary = () => {
    navigate(`/data/${selectedItem.database}`);
  };

  // 从此数据源中移出某张表
  const handleDeleteTable = (tableName: string) => {
    if (confirm(`确定要从此数据中心数据源中移除表 ${tableName} 吗？`)) {
      const updated = (selectedItem.importedTables || []).filter(t => t !== tableName);
      if (onUpdateImportedTables) {
        onUpdateImportedTables(selectedItem.id, updated);
      }
    }
  };



  // 组装真实的表采样预览数据结构 (融合 columnsData 获取类型)
  const currentTableSampleData = useMemo(() => {
    if (!activeSubTable || !tablePreviewData) return null;
    return {
      columns: tablePreviewData.columns.map(colName => {
        const matched = columnsData.find(c => c.name === colName);
        return {
          name: colName,
          type: matched?.type || 'VARCHAR'
        };
      }),
      data: tablePreviewData.data
    };
  }, [activeSubTable, tablePreviewData, columnsData]);

  return (
    <div className="h-full w-full flex flex-col overflow-hidden animate-in fade-in duration-300">
      
      {/* 1. 级联面包屑导航栏 */}
      <div className="flex h-9 w-full items-center px-4 text-xs flex-none border-b border-gray-100 bg-white select-none">
        <nav aria-label="breadcrumb">
          <ol className="flex flex-wrap items-center gap-1.5 break-words text-xs text-gray-400 sm:gap-2">
            <li className="inline-flex items-center gap-1.5">
              <span 
                onClick={onBackToCenter}
                className="transition-colors hover:text-gray-800 font-medium cursor-pointer"
              >
                数据中心
              </span>
            </li>
            <li role="presentation" aria-hidden="true" className="[&>svg]:w-3.5 [&>svg]:h-3.5 text-gray-300">
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5"><path d="m9 18 6-6-6-6"></path></svg>
            </li>
             <li className="inline-flex items-center gap-1.5">
              <span 
                onClick={handleBackToLibrary}
                className={clsx(
                  "transition-colors hover:text-gray-800 cursor-pointer font-medium flex items-center gap-1",
                  !activeSubTable ? "text-gray-800 font-bold" : "text-gray-400"
                )}
              >
                {(() => {
                  const logo = getDatabaseLogo(selectedItem.type);
                  if (logo) {
                    return <img src={logo} className="w-3.5 h-3.5 object-contain flex-none" alt={selectedItem.type} />;
                  }
                  return (
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" className="text-blue-500 w-3.5 h-3.5 flex-none"><path d="M12 2C6.5 2 2 4.2 2 7s4.5 5 10 5 10-2.2 10-5-4.5-5-10-5z"/><path d="M2 7v5c0 2.8 4.5 5 10 5s10-2.2 10-5V7"/><path d="M2 12v5c0 2.8 4.5 5 10 5s10-2.2 10-5v-5"/></svg>
                  );
                })()}
                <span>{selectedItem.database}</span>
              </span>
            </li>
            {activeSubTable && (
              <>
                <li role="presentation" aria-hidden="true" className="[&>svg]:w-3.5 [&>svg]:h-3.5 text-gray-300">
                  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-3.5 h-3.5"><path d="m9 18 6-6-6-6"></path></svg>
                </li>
                <li className="inline-flex items-center gap-1.5">
                  <span className="font-bold text-gray-800 flex items-center gap-1 select-none">
                    <Sheet className="w-3.5 h-3.5 text-gray-400 flex-none" />
                    {activeSubTable}
                  </span>
                </li>
              </>
            )}
          </ol>
        </nav>
      </div>

      {/* 2. 主卡片头部标题区域 */}
      <div className="flex flex-none items-center justify-between px-4 py-4 select-none">
        <span className="flex items-center gap-3">
          <div className="bg-gray-50 rounded-md p-1.5 flex items-center justify-center border border-gray-200/60 shadow-xs w-10 h-10 select-none">
            {(() => {
              if (activeSubTable) {
                return <Sheet className="w-6 h-6 text-gray-450" />;
              }
              const logo = getDatabaseLogo(selectedItem.type);
              if (logo) {
                return <img src={logo} className="w-7 h-7 object-contain" alt={selectedItem.type} />;
              }
              return (
                <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" className="text-blue-500 w-6 h-6"><path d="M12 2C6.5 2 2 4.2 2 7s4.5 5 10 5 10-2.2 10-5-4.5-5-10-5z"/><path d="M2 7v5c0 2.8 4.5 5 10 5s10-2.2 10-5V7"/><path d="M2 12v5c0 2.8 4.5 5 10 5s10-2.2 10-5v-5"/></svg>
              );
            })()}
          </div>
          <div className="group relative flex items-center gap-2">
            <span className="text-gray-850 text-xl font-bold">
              {activeSubTable ? activeSubTable : selectedItem.database}
            </span>
            <span className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <button 
                onClick={() => handleCopyText(activeSubTable || selectedItem.database)}
                className="text-gray-400 hover:text-gray-600 p-1 rounded hover:bg-gray-150/40 border-0 bg-transparent cursor-pointer"
                title={isCopied ? "已复制" : "复制名称"}
              >
                <Copy className={clsx("h-4 w-4", isCopied && "text-green-500")} />
              </button>
              <button className="text-gray-400 hover:text-yellow-500 p-1 rounded hover:bg-gray-150/40 border-0 bg-transparent cursor-pointer" title="收藏">
                <Star className="h-4 w-4" />
              </button>
            </span>
          </div>
        </span>

        <div className="flex items-center gap-2">
          <button className="justify-center whitespace-nowrap font-medium transition-colors border border-gray-200 bg-white hover:bg-gray-50 rounded-md text-sm flex h-8 items-center gap-1.5 px-3 shadow-xs cursor-pointer text-gray-700">
            <Timer className="h-4 w-4 text-gray-500" />
            设置周期任务
          </button>
          <button className="justify-center whitespace-nowrap font-medium transition-colors border border-gray-200 bg-white hover:bg-gray-50 rounded-md text-sm flex h-8 items-center gap-1.5 px-3 shadow-xs cursor-pointer text-gray-700">
            <Plus className="h-4 w-4 text-gray-500" />
            去分析
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-hidden flex flex-col px-4">
        {activeSubTable === null ? (
          /* ======================== 库级详情页面 ======================== */
          <div className="flex-1 overflow-hidden flex flex-col">
            
            {/* 关于此库面板 */}
            <div className="flex-none pb-4 select-none">
              <p className="my-5 text-sm font-bold text-gray-800">关于此库</p>
              <div className="text-sm leading-8">
                <div className="text-gray-500 flex items-center">
                  <span className="mr-2 w-[120px]">数据源类型</span>
                  <span className="text-gray-800">{selectedItem.type.toLowerCase()}</span>
                </div>
                <div className="text-gray-500 flex items-center">
                  <span className="mr-2 w-[120px]">创建时间</span>
                  <span className="text-gray-800">{selectedItem.createdAt}</span>
                </div>
                <div className="text-gray-500 flex items-center">
                  <span className="mr-2 w-[120px]">所属实例</span>
                  <span className="text-gray-800 font-mono">{selectedItem.host}</span>
                </div>
              </div>
              
              <div className="relative"></div>
              
              <div className="shrink-0 bg-gray-200/80 h-[1px] w-full my-4"></div>
              
              <p className="my-5 flex items-center text-sm font-bold text-gray-800">
                描述
                <CircleHelp className="text-gray-400 hover:text-gray-600 ml-1 h-4 w-4 cursor-pointer" />
              </p>
              <div className="flex items-center text-sm text-gray-500">
                <span>暂无</span>
              </div>
            </div>

            {/* 数据库表列表表格 */}
            <div className="flex-1 overflow-hidden flex flex-col mt-2">
              <div className="mb-3 flex flex-none items-center gap-1 border-b border-gray-100 select-none">
                <button
                  onClick={() => setActiveLibraryTab('tables')}
                  className={clsx(
                    "flex h-8 items-center gap-1.5 border-0 bg-transparent px-3 text-sm font-semibold transition-colors",
                    activeLibraryTab === 'tables'
                      ? "border-b-2 border-gray-900 text-gray-900"
                      : "text-gray-500 hover:text-gray-800"
                  )}
                >
                  <Sheet className="h-3.5 w-3.5" />
                  数据表
                </button>
                <button
                  onClick={() => setActiveLibraryTab('relations')}
                  className={clsx(
                    "flex h-8 items-center gap-1.5 border-0 bg-transparent px-3 text-sm font-semibold transition-colors",
                    activeLibraryTab === 'relations'
                      ? "border-b-2 border-gray-900 text-gray-900"
                      : "text-gray-500 hover:text-gray-800"
                  )}
                >
                  <Link2 className="h-3.5 w-3.5" />
                  逻辑外键
                </button>
              </div>
              {activeLibraryTab === 'tables' ? (
                <>
              <div className="mb-2.5 flex justify-between items-center flex-none select-none">
                <div className="relative w-64">
                  <input 
                    className="flex border border-gray-200 bg-white pl-8 pr-3 py-1 text-sm rounded-md h-8 w-full placeholder:text-gray-400 focus:outline-none focus:ring-1 focus:ring-blue-500" 
                    placeholder="搜索" 
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                  />
                  <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-gray-400" />
                </div>
                <button className="flex h-7 w-7 cursor-pointer items-center justify-center rounded-md border border-gray-200 bg-white hover:bg-gray-50 text-gray-500 hover:text-gray-700 shadow-2xs">
                  <RefreshCcw className="h-3.5 w-3.5" />
                </button>
              </div>

              {/* 表数据列表 */}
              <div className="flex-1 overflow-auto border-t border-gray-150/40">
                <table className="w-full text-left border-collapse text-sm">
                  <thead className="sticky top-0 bg-gray-50/90 backdrop-blur-xs z-10 border-b border-gray-200 select-none">
                    <tr>
                      <th className="px-4 py-2.5 font-bold text-gray-600 border-r border-gray-100/50 w-[35%] relative">
                        名称
                        <span className="absolute right-0 top-3 inline-block h-3 w-px bg-gray-200"></span>
                      </th>
                      <th className="px-4 py-2.5 font-bold text-gray-600 border-r border-gray-100/50 w-[35%] relative">
                        描述
                        <span className="absolute right-0 top-3 inline-block h-3 w-px bg-gray-200"></span>
                      </th>
                      <th className="px-4 py-2.5 font-bold text-gray-600 border-r border-gray-100/50 w-[20%] relative">
                        创建时间
                        <span className="absolute right-0 top-3 inline-block h-3 w-px bg-gray-200"></span>
                      </th>
                      <th className="px-4 py-2.5 font-bold text-gray-600 w-[10%]">操作</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {filteredTables.length === 0 ? (
                      <tr>
                        <td colSpan={4} className="px-4 py-10 text-center text-gray-400 text-sm">暂无数据表</td>
                      </tr>
                    ) : (
                      filteredTables.map(tb => {
                        const cachedDesc = localStorage.getItem(`tb_desc_${selectedItem.id}_${tb.name}`);
                        const description = cachedDesc || tb.comment || '数据表';
                        return (
                          <tr key={tb.name} className="hover:bg-gray-50/50 transition-colors h-9">
                            <td className="px-4 py-1.5 text-gray-700 font-medium font-mono">
                              <button 
                                onClick={() => navigate(`/data/${selectedItem.database}/${tb.name}`)}
                                className="flex items-center gap-1.5 text-gray-700 hover:text-gray-900 border-0 bg-transparent cursor-pointer text-sm font-medium p-0"
                              >
                                <Sheet className="h-4 w-4 text-gray-400 flex-none" />
                                {tb.name}
                              </button>
                            </td>
                            <td className="px-4 py-1.5 text-gray-500 truncate max-w-72 font-medium">
                              {description}
                            </td>
                            <td className="px-4 py-1.5 text-gray-500 font-mono">
                              {selectedItem.createdAt}
                            </td>
                            <td className="px-4 py-1.5">
                              <button 
                                onClick={() => handleDeleteTable(tb.name)}
                                className="text-gray-600 hover:text-red-500 border-0 bg-transparent cursor-pointer text-xs font-medium p-0"
                              >
                                删除
                              </button>
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>

              {/* 分页控制栏 */}
              <div className="flex items-center justify-end py-3.5 border-t border-gray-100 flex-none select-none">
                <div className="flex flex-1 items-center justify-end text-sm mr-5 text-gray-500">
                  每页展示
                  <div className="relative mx-1">
                    <select className="appearance-none border rounded-md bg-white py-0.5 pl-2 pr-6 text-xs font-medium text-gray-600 h-6 w-14 focus:outline-none cursor-pointer border-gray-200">
                      <option>10</option>
                    </select>
                    <ChevronDown className="absolute right-1.5 top-1.5 h-3 w-3 text-gray-400 pointer-events-none" />
                  </div>
                  行
                </div>
                <div className="flex items-center space-x-1">
                  <button disabled className="h-6 w-6 rounded border border-gray-200 p-0 flex items-center justify-center bg-gray-50 text-gray-400 cursor-not-allowed">
                    <ChevronLeft className="h-3.5 w-3.5" />
                  </button>
                  <button className="h-6 w-6 rounded border border-blue-500 bg-blue-50 text-blue-600 text-xs font-semibold flex items-center justify-center">
                    1
                  </button>
                  <button disabled className="h-6 w-6 rounded border border-gray-200 p-0 flex items-center justify-center bg-gray-50 text-gray-400 cursor-not-allowed">
                    <ChevronRight className="h-3.5 w-3.5" />
                  </button>
                </div>
                <div className="ml-2 text-sm text-gray-500">
                  <span className="text-blue-600 font-semibold">1</span>&nbsp;/&nbsp;<span>1</span>
                </div>
              </div>

                </>
              ) : (
                <LogicalRelationConfig
                  selectedItem={selectedItem}
                  tables={tablesList}
                  onNotice={onNotice}
                />
              )}
            </div>
          </div>
        ) : (
          /* ======================== 数据表级下钻页面 ======================== */
          <div className="flex-1 overflow-hidden flex flex-col">
            
            {/* 关于此表面板 */}
            <div className="flex-none pb-3 select-none">
              <p className="my-5 text-sm font-bold text-gray-800">关于此表</p>
              <div className="text-sm leading-8">
                <div className="text-gray-500 flex items-center">
                  <span className="mr-2 w-[120px]">建表描述</span>
                  <span className="text-gray-800">
                    {tableDesc || '数据表'}
                  </span>
                </div>
                <div className="text-gray-500 flex items-center">
                  <span className="mr-2 w-[120px]">表大小</span>
                  <span className="text-gray-800">{currentTableDetail?.size}</span>
                </div>
                <div className="text-gray-500 flex items-center">
                  <span className="mr-2 w-[120px]">表行数</span>
                  <span className="text-gray-800">{currentTableDetail?.rows}</span>
                </div>
                <div className="text-gray-500 flex items-center">
                  <span className="mr-2 w-[120px]">更新时间</span>
                  <span className="text-gray-800 font-mono">{currentTableDetail?.updateTime}</span>
                </div>
              </div>

              <div className="shrink-0 bg-gray-200/80 h-[1px] w-full my-4"></div>

              <p className="my-5 flex items-center text-sm font-bold text-gray-800 select-none cursor-default">
                描述
                <svg 
                  xmlns="http://www.w3.org/2000/svg" 
                  width="24" 
                  height="24" 
                  viewBox="0 0 24 24" 
                  fill="none" 
                  stroke="currentColor" 
                  strokeWidth="2" 
                  strokeLinecap="round" 
                  strokeLinejoin="round" 
                  className="text-gray-400 hover:text-blue-500 ml-1.5 flex h-3.5 w-3.5 cursor-pointer items-center flex-none transition-colors"
                  onClick={handleStartEditTableDesc}
                >
                  <path d="M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                  <path d="M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1-.853.505l-2.873.84a.5.5 0 0 1-.62-.62l.84-2.873a2 2 0 0 1 .506-.852z"></path>
                </svg>
              </p>
              <div className="flex items-center text-sm text-gray-500 font-medium select-text">
                <span className="cursor-default">{tableDesc || "暂无描述"}</span>
              </div>
            </div>

            {/* TAB栏切换列信息 vs 表预览 */}
            <div className="flex-none flex border-b border-gray-200 mt-2 select-none">
              <button
                onClick={() => setActiveDetailTab('columns')}
                className={clsx(
                  "px-4 py-2 text-sm font-semibold border-b-2 outline-none cursor-pointer transition-colors",
                  activeDetailTab === 'columns' 
                    ? "border-blue-500 text-blue-600" 
                    : "border-transparent text-gray-500 hover:text-gray-700"
                )}
              >
                列信息
              </button>
              <button
                onClick={() => setActiveDetailTab('preview')}
                className={clsx(
                  "px-4 py-2 text-sm font-semibold border-b-2 outline-none cursor-pointer transition-colors",
                  activeDetailTab === 'preview' 
                    ? "border-blue-500 text-blue-600" 
                    : "border-transparent text-gray-500 hover:text-gray-700"
                )}
              >
                表预览
              </button>
            </div>

            {/* TAB面板内容渲染 */}
            <div className="flex-1 overflow-hidden flex flex-col py-3">
              {activeDetailTab === 'columns' ? (
                /* ============== 列信息表格 ============== */
                <div className="flex-1 overflow-hidden flex flex-col">
                  {/* 列配置快捷操作栏 */}
                  <div className="flex items-center justify-between pb-3.5 select-none flex-none">
                    <div className="flex items-center gap-1.5">
                      <button 
                        onClick={() => handleSetAnalytic(true)}
                        disabled={selectedColumns.length === 0}
                        className="px-3 h-7 bg-[#151517] hover:bg-[#151517]/90 text-white text-xs font-semibold rounded-md shadow-xs flex items-center justify-center cursor-pointer disabled:opacity-50"
                      >
                        参与分析
                      </button>
                      <button 
                        onClick={() => handleSetAnalytic(false)}
                        disabled={selectedColumns.length === 0}
                        className="px-3 h-7 bg-[#F3F3F5] hover:bg-[#EAEAEF] text-gray-700 text-xs font-semibold rounded-md flex items-center justify-center border-0 cursor-pointer disabled:opacity-50"
                      >
                        不参与分析
                      </button>
                      <button className="px-3 h-7 bg-transparent hover:bg-gray-100 text-gray-700 text-xs font-semibold rounded-md flex items-center gap-1 border-0 cursor-pointer shadow-none">
                        <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="w-3 h-3"><polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"></polygon></svg>
                        筛选
                      </button>
                    </div>
                    <button className="flex h-7 w-7 cursor-pointer items-center justify-center rounded-md border border-gray-200 bg-white hover:bg-gray-50 text-gray-500 shadow-2xs">
                      <RefreshCcw className="h-3.5 w-3.5" />
                    </button>
                  </div>

                  {/* 表格容器 */}
                  <div className="flex-1 overflow-auto border-t border-gray-150/40">
                    <table className="w-full text-left border-collapse text-sm">
                      <thead className="sticky top-0 bg-gray-50/90 backdrop-blur-xs z-10 border-b border-gray-200 select-none">
                        <tr>
                          <th className="px-4 py-2.5 text-gray-600 w-12">
                            <label className="relative flex items-center justify-center cursor-pointer select-none">
                              <input 
                                type="checkbox" 
                                className="sr-only peer" 
                                checked={!!columnsData.length && selectedColumns.length === columnsData.length}
                                onChange={handleToggleAllColumns} 
                              />
                              <div className="w-4 h-4 bg-white border border-gray-300 rounded-[4px] peer-checked:bg-gray-900 peer-checked:border-gray-900 transition-colors"></div>
                              <svg className="absolute w-2.5 h-2.5 text-white opacity-0 peer-checked:opacity-100 pointer-events-none transition-opacity" fill="none" stroke="currentColor" strokeWidth="3.5" viewBox="0 0 24 24">
                                <polyline points="20 6 9 17 4 12" />
                              </svg>
                            </label>
                          </th>
                          <th className="px-3 py-2.5 font-semibold text-gray-700 border-r border-gray-100/50 w-[22%] relative whitespace-nowrap">
                            列名
                            <span className="absolute right-0 top-3 inline-block h-3 w-px bg-gray-200"></span>
                          </th>
                          <th className="px-3 py-2.5 font-semibold text-gray-700 border-r border-gray-100/50 w-[18%] relative whitespace-nowrap">
                            数据类型
                            <span className="absolute right-0 top-3 inline-block h-3 w-px bg-gray-200"></span>
                          </th>
                          <th className="px-3 py-2.5 font-semibold text-gray-700 border-r border-gray-100/50 w-[14%] relative text-center whitespace-nowrap">
                            可空性
                            <span className="absolute right-0 top-3 inline-block h-3 w-px bg-gray-200"></span>
                          </th>
                          <th className="px-3 py-2.5 font-semibold text-gray-700 border-r border-gray-100/50 w-[16%] relative text-center whitespace-nowrap">
                            是否参与分析
                            <span className="absolute right-0 top-3 inline-block h-3 w-px bg-gray-200"></span>
                          </th>
                          <th className="px-3 py-2.5 font-semibold text-gray-700 w-[30%] whitespace-nowrap">
                            <span className="flex items-center gap-1">
                              描述
                              <CircleHelp className="h-3.5 w-3.5 text-gray-450 cursor-pointer" />
                            </span>
                          </th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100 font-medium">
                        {columnsData.map(col => (
                          <tr key={col.name} className="hover:bg-gray-50/50 transition-colors h-11">
                            <td className="px-4 py-1.5">
                              <label className="relative flex items-center justify-center cursor-pointer select-none">
                                <input 
                                  type="checkbox" 
                                  className="sr-only peer" 
                                  checked={selectedColumns.includes(col.name)}
                                  onChange={() => handleToggleColumn(col.name)}
                                />
                                <div className="w-4 h-4 bg-white border border-gray-300 rounded-[4px] peer-checked:bg-gray-900 peer-checked:border-gray-900 transition-colors"></div>
                                <svg className="absolute w-2.5 h-2.5 text-white opacity-0 peer-checked:opacity-100 pointer-events-none transition-opacity" fill="none" stroke="currentColor" strokeWidth="3.5" viewBox="0 0 24 24">
                                  <polyline points="20 6 9 17 4 12" />
                                </svg>
                              </label>
                            </td>
                            <td className="px-3 py-1.5 text-gray-800 font-semibold font-mono whitespace-nowrap">
                              <div className="flex items-center gap-1.5">
                                <span>{col.name}</span>
                                {(col.primaryKey || col.name?.trim().toLowerCase() === 'id') && (
                                  <span 
                                    title="主键" 
                                    className="inline-flex items-center justify-center p-0.5 bg-amber-50 border border-amber-200 rounded-sm flex-none text-amber-600 ml-0.5"
                                  >
                                    <Key className="h-3 w-3" />
                                  </span>
                                )}
                              </div>
                            </td>
                            <td className="px-3 py-1.5 text-gray-500 font-mono text-xs whitespace-nowrap">{col.type}</td>
                            <td className="px-3 py-1.5 text-center whitespace-nowrap">
                              <span className={clsx(
                                "inline-flex items-center px-1.5 py-0.5 rounded text-[11px] font-bold select-none",
                                col.nullable 
                                  ? "bg-gray-50 border border-gray-200 text-gray-400" 
                                  : "bg-blue-50 border border-blue-200 text-blue-600"
                              )}>
                                {col.nullable ? 'NULL' : 'NOT NULL'}
                              </span>
                            </td>
                            <td className="px-3 py-1.5 text-center whitespace-nowrap">
                              <span className={clsx(
                                "inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-semibold select-none",
                                col.isAnalytic 
                                  ? "bg-green-55 border border-green-200/65 text-green-700" 
                                  : "bg-gray-100 border border-gray-200/60 text-gray-500"
                              )}>
                                <span className={clsx("w-1.5 h-1.5 rounded-full", col.isAnalytic ? "bg-green-500" : "bg-gray-450")} />
                                {col.isAnalytic ? '是' : '否'}
                              </span>
                            </td>
                            <td className="px-3 py-1.5 text-gray-600 font-sans text-xs whitespace-nowrap">
                              <div className="flex items-center gap-1.5 select-none">
                                <svg 
                                  xmlns="http://www.w3.org/2000/svg" 
                                  width="24" 
                                  height="24" 
                                  viewBox="0 0 24 24" 
                                  fill="none" 
                                  stroke="currentColor" 
                                  strokeWidth="2" 
                                  strokeLinecap="round" 
                                  strokeLinejoin="round" 
                                  className="text-gray-400 hover:text-blue-500 mr-1.5 flex h-3.5 w-3.5 cursor-pointer items-center flex-none transition-colors"
                                  onClick={() => setEditingColumn({ name: col.name, assetDesc: col.desc || '' })}
                                >
                                  <path d="M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                                  <path d="M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1-.853.505l-2.873.84a.5.5 0 0 1-.62-.62l.84-2.873a2 2 0 0 1 .506-.852z"></path>
                                </svg>
                                <span className="cursor-default select-text text-gray-500 truncate max-w-xs">{col.desc || "—"}</span>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              ) : (
                /* ============== 数据表预览采样 ============== */
                <div className="flex-1 overflow-auto border-t border-gray-150/40">
                  {currentTableSampleData ? (
                    <table className="w-full text-left border-collapse text-sm">
                      <thead className="sticky top-0 bg-gray-50/90 backdrop-blur-xs z-10 border-b border-gray-200 select-none">
                        <tr>
                          {currentTableSampleData.columns.map(col => (
                            <th key={col.name} className="px-4 py-2.5 font-bold text-gray-600 border-r border-gray-100 last:border-0">
                              <div className="flex flex-col">
                                <span className="font-semibold text-gray-700">{col.name}</span>
                                <span className="text-[9px] text-gray-400 font-normal font-mono">{col.type}</span>
                              </div>
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100 font-medium">
                        {currentTableSampleData.data.map((row, idx) => (
                          <tr key={idx} className="hover:bg-gray-50/40 transition-colors h-9">
                            {currentTableSampleData.columns.map(col => (
                              <td key={col.name} className="px-4 py-2.5 text-gray-700 font-mono truncate max-w-52 border-r border-gray-50 last:border-0">
                                {String(row[col.name])}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  ) : (
                    <div className="text-gray-400 text-xs text-center py-20">暂无采样数据</div>
                  )}
                </div>
              )}
            </div>

            {/* 分页控制栏 */}
            <div className="flex items-center justify-end py-2 border-t border-gray-100 flex-none select-none">
              <div className="flex flex-1 items-center justify-end text-sm mr-5 text-gray-500">
                每页展示
                <div className="relative mx-1">
                  <select className="appearance-none border rounded-md bg-white py-0.5 pl-2 pr-6 text-xs font-medium text-gray-600 h-6 w-14 focus:outline-none cursor-pointer border-gray-200">
                    <option>10</option>
                  </select>
                  <ChevronDown className="absolute right-1.5 top-1.5 h-3 w-3 text-gray-400 pointer-events-none" />
                </div>
                行
              </div>
              <div className="flex items-center space-x-1">
                <button disabled className="h-6 w-6 rounded border border-gray-200 p-0 flex items-center justify-center bg-gray-50 text-gray-400 cursor-not-allowed">
                  <ChevronLeft className="h-3.5 w-3.5" />
                </button>
                <button className="h-6 w-6 rounded border border-blue-500 bg-blue-50 text-blue-600 text-xs font-semibold flex items-center justify-center">
                  1
                </button>
                <button disabled className="h-6 w-6 rounded border border-gray-200 p-0 flex items-center justify-center bg-gray-50 text-gray-400 cursor-not-allowed">
                  <ChevronRight className="h-3.5 w-3.5" />
                </button>
              </div>
              <div className="ml-2 text-sm text-gray-500">
                <span className="text-blue-600 font-semibold">1</span>&nbsp;/&nbsp;<span>1</span>
              </div>
            </div>

          </div>
        )}
      </div>

      {/* 4. 编辑资产描述弹出模态框 */}
      {editingColumn && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-xs flex items-center justify-center z-50 animate-overlayShow">
          <div className="w-[425px] h-[350px] bg-white rounded-[10px] p-6 shadow-[0_8px_16px_rgba(0,0,0,0.1)] flex flex-col justify-between relative select-none animate-contentShow">
            <div className="flex justify-between items-center flex-none">
              <span className="text-[16px] font-bold text-gray-800">编辑列资产描述</span>
              <button 
                onClick={() => setEditingColumn(null)}
                className="text-gray-400 hover:text-gray-600 border-none bg-transparent cursor-pointer p-1 rounded-md hover:bg-gray-100 flex items-center justify-center"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
              </button>
            </div>
            
            <div className="flex-1 mt-4">
              <textarea
                className="w-full h-[185px] p-3 text-sm border border-gray-200 rounded-[6px] focus:outline-none focus:border-gray-500 text-[#3B3B3B] font-sans resize-none placeholder:text-gray-300"
                value={editingColumn.assetDesc}
                onChange={(e) => setEditingColumn(prev => prev ? { ...prev, assetDesc: e.target.value } : null)}
                placeholder="请输入列资产描述"
              />
            </div>
            
            <div className="flex justify-end gap-3 flex-none mt-2">
              <button
                onClick={() => setEditingColumn(null)}
                className="px-4 py-2 border border-gray-200 bg-white hover:bg-gray-50 text-gray-800 text-sm font-medium rounded-lg cursor-pointer transition-colors"
              >
                取消
              </button>
              <button
                onClick={() => handleSaveAssetDesc(editingColumn.name, editingColumn.assetDesc)}
                className="px-4 py-2 bg-[#151517] hover:bg-[#151517]/90 text-white text-sm font-medium rounded-lg cursor-pointer transition-colors"
              >
                保存
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 5. 编辑数据表描述弹出模态框 */}
      {isEditingTableDesc && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-xs flex items-center justify-center z-50 animate-overlayShow">
          <div className="w-[425px] h-[350px] bg-white rounded-[10px] p-6 shadow-[0_8px_16px_rgba(0,0,0,0.1)] flex flex-col justify-between relative select-none animate-contentShow">
            <div className="flex justify-between items-center flex-none">
              <span className="text-[16px] font-bold text-gray-800">编辑数据表描述</span>
              <button 
                onClick={() => setIsEditingTableDesc(false)}
                className="text-gray-400 hover:text-gray-600 border-none bg-transparent cursor-pointer p-1 rounded-md hover:bg-gray-100 flex items-center justify-center"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
              </button>
            </div>
            
            <div className="flex-1 mt-4">
              <textarea
                className="w-full h-[185px] p-3 text-sm border border-gray-200 rounded-[6px] focus:outline-none focus:border-gray-500 text-[#3B3B3B] font-sans resize-none placeholder:text-gray-300"
                value={tempTableDesc}
                onChange={(e) => setTempTableDesc(e.target.value)}
                placeholder="请输入数据表描述"
              />
            </div>
            
            <div className="flex justify-end gap-3 flex-none mt-2">
              <button
                onClick={() => setIsEditingTableDesc(false)}
                className="px-4 py-2 border border-gray-200 bg-white hover:bg-gray-50 text-gray-800 text-sm font-medium rounded-lg cursor-pointer transition-colors"
              >
                取消
              </button>
              <button
                onClick={handleSaveTableDesc}
                className="px-4 py-2 bg-[#151517] hover:bg-[#151517]/90 text-white text-sm font-medium rounded-lg cursor-pointer transition-colors"
              >
                保存
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};
