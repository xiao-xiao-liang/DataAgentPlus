import React, { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  Database, 
  ChevronRight, 
  Copy, 
  Star, 
  Trash2, 
  Plus, 
  HelpCircle, 
  Tag, 
  Search, 
  RefreshCw, 
  Table, 
  ChevronsUpDown 
} from 'lucide-react';
import clsx from 'clsx';
import type { UploadedFile } from '../types';

interface FileDetailProps {
  file: UploadedFile;
  onSelectSubTable: (tableName: string) => void;
  onDelete: (id: string) => void;
  setToastMessage: (msg: string | null) => void;
}

export const FileDetail: React.FC<FileDetailProps> = ({ 
  file, 
  onSelectSubTable, 
  onDelete, 
  setToastMessage 
}) => {
  const navigate = useNavigate();
  const baseName = useMemo(() => file.name.replace(/\.[^/.]+$/, ""), [file.name]);
  const displayId = file.id;

  const fileDesc = useMemo(() => {
    if (file.name.includes('餐厅')) {
      return '某餐厅的销售数据';
    } else if (file.name.includes('游戏')) {
      return '某游戏的运营和销售相关数据表';
    } else if (file.name.includes('信用卡')) {
      return '信用卡客户交易透支与还款记录明细';
    } else {
      return '该本地数据集的描述和字段定义信息';
    }
  }, [file.name]);

  const copyToClipboard = (val: string) => {
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

  const handleDelete = () => {
    if (file.isBuiltIn) return;
    onDelete(file.id);
  };

  return (
    <div className="h-full w-full flex flex-col overflow-hidden text-gray-850 animate-in fade-in duration-300">
      {/* 顶部面包屑导航 */}
      <div className="flex h-12 w-full items-center px-4 py-2 text-sm flex-none border-b border-gray-100 select-none">
        <nav aria-label="breadcrumb">
          <ol className="flex flex-wrap items-center gap-1.5 wrap-break-word text-sm text-gray-400 sm:gap-2.5">
            <li className="inline-flex items-center gap-1.5">
              <span className="text-gray-400 font-medium">数据中心</span>
            </li>
            <li role="presentation" aria-hidden="true" className="text-gray-300">
              <ChevronRight className="w-3.5 h-3.5" />
            </li>
            <li className="inline-flex items-center gap-1.5">
              <span className="font-normal text-gray-700 flex items-center gap-1 font-medium select-none">
                <Database className="w-4 h-4 text-gray-400 mr-1" />
                {file.name}
              </span>
            </li>
          </ol>
        </nav>
      </div>

      <div className="flex flex-1 flex-col overflow-y-auto px-4 pb-4">
        {/* 标题区与操作按钮 */}
        <div className="flex flex-none items-center justify-between py-1">
          <span className="flex items-center">
            <div className="bg-gray-100 text-gray-400 rounded-md p-3 flex items-center justify-center mr-2 select-none border border-gray-150">
              <Database className="w-6 h-6 text-gray-450" />
            </div>
            <div className="group relative flex items-center">
              <span className="text-gray-900 text-xl font-bold mx-2">{file.name}</span>
              <span className="ml-2 hidden items-center group-hover:flex">
                <button 
                  onClick={() => copyToClipboard(file.name)}
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
          
          <div className="flex items-center">
            <span className="flex items-center select-none">
              <button 
                onClick={handleDelete}
                disabled={file.isBuiltIn}
                title={file.isBuiltIn ? "不可删除内置数据" : "删除数据"}
                className={clsx(
                  "rounded-md p-2 flex items-center justify-center border transition-colors",
                  file.isBuiltIn 
                    ? "bg-gray-50 text-gray-350 border-gray-150/70 cursor-not-allowed" 
                    : "bg-white text-gray-500 hover:text-red-500 border-gray-200 hover:bg-red-50/20 cursor-pointer"
                )}
              >
                <Trash2 className="h-4 w-4" />
              </button>
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
              className="justify-center whitespace-nowrap font-semibold rounded-md text-sm ml-2 flex h-8 items-center gap-1.5 px-4 bg-[#151517] hover:bg-[#252528] cursor-pointer active:scale-[0.98] transition-all text-white shadow-[0_2px_4px_rgba(21,21,23,0.12)] border border-transparent"
            >
              <Plus className="h-4 w-4 text-white" />
              去分析
            </button>
          </div>
        </div>

        {/* 精致元数据与描述集成卡片 */}
        <div className="flex-none bg-[#FAFAFA]/90 border border-gray-200/40 rounded-xl p-4.5 mb-4 select-text shadow-xs">
          {/* 关于此库 */}
          <div className="mb-4">
            <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3 select-none">关于此库</p>
            <div className="grid grid-cols-2 gap-x-8 gap-y-2.5 text-[13px] leading-6">
              
              {/* ID */}
              <div className="text-gray-500 flex items-center">
                <span className="w-[85px] text-gray-400 font-medium select-none">ID</span>
                <div className="group relative flex cursor-pointer items-center text-gray-800 font-semibold">
                  <span className="select-text" title={displayId}>{displayId}</span>
                  <button
                    onClick={() => copyToClipboard(displayId)}
                    className="p-0.5 hover:bg-gray-200 rounded text-gray-400 hover:text-gray-600 transition-colors ml-1.5 opacity-60 hover:opacity-100 select-none border-0 bg-transparent"
                  >
                    <Copy className="h-3.5 w-3.5" />
                  </button>
                </div>
              </div>

              {/* 数据源类型 */}
              <div className="text-gray-500 flex items-center">
                <span className="w-[85px] text-gray-400 font-medium select-none">数据源类型</span>
                <span className="flex items-center">
                  <div className="text-indigo-600 bg-indigo-50/70 border border-indigo-100/60 flex items-center rounded-md px-1.5 py-0.5 text-xs font-semibold gap-1 select-none">
                    <Tag className="size-3 text-indigo-500" />
                    <span>{file.isBuiltIn ? "内置数据" : "本地上传"}</span>
                  </div>
                </span>
              </div>

              {/* 创建时间 */}
              <div className="text-gray-500 flex items-center">
                <span className="w-[85px] text-gray-400 font-medium select-none">创建时间</span>
                <span className="text-gray-800 font-semibold">{file.createdAt}</span>
              </div>

              {/* 文件大小 */}
              <div className="text-gray-500 flex items-center">
                <span className="w-[85px] text-gray-400 font-medium select-none">文件大小</span>
                <span className="text-gray-800 font-semibold">{file.size}</span>
              </div>

            </div>
          </div>

          {/* 极淡卡片内分隔线 */}
          <div className="h-[1px] w-full bg-gray-200/50 my-3.5 select-none"></div>

          {/* 描述信息 */}
          <div>
            <p className="flex items-center text-xs font-bold text-gray-400 uppercase tracking-wider mb-2 select-none">
              描述
              <HelpCircle className="text-gray-400 hover:text-gray-600 ml-1 h-3.5 w-3.5 cursor-pointer transition-colors" />
            </p>
            <div className="text-gray-600 text-[13px] font-medium leading-relaxed">{fileDesc}</div>
          </div>
        </div>

        {/* 子数据表列表 */}
        <div className="flex-1 flex flex-col overflow-hidden mt-2">
          <div className="mb-3.5 flex justify-between items-center flex-none select-none">
            <div>
              <div className="relative">
                <input 
                  className="flex border border-gray-200 bg-white pl-8 pr-3 py-1 text-xs rounded-md h-8 w-48 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-[#3A78F2]/20 focus:border-[#3A78F2] transition-all"
                  placeholder="搜索"
                  disabled
                />
                <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-gray-400 z-10 pointer-events-none" />
              </div>
            </div>
            <button 
              onClick={() => setToastMessage('元数据刷新成功')}
              className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-md border border-gray-200 hover:bg-gray-50 text-gray-500 hover:text-gray-700 transition-colors bg-white"
            >
              <RefreshCw className="h-4 w-4" />
            </button>
          </div>

          <div className="flex-1 overflow-auto border border-gray-200/60 rounded-md bg-white">
            <table className="w-full text-left border-collapse text-sm">
              <thead className="sticky top-0 bg-[#F7F9FA] z-10 border-b border-gray-150 select-none">
                <tr>
                  <th className="px-4 py-2.5 font-semibold text-gray-500 text-xs tracking-wider">名称</th>
                  <th className="px-4 py-2.5 font-semibold text-gray-500 text-xs tracking-wider">描述</th>
                  <th className="px-4 py-2.5 font-semibold text-gray-500 text-xs tracking-wider">创建时间</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100/70">
                <tr className="hover:bg-gray-50/50 transition-colors">
                  <td className="px-4 py-3">
                    <button 
                      onClick={() => onSelectSubTable(baseName)}
                      className="flex items-center text-[#1C2030] hover:text-[#3A78F2] hover:underline font-bold text-[13.5px] cursor-pointer border-0 bg-transparent text-left"
                    >
                      <Table className="h-4 w-4 text-gray-500 mr-2.5" />
                      {baseName}
                    </button>
                  </td>
                  <td className="px-4 py-3 text-gray-500 text-[13px] font-normal">{fileDesc.replace('某', '存储').replace('该', '存储')}</td>
                  <td className="px-4 py-3 text-gray-500 text-[13px] font-normal">{file.createdAt}</td>
                </tr>
              </tbody>
            </table>
          </div>

          {/* 分页导航 */}
          <div className="flex items-center justify-end py-4 flex-none select-none">
            <div className="flex flex-1 items-center justify-end text-sm mr-5 text-gray-500">
              每页展示
              <div className="mx-1 h-6 w-16 border border-gray-200 px-2 flex items-center justify-between rounded-md bg-white text-gray-600 cursor-pointer">
                <span>10</span>
                <ChevronsUpDown className="h-4 w-4 text-gray-400 opacity-60" />
              </div>
              行
            </div>
            <div className="flex items-center space-x-1">
              <button className="inline-flex items-center justify-center h-6 w-6 rounded border border-gray-200 bg-gray-50 text-gray-300 cursor-not-allowed" disabled>
                <ChevronRight className="w-4 h-4 rotate-180" />
              </button>
              <button className="inline-flex items-center justify-center h-6 w-6 rounded border border-blue-200 bg-blue-50 text-[#3A78F2] font-semibold text-sm">1</button>
              <button className="inline-flex items-center justify-center h-6 w-6 rounded border border-gray-200 bg-gray-50 text-gray-300 cursor-not-allowed" disabled>
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
            <div className="ml-2 text-sm text-gray-500">
              <span className="text-[#3A78F2] font-semibold">1</span> / 1
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
