package com.zhimian.service;

import com.zhimian.common.PageResult;
import com.zhimian.model.dto.FavoriteQueryDTO;
import com.zhimian.model.vo.FavoriteQuestionVO;
import jakarta.validation.Valid;

public interface QuestionFavoriteService {
    PageResult<FavoriteQuestionVO> page(@Valid FavoriteQueryDTO query);

    void add(Long questionId);

    void remove(Long questionId);

    Boolean isFavorited(Long questionId);
}
