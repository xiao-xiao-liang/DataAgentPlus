export const resolveSessionAgentId = (
  sessionAgentId: number | string | undefined,
  fallbackAgentId: string,
): string => {
  if (sessionAgentId === undefined || sessionAgentId === null || sessionAgentId === '') {
    return fallbackAgentId;
  }

  return String(sessionAgentId);
};

export const buildPathWithAgentId = (path: string, agentId: string): string => {
  return agentId && agentId !== 'default' ? `${path}?agentId=${agentId}` : path;
};
