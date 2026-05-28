import { createBrowserRouter, Navigate } from 'react-router-dom';
import GlobalLayout from '../layout/GlobalLayout';
import Home from '../views/Home';
import { DataCenter } from '../views/DataCenter';
import { McpCenter } from '../views/McpCenter';
import { KnowledgeCenter } from '../views/KnowledgeCenter';
import { CustomAgent } from '../views/CustomAgent';
import { CreateAgent } from '../views/CreateAgent';

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
        path: 'knowledge/:knowledgeBaseId',
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

