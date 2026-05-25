import React from 'react';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import { BookOpen, Ellipsis, Settings, Trash2 } from 'lucide-react';
import type { KnowledgeBase } from '../types';

interface KnowledgeListProps {
  list: KnowledgeBase[];
  onCreateClick: () => void;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
}

export const KnowledgeList: React.FC<KnowledgeListProps> = ({
  list,
  onCreateClick,
  onSelect,
  onDelete,
}) => {
  return (
    <div className="grid min-h-0 flex-1 auto-rows-min grid-cols-1 items-start gap-4 overflow-y-auto px-6 md:grid-cols-2 lg:grid-cols-3 no-scrollbar pt-4 pb-6 select-none animate-in fade-in duration-200">
      
      {/* “创建知识库”卡片 */}
      <div 
        onClick={onCreateClick}
        className="group relative h-[10rem] cursor-pointer rounded-xl border border-dashed border-indigo-200 hover:border-indigo-400 bg-[#F6F6FD]/70 hover:bg-[#F6F6FD] p-[1px] transition-all hover:shadow-[0_0.25rem_0.625rem_0_rgba(102,127,255,0.15)] hover:-translate-y-[2px] duration-300 overflow-hidden"
      >
        <div className="relative flex h-full flex-col justify-end gap-2 px-6 py-5">
          {/* 右下角大图 */}
          <img 
            className="absolute bottom-[-1.5rem] right-0 h-[85%] w-auto object-contain opacity-55 transition-transform duration-500 group-hover:scale-105" 
            src="https://g.alicdn.com/apsaradb-fe/dms-theia-app/0.3.5/data-agent/static/svg/create.svg" 
            alt="创建知识库" 
          />
          <div className="relative z-10">
            <span className="bg-gradient-to-r from-[#2D336B] to-[#4F46E5] bg-clip-text text-base font-extrabold tracking-[1px] text-transparent">
              创建知识库
            </span>
            <div className="text-gray-500 mt-1.5 text-xs font-medium">
              构建与 Data Agent 匹配使用的企业知识库
            </div>
          </div>
        </div>
      </div>

      {/* 已有知识库卡片列表 */}
      {list.map((kb) => (
        <div 
          key={kb.id}
          onClick={() => onSelect(kb.id)}
          className="group relative h-[10rem] cursor-pointer rounded-xl border border-gray-200/80 bg-white p-[1px] transition-all hover:shadow-[0_0.25rem_0.625rem_0_rgba(102,127,255,0.15)] hover:-translate-y-[2px] duration-300 flex flex-col justify-between"
        >
          <div className="relative flex h-full w-full flex-col justify-between p-4 bg-white rounded-xl">
            
            {/* 卡片右上角下拉管理菜单 */}
            <div className="absolute right-3 top-3 z-20">
              <DropdownMenu.Root>
                <DropdownMenu.Trigger asChild>
                  <button 
                    onClick={(e) => e.stopPropagation()}
                    className="flex size-6 items-center justify-center rounded-md hover:bg-gray-100 text-gray-400 hover:text-gray-700 transition-colors border-none bg-transparent outline-none cursor-pointer"
                    type="button"
                  >
                    <Ellipsis className="size-4" />
                  </button>
                </DropdownMenu.Trigger>

                <DropdownMenu.Portal>
                  <DropdownMenu.Content 
                    align="end" 
                    sideOffset={5}
                    onClick={(e) => e.stopPropagation()}
                    className="z-50 min-w-[110px] rounded-lg border border-gray-150 bg-white p-1 shadow-md animate-in fade-in slide-in-from-top-1 duration-100 font-sans"
                  >
                    {/* 管理详情项 */}
                    <DropdownMenu.Item 
                      onClick={() => onSelect(kb.id)}
                      className="flex items-center gap-2 px-2.5 py-1.5 text-xs font-semibold text-gray-700 rounded-md hover:bg-gray-100 focus:bg-gray-100 cursor-pointer outline-none transition-colors"
                    >
                      <Settings className="size-3.5 text-gray-500" />
                      管理知识库
                    </DropdownMenu.Item>
                    
                    {/* 分割线 */}
                    <div className="h-px bg-gray-100 my-1" />

                    {/* 删除项 */}
                    <DropdownMenu.Item 
                      onClick={() => onDelete(kb.id)}
                      className="flex items-center gap-2 px-2.5 py-1.5 text-xs font-semibold text-red-600 rounded-md hover:bg-red-50 focus:bg-red-50 cursor-pointer outline-none transition-colors"
                    >
                      <Trash2 className="size-3.5 text-red-500" />
                      删除
                    </DropdownMenu.Item>
                  </DropdownMenu.Content>
                </DropdownMenu.Portal>
              </DropdownMenu.Root>
            </div>

            {/* 卡片标题区 */}
            <div className="pr-8">
              <div className="flex items-center gap-2">
                <div className="p-1 rounded bg-[#F4F5FF] flex-none">
                  <BookOpen className="size-4 text-[#2D336B]" />
                </div>
                <h3 className="truncate text-sm font-bold text-gray-800 leading-tight group-hover:underline">
                  {kb.name}
                </h3>
              </div>
              <p className="text-gray-400 mt-2 text-xs line-clamp-2 leading-relaxed font-medium">
                {kb.description || '暂无描述信息'}
              </p>
            </div>

            {/* 卡片尾部元数据 */}
            <div className="text-gray-400 flex items-center gap-2 text-[10px] border-t border-gray-100/70 pt-2 font-mono">
              <span className="shrink-0 rounded-full px-1.5 py-0.5 text-[9px] bg-emerald-50 text-emerald-600 border border-emerald-200/50 font-bold font-sans">
                已就绪
              </span>
              <span className="shrink-0 text-gray-200 font-sans">|</span>
              <span className="truncate max-w-[80px]" title={kb.creator}>
                {kb.creator}
              </span>
              <span className="shrink-0 text-gray-200 font-sans">|</span>
              <span className="truncate">
                更新于 {kb.updatedAt.split(' ')[0]}
              </span>
            </div>

          </div>
        </div>
      ))}
    </div>
  );
};
