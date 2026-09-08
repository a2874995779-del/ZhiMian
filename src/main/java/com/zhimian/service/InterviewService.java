package com.zhimian.service;

import com.zhimian.common.PageResult;
import com.zhimian.model.dto.ChatMessageDTO;
import com.zhimian.model.dto.CreateInterviewDTO;
import com.zhimian.model.dto.InterviewSessionQueryDTO;
import com.zhimian.model.vo.*;
import jakarta.validation.Valid;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface InterviewService {
    InterviewSessionVO createInterview(CreateInterviewDTO dto);

    SseEmitter chat(Long id, ChatMessageDTO dto);

    PageResult<InterviewSessionListVO> listSessions(InterviewSessionQueryDTO dto);

    InterviewSessionDetailVO getSessionDetail(Long id);

    InterviewSessionDetailVO getCurrentSession();

    InterviewReportStatusVO finishInterview(Long id);

    InterviewReportStatusVO getReportStatus(Long id);
}
