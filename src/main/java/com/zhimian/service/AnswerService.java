package com.zhimian.service;

import com.zhimian.model.dto.AnswerSubmitDTO;
import com.zhimian.model.vo.DashboardVO;
import com.zhimian.model.vo.RankVO;

import java.util.List;

public interface AnswerService {
    void submitAnswer(long id, AnswerSubmitDTO dto);

    List<RankVO> getRank(String type, int limit);

    DashboardVO getDashboard();
}
