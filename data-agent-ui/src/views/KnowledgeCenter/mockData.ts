import type { KnowledgeBase } from './types';

// 初始默认的知识库列表
export const INITIAL_KNOWLEDGE_BASES: KnowledgeBase[] = [
  {
    id: 'kb-default-1',
    name: '我的知识库',
    creator: 'aliyun9466154613',
    status: 'ready',
    updatedAt: '2026-05-18 20:53:04',
    fileCount: 2,
    description: '构建与 Data Agent 匹配使用的企业知识库',
    files: [
      {
        id: 'file-default-1-1',
        name: '2024Q3游戏营销策略.pdf',
        size: '1.2 MB',
        status: 'success',
        progress: 100,
        uploadedAt: '2026-05-18 20:45:10'
      },
      {
        id: 'file-default-1-2',
        name: '用户流失预测分析.xlsx',
        size: '850 KB',
        status: 'success',
        progress: 100,
        uploadedAt: '2026-05-18 20:50:22'
      }
    ]
  },
  {
    id: 'kb-default-2',
    name: '电商运营数据集',
    creator: 'aliyun9466154613',
    status: 'ready',
    updatedAt: '2026-05-20 11:22:15',
    fileCount: 4,
    description: '用于分析2025年二季度电商平台核心销售业绩及用户留存指标的知识储备。',
    files: [
      {
        id: 'file-default-2-1',
        name: 'orders.csv',
        size: '2.4 MB',
        status: 'success',
        progress: 100,
        uploadedAt: '2026-05-20 11:10:00'
      },
      {
        id: 'file-default-2-2',
        name: 'products.xlsx',
        size: '1.1 MB',
        status: 'success',
        progress: 100,
        uploadedAt: '2026-05-20 11:15:00'
      },
      {
        id: 'file-default-2-3',
        name: 'readme.md',
        size: '12 KB',
        status: 'success',
        progress: 100,
        uploadedAt: '2026-05-20 11:18:12'
      },
      {
        id: 'file-default-2-4',
        name: 'customer_behavior.csv',
        size: '3.2 MB',
        status: 'success',
        progress: 100,
        uploadedAt: '2026-05-20 11:20:00'
      }
    ]
  }
];
