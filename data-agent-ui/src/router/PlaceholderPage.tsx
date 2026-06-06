import { useOutletContext } from 'react-router-dom';
import { CollapsedSidebarMenuButton } from '../layout/CollapsedSidebarMenuButton';
import type { LayoutOutletContext } from '../layout/GlobalLayout';

interface PlaceholderPageProps {
  title: string;
}

/**
 * 占位页面，用于保持待开发模块与其他页面一致的标题栏结构。
 */
export const PlaceholderPage = ({ title }: PlaceholderPageProps) => {
  const {
    isSidebarCollapsed,
    isSidebarVisible,
    expandSidebar,
  } = useOutletContext<LayoutOutletContext>();

  return (
    <div className="m-2 flex h-[calc(100%-1rem)] flex-col rounded-lg border border-gray-200/80 bg-white shadow-sm">
      <div className="flex h-12 items-center gap-3 border-b border-gray-100 px-5 text-gray-800">
        <CollapsedSidebarMenuButton
          isSidebarCollapsed={isSidebarCollapsed}
          isSidebarVisible={isSidebarVisible}
          expandSidebar={expandSidebar}
        />
        <span className="text-[14px] font-bold">{title}</span>
      </div>
      <div className="flex flex-1 items-center justify-center text-xl font-medium text-gray-400">
        {title} - 正在开发中...
      </div>
    </div>
  );
};
