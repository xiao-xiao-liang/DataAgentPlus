import { create } from 'zustand';

const CURRENT_AGENT_STORAGE_KEY = 'data-agent-current-agent';

export type CurrentAgentSnapshot = {
  agentId: string;
  agentName: string;
};

type CurrentAgentState = CurrentAgentSnapshot & {
  setCurrentAgent: (agent: Partial<CurrentAgentSnapshot> & { agentId: string }) => void;
};

const DEFAULT_AGENT: CurrentAgentSnapshot = {
  agentId: 'default',
  agentName: 'Data Agent',
};

const canUseLocalStorage = () => typeof localStorage !== 'undefined';

const readStoredAgent = (): CurrentAgentSnapshot => {
  if (!canUseLocalStorage()) {
    return DEFAULT_AGENT;
  }

  try {
    const raw = localStorage.getItem(CURRENT_AGENT_STORAGE_KEY);
    if (!raw) {
      return DEFAULT_AGENT;
    }
    const parsed = JSON.parse(raw) as Partial<CurrentAgentSnapshot>;
    if (!parsed.agentId) {
      return DEFAULT_AGENT;
    }
    return {
      agentId: String(parsed.agentId),
      agentName: parsed.agentName || DEFAULT_AGENT.agentName,
    };
  } catch (error) {
    console.error('读取当前智能体缓存失败', error);
    return DEFAULT_AGENT;
  }
};

const writeStoredAgent = (agent: CurrentAgentSnapshot) => {
  if (!canUseLocalStorage()) {
    return;
  }

  try {
    localStorage.setItem(CURRENT_AGENT_STORAGE_KEY, JSON.stringify(agent));
  } catch (error) {
    console.error('保存当前智能体缓存失败', error);
  }
};

export const useCurrentAgentStore = create<CurrentAgentState>((set, get) => ({
  ...readStoredAgent(),
  setCurrentAgent: (agent) => {
    const nextAgent = {
      agentId: String(agent.agentId),
      agentName: agent.agentName || get().agentName || DEFAULT_AGENT.agentName,
    };
    writeStoredAgent(nextAgent);
    set(nextAgent);
  },
}));

export const getCurrentAgentSnapshot = (): CurrentAgentSnapshot => {
  const { agentId, agentName } = useCurrentAgentStore.getState();
  return { agentId, agentName };
};

export const setCurrentAgentSnapshot = (agent: CurrentAgentSnapshot) => {
  useCurrentAgentStore.getState().setCurrentAgent(agent);
};
