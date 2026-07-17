package com.zhimian.service;

import com.zhimian.common.PageResult;
import com.zhimian.model.dto.QuestionAddDTO;
import com.zhimian.model.dto.QuestionQueryDTO;
import com.zhimian.model.vo.QuestionListVO;
import jakarta.validation.Valid;

public interface QuestionService {
    Long addQuestion(@Valid QuestionAddDTO dto);

    void updateQuestion(Long id, @Valid QuestionAddDTO dto);

    void deleteQuestion(Long id);

    PageResult<QuestionListVO> pageQuestions(@Valid QuestionQueryDTO dto);
}
