/**
 * 从智能体知识库路由 ID 中提取智能体 ID。
 */
export const getAgentIdFromKnowledgeBaseId = (knowledgeBaseId?: string): string | null => {
  const match = knowledgeBaseId?.match(/^kb-agent-(.+)$/);
  return match?.[1] || null;
};

/**
 * 解析知识中心当前应该使用的智能体 ID。
 */
export const resolveKnowledgeAgentId = (
  currentAgentId: string | null | undefined,
  knowledgeBaseId?: string,
): string => {
  const routeAgentId = getAgentIdFromKnowledgeBaseId(knowledgeBaseId);
  if (routeAgentId) return routeAgentId;
  return currentAgentId && currentAgentId !== 'default' ? currentAgentId : '1';
};
