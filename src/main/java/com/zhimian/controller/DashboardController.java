package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.model.vo.DashboardVO;
import com.zhimian.service.AnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final AnswerService answerService;

    @GetMapping
    public Result<DashboardVO> getDashboard() {
        return Result.success(answerService.getDashboard());
    }
}
