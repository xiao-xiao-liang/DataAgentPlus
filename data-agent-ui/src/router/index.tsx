import { createBrowserRouter, Navigate } from 'react-router-dom';
import GlobalLayout from '../layout/GlobalLayout';
import Home from '../views/Home';
import { DataCenter } from '../views/DataCenter';
import { McpCenter } from '../views/McpCenter';
import { KnowledgeCenter } from '../views/KnowledgeCenter';

const Placeholder = ({ title }: { title: string }) => (
  <div className="flex h-full items-center justify-center text-gray-400 text-xl font-medium">
    {title} - 正在开发中...
  </div>
);

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
        path: 'knowledge',
        element: <KnowledgeCenter />,
      },
      {
        path: 'memory',
        element: <Placeholder title="记忆管理" />,
      },
      {
        path: 'agent',
        element: <Placeholder title="自定义Agent" />,
      },
      {
        path: 'mcp',
        element: <McpCenter />,
      },
    ],
  },
]);

export default router;

