package com.liang.data.agent.service.knowledgecandidate;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liang.data.agent.dal.entity.KnowledgeCandidateEntity;
import com.liang.data.agent.service.knowledgecandidate.dto.KnowledgeCandidateDTO;
import com.liang.data.agent.service.knowledgecandidate.dto.PublishKnowledgeCandidateDTO;
import com.liang.data.agent.service.knowledgecandidate.vo.KnowledgeCandidateVO;

import java.util.List;

public interface KnowledgeCandidateService extends IService<KnowledgeCandidateEntity> {

    Long create(KnowledgeCandidateDTO dto);

    List<KnowledgeCandidateVO> listByAgent(Integer agentId, String status);

    KnowledgeCandidateVO getDetail(Long id);

    void submitForReview(Long id);

    void reject(Long id, PublishKnowledgeCandidateDTO dto);

    Long publish(Long id, PublishKnowledgeCandidateDTO dto);
}
