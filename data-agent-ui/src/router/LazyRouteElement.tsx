import { lazy, Suspense, type ReactNode } from 'react';
import { PlaceholderPage } from './PlaceholderPage';

const Home = lazy(() => import('../views/Home'));
const DataCenter = lazy(() => import('../views/DataCenter').then((module) => ({ default: module.DataCenter })));
const McpCenter = lazy(() => import('../views/McpCenter').then((module) => ({ default: module.McpCenter })));
const KnowledgeCenter = lazy(() => import('../views/KnowledgeCenter').then((module) => ({ default: module.KnowledgeCenter })));
const CustomAgent = lazy(() => import('../views/CustomAgent').then((module) => ({ default: module.CustomAgent })));
const CreateAgent = lazy(() => import('../views/CreateAgent').then((module) => ({ default: module.CreateAgent })));

const RouteSuspense = ({ children }: { children: ReactNode }) => (
  <Suspense
    fallback={(
      <div className="flex h-full items-center justify-center text-sm font-medium text-gray-400">
        页面加载中...
      </div>
    )}
  >
    {children}
  </Suspense>
);

/**
 * 聊天页面的懒加载路由元素。
 */
export const LazyHome = () => (
  <RouteSuspense>
    <Home />
  </RouteSuspense>
);

/**
 * 数据中心页面的懒加载路由元素。
 */
export const LazyDataCenter = () => (
  <RouteSuspense>
    <DataCenter />
  </RouteSuspense>
);

/**
 * MCP 中心页面的懒加载路由元素。
 */
export const LazyMcpCenter = () => (
  <RouteSuspense>
    <McpCenter />
  </RouteSuspense>
);

/**
 * 知识库页面的懒加载路由元素。
 */
export const LazyKnowledgeCenter = () => (
  <RouteSuspense>
    <KnowledgeCenter />
  </RouteSuspense>
);

/**
 * 自定义智能体页面的懒加载路由元素。
 */
export const LazyCustomAgent = () => (
  <RouteSuspense>
    <CustomAgent />
  </RouteSuspense>
);

/**
 * 创建智能体页面的懒加载路由元素。
 */
export const LazyCreateAgent = () => (
  <RouteSuspense>
    <CreateAgent />
  </RouteSuspense>
);

/**
 * 占位页面的懒加载路由元素。
 */
export const LazyPlaceholderPage = ({ title }: { title: string }) => (
  <RouteSuspense>
    <PlaceholderPage title={title} />
  </RouteSuspense>
);
