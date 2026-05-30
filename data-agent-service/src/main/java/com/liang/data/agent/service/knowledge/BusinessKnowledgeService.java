package com.liang.data.agent.service.knowledge;

public interface BusinessKnowledgeService {

    Integer createFromCandidate(Integer agentId, String businessTerm, String description, String synonyms);
}
