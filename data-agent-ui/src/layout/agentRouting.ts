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
  if (!agentId || agentId === 'default') {
    return path;
  }

  if (!path.startsWith('/chat') && path !== '/knowledge/candidates') {
    return path;
  }

  const separator = path.includes('?') ? '&' : '?';
  return `${path}${separator}agentId=${agentId}`;
};
