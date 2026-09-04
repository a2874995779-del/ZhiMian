package com.zhimian.service;

import com.zhimian.common.PageResult;
import com.zhimian.model.dto.WrongQuestionQueryDTO;
import com.zhimian.model.vo.WrongQuestionVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public interface WrongQuestionService {
    PageResult<WrongQuestionVO> page(@Valid WrongQuestionQueryDTO dto);

    void markUnmastered(Long questionId);

    void markMastered(Long questionId);

    void remove(Long questionId);

    void recordAnswer(long questionId, @NotNull(message = "答题结果不能为空") @Min(value = 0,message = "答题结果取值为0或1") @Max(value = 1 ,message = "答题结果取值为0或1") Integer result);
}
