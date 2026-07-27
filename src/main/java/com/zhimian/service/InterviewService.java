package com.zhimian.service;

import com.zhimian.common.PageResult;
import com.zhimian.model.dto.ChatMessageDTO;
import com.zhimian.model.dto.CreateInterviewDTO;
import com.zhimian.model.dto.InterviewSessionQueryDTO;
import com.zhimian.model.vo.InterviewReportVO;
import com.zhimian.model.vo.InterviewSessionDetailVO;
import com.zhimian.model.vo.InterviewSessionListVO;
import com.zhimian.model.vo.InterviewSessionVO;
import jakarta.validation.Valid;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface InterviewService {
    InterviewSessionVO createInterview(CreateInterviewDTO dto);

    SseEmitter chat(Long id, ChatMessageDTO dto);

    PageResult<InterviewSessionListVO> listSessions(InterviewSessionQueryDTO dto);

    InterviewSessionDetailVO getSessionDetail(Long id);

    InterviewReportVO finishInterview(Long id);
}
