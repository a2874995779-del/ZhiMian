package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.model.dto.CreateInterviewDTO;
import com.zhimian.model.vo.InterviewSessionVO;
import com.zhimian.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {
    private final InterviewService interviewService;

    @PostMapping
    public Result<InterviewSessionVO> create(@Valid @RequestBody CreateInterviewDTO dto){
        return Result.success(interviewService.createInterview(dto));
    }
}
