package com.zhimian.controller;

import com.zhimian.annotation.Public;
import com.zhimian.common.Result;
import com.zhimian.model.vo.RankVO;
import com.zhimian.service.AnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ranks")
@RequiredArgsConstructor
public class RankController {
    private final AnswerService answerService;

    @Public
    @GetMapping("/answer")
    public Result<List<RankVO>> rank(@RequestParam(defaultValue = "total")String type,
                                     @RequestParam(defaultValue = "10")int limit){
        return Result.success(answerService.getRank(type,limit));
    }
}
