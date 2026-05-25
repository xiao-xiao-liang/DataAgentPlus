// 知识库文件接口定义
export interface KnowledgeFile {
  id: string;
  name: string;
  size: string;
  status: 'uploading' | 'parsing' | 'success' | 'failed';
  progress?: number; // 模拟上传进度百分比 (0-100)
  uploadedAt: string;
}

// 知识库接口定义
export interface KnowledgeBase {
  id: string;
  name: string;
  creator: string;
  status: 'ready' | 'loading' | 'failed';
  updatedAt: string;
  fileCount: number;
  description: string;
  files: KnowledgeFile[];
}
