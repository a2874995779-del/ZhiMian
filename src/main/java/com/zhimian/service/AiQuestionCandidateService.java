package com.zhimian.service;

import com.zhimian.common.PageResult;
import com.zhimian.model.dto.AiQuestionApproveDTO;
import com.zhimian.model.dto.AiQuestionCandidateQueryDTO;
import com.zhimian.model.vo.AiQuestionCandidateVO;

public interface AiQuestionCandidateService {
    PageResult<AiQuestionCandidateVO> page(AiQuestionCandidateQueryDTO query);

    Long approve(Long id, AiQuestionApproveDTO dto);

    void ignore(Long id);
}
