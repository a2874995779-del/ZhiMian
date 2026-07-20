package com.zhimian.service;

import com.zhimian.model.dto.CreateInterviewDTO;
import com.zhimian.model.vo.InterviewSessionVO;
import jakarta.validation.Valid;

public interface InterviewService {
    InterviewSessionVO createInterview(CreateInterviewDTO dto);
}
