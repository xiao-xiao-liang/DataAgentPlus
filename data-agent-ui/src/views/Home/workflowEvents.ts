export type WorkflowEventType =
  | 'clarification_request'
  | 'clarification_confirmation'
  | 'memory_candidate'
  | 'queue_waiting'
  | 'queue_running';

export interface WorkflowEvent<T = any> {
  eventType: WorkflowEventType;
  payload: T;
}

export const EVENT_PREFIX = '@@DATA_AGENT_EVENT@@';
export const EVENT_SUFFIX = '@@END_DATA_AGENT_EVENT@@';

export const splitWorkflowEvents = (content: string): { visibleContent: string; events: WorkflowEvent[] } => {
  const events: WorkflowEvent[] = [];
  let visibleContent = content;

  while (visibleContent.includes(EVENT_PREFIX) && visibleContent.includes(EVENT_SUFFIX)) {
    const start = visibleContent.indexOf(EVENT_PREFIX);
    const end = visibleContent.indexOf(EVENT_SUFFIX, start);
    if (end < 0) break;

    const jsonText = visibleContent.slice(start + EVENT_PREFIX.length, end);
    try {
      const parsed = JSON.parse(jsonText);
      if (parsed.eventType && parsed.payload) {
        events.push({ eventType: parsed.eventType, payload: parsed.payload });
      }
    } catch (error) {
      console.warn('解析工作流事件失败', error);
    }
    visibleContent = visibleContent.slice(0, start) + visibleContent.slice(end + EVENT_SUFFIX.length);
  }

  return { visibleContent, events };
};
