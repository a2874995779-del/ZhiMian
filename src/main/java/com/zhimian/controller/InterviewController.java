package com.zhimian.controller;

import com.zhimian.common.PageResult;
import com.zhimian.common.Result;
import com.zhimian.model.dto.ChatMessageDTO;
import com.zhimian.model.dto.CreateInterviewDTO;
import com.zhimian.model.dto.InterviewSessionQueryDTO;
import com.zhimian.model.vo.InterviewReportVO;
import com.zhimian.model.vo.InterviewSessionDetailVO;
import com.zhimian.model.vo.InterviewSessionListVO;
import com.zhimian.model.vo.InterviewSessionVO;
import com.zhimian.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {
    private final InterviewService interviewService;

    @PostMapping
    public Result<InterviewSessionVO> create(@Valid @RequestBody CreateInterviewDTO dto){
        return Result.success(interviewService.createInterview(dto));
    }

    @PostMapping("/{id}/chat")
    public SseEmitter chat(@PathVariable Long id, @Valid @RequestBody ChatMessageDTO dto){
        return interviewService.chat(id,dto);
    }

    @GetMapping
    public Result<PageResult<InterviewSessionListVO>> list(@Valid InterviewSessionQueryDTO dto){
        return Result.success(interviewService.listSessions(dto));
    }

    @GetMapping("/{id}")
    public Result<InterviewSessionDetailVO> detail(@PathVariable Long id){
        return Result.success(interviewService.getSessionDetail(id));
    }

    @PostMapping("/{id}/finish")
    public Result<InterviewReportVO> finish(@PathVariable Long id){
        return Result.success(interviewService.finishInterview(id));
    }


}
