import type { KnowledgeCandidate, KnowledgeCandidateExtraEntry, KnowledgeCandidateViewModel } from './types';

type ParsedCandidateContent = {
  businessTerm?: string;
  description?: string;
  calculationRule?: string;
  synonyms?: string[];
  isRecall?: boolean;
  extraEntries: KnowledgeCandidateExtraEntry[];
};

const PRIMARY_KEYS = new Set(['businessTerm', 'description', 'calculationRule', 'synonyms', 'isRecall']);

const toDisplayText = (value: unknown): string => {
  if (value === null || value === undefined) return '';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (Array.isArray(value)) return value.map(toDisplayText).filter(Boolean).join('、');
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
};

const safeParseNormalizedContent = (normalizedContent: string): ParsedCandidateContent => {
  try {
    const parsed = JSON.parse(normalizedContent) as Record<string, unknown>;
    const extras = Object.entries(parsed)
      .filter(([key, value]) => !PRIMARY_KEYS.has(key) && toDisplayText(value))
      .map(([key, value]) => ({
        label: '补充信息',
        value: `${key}：${toDisplayText(value)}`,
      }));

    return {
      businessTerm: typeof parsed.businessTerm === 'string' ? parsed.businessTerm : undefined,
      description: typeof parsed.description === 'string' ? parsed.description : undefined,
      calculationRule: typeof parsed.calculationRule === 'string' ? parsed.calculationRule : undefined,
      synonyms: Array.isArray(parsed.synonyms) ? parsed.synonyms.map(toDisplayText).filter(Boolean) : undefined,
      isRecall: Boolean(parsed.isRecall),
      extraEntries: extras,
    };
  } catch {
    return {
      description: normalizedContent.trim(),
      extraEntries: [],
    };
  }
};

export const buildKnowledgeCandidateViewModel = (candidate: KnowledgeCandidate): KnowledgeCandidateViewModel => {
  const parsed = safeParseNormalizedContent(candidate.normalizedContent || '');
  const badges = [
    candidate.status,
    candidate.scope,
    parsed.isRecall ? '可召回' : '',
  ].filter(Boolean) as string[];

  return {
    businessTerm: parsed.businessTerm || candidate.title || '未命名候选知识',
    description: parsed.description || '',
    calculationRule: parsed.calculationRule || '',
    synonyms: parsed.synonyms || [],
    badges,
    extraEntries: parsed.extraEntries,
  };
};
