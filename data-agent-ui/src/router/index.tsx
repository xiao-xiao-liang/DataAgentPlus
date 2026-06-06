import { createBrowserRouter, Navigate, useOutletContext } from 'react-router-dom';
import GlobalLayout from '../layout/GlobalLayout';
import type { LayoutOutletContext } from '../layout/GlobalLayout';
import { CollapsedSidebarMenuButton } from '../layout/CollapsedSidebarMenuButton';
import Home from '../views/Home';
import { DataCenter } from '../views/DataCenter';
import { McpCenter } from '../views/McpCenter';
import { KnowledgeCenter } from '../views/KnowledgeCenter';
import { CustomAgent } from '../views/CustomAgent';
import { CreateAgent } from '../views/CreateAgent';

/**
 * 简单占位页，保持与其他内容页一致的标题栏结构。
 */
const Placeholder = ({ title }: { title: string }) => {
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

const router = createBrowserRouter([
  {
    path: '/',
    element: <GlobalLayout />,
    children: [
      {
        index: true,
        element: <Navigate to="/chat" replace />,
      },
      {
        path: 'chat',
        element: <Home />,
      },
      {
        path: 'chat/:sessionId',
        element: <Home />,
      },
      {
        path: 'data',
        element: <DataCenter />,
      },
      {
        path: 'data/:database',
        element: <DataCenter />,
      },
      {
        path: 'data/:database/:tableName',
        element: <DataCenter />,
      },
      {
        path: 'knowledge',
        element: <KnowledgeCenter />,
      },
      {
        path: 'knowledge/candidates',
        element: <KnowledgeCenter />,
      },
      {
        path: 'knowledge/:knowledgeBaseId',
        element: <KnowledgeCenter />,
      },
      {
        path: 'knowledge/:knowledgeBaseId/files/:knowledgeId/chunks',
        element: <KnowledgeCenter />,
      },
      {
        path: 'memory',
        element: <Placeholder title="记忆管理" />,
      },
      {
        path: 'agent',
        element: <CustomAgent />,
      },
      {
        path: 'agent/create',
        element: <CreateAgent />,
      },
      {
        path: 'mcp',
        element: <McpCenter />,
      },
    ],
  },
]);

export default router;
