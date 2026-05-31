import { ChevronsRight, Menu } from 'lucide-react';
import clsx from 'clsx';

interface CollapsedSidebarMenuButtonProps {
  isSidebarCollapsed: boolean;
  isSidebarVisible: boolean;
  expandSidebar: () => void;
  className?: string;
}

/**
 * 内容区标题栏内的统一侧边栏展开入口。
 */
export const CollapsedSidebarMenuButton: React.FC<CollapsedSidebarMenuButtonProps> = ({
  isSidebarCollapsed,
  isSidebarVisible,
  expandSidebar,
  className,
}) => {
  if (!isSidebarCollapsed) {
    return null;
  }

  return (
    <button
      type="button"
      onClick={expandSidebar}
      title="展开边栏"
      aria-label="展开边栏"
      className={clsx(
        'group inline-flex size-7 items-center justify-center rounded-md border-0 bg-transparent p-0 text-[#0A0A0B] transition-colors hover:bg-gray-200/40',
        isSidebarVisible ? 'bg-[#F3F3F4] shadow-sm' : '',
        className
      )}
    >
      <span className="relative size-[18px]">
        <Menu
          className={clsx(
            'absolute inset-0 size-[18px] transition-opacity duration-150',
            isSidebarVisible ? 'opacity-0' : 'opacity-100 group-hover:opacity-0 group-focus-visible:opacity-0'
          )}
        />
        <ChevronsRight
          className={clsx(
            'absolute inset-0 size-[18px] transition-opacity duration-150',
            isSidebarVisible ? 'opacity-100' : 'opacity-0 group-hover:opacity-100 group-focus-visible:opacity-100'
          )}
        />
      </span>
    </button>
  );
};
