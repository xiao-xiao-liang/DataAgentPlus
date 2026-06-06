// 知识库文件接口定义
export interface KnowledgeFile {
  id: string;
  backendId?: number;
  name: string;
  size: string;
  status: 'uploading' | 'parsing' | 'success' | 'failed' | 'deleting' | 'delete_failed';
  progress?: number; // 上传进度百分比 (0-100)
  uploadedAt: string;
  splitterType?: string;
  errorMsg?: string;
}

export interface KnowledgeChunk {
  id: string;
  knowledgeId: number;
  seq: number;
  name: string;
  content: string;
  length: number;
  contentVersion: number;
  vectorVersion?: number;
  vectorTaskVersion: number;
  vectorStatus: 'PENDING' | 'PROCESSING' | 'SYNCED' | 'FAILED';
  vectorProcessingStartedAt?: string;
  vectorTaskTimeoutSeconds?: number;
  updateTime?: string;
  nameLocked?: boolean;
  retryCount?: number;
  errorMsg?: string;
  splitterType?: string;
}

export interface KnowledgeChunkUpdateResult {
  detail: KnowledgeChunk;
  messageSubmitted: boolean;
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

export interface KnowledgeCandidate {
  id: number;
  agentId: number;
  datasourceId?: number;
  sessionId?: string;
  threadId?: string;
  sourceQuestion: string;
  clarificationQuestion?: string;
  userAnswer?: string;
  normalizedContent: string;
  candidateType: string;
  title: string;
  scope: string;
  status: string;
  confidenceScore?: number;
  publishedTargetType?: string;
  publishedTargetId?: number;
  createTime?: string;
  updateTime?: string;
}

export interface KnowledgeCandidateExtraEntry {
  label: string;
  value: string;
}

export interface KnowledgeCandidateViewModel {
  businessTerm: string;
  description: string;
  calculationRule: string;
  synonyms: string[];
  badges: string[];
  extraEntries: KnowledgeCandidateExtraEntry[];
}
