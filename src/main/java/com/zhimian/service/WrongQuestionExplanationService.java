package com.zhimian.service;

import com.zhimian.model.vo.WrongQuestionExplanationVO;

public interface WrongQuestionExplanationService {

    WrongQuestionExplanationVO explain(Long questionId);
}
