import { createBrowserRouter, Navigate } from 'react-router-dom';
import GlobalLayout from '../layout/GlobalLayout';
import {
  LazyCreateAgent,
  LazyCustomAgent,
  LazyDataCenter,
  LazyHome,
  LazyKnowledgeCenter,
  LazyMcpCenter,
  LazyPlaceholderPage,
} from './LazyRouteElement';

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
        element: <LazyHome />,
      },
      {
        path: 'chat/:sessionId',
        element: <LazyHome />,
      },
      {
        path: 'data',
        element: <LazyDataCenter />,
      },
      {
        path: 'data/:database',
        element: <LazyDataCenter />,
      },
      {
        path: 'data/:database/:tableName',
        element: <LazyDataCenter />,
      },
      {
        path: 'knowledge',
        element: <LazyKnowledgeCenter />,
      },
      {
        path: 'knowledge/candidates',
        element: <LazyKnowledgeCenter />,
      },
      {
        path: 'knowledge/:knowledgeBaseId',
        element: <LazyKnowledgeCenter />,
      },
      {
        path: 'knowledge/:knowledgeBaseId/files/:knowledgeId/chunks',
        element: <LazyKnowledgeCenter />,
      },
      {
        path: 'memory',
        element: <LazyPlaceholderPage title="记忆管理" />,
      },
      {
        path: 'agent',
        element: <LazyCustomAgent />,
      },
      {
        path: 'agent/create',
        element: <LazyCreateAgent />,
      },
      {
        path: 'mcp',
        element: <LazyMcpCenter />,
      },
    ],
  },
]);

export default router;
