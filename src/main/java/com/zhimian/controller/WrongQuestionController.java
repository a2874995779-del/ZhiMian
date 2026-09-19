package com.zhimian.controller;

import com.zhimian.common.PageResult;
import com.zhimian.common.Result;
import com.zhimian.model.dto.WrongQuestionQueryDTO;
import com.zhimian.model.vo.WrongQuestionExplanationVO;
import com.zhimian.model.vo.WrongQuestionVO;
import com.zhimian.service.WrongQuestionExplanationService;
import com.zhimian.service.WrongQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wrong-questions")
@RequiredArgsConstructor
public class WrongQuestionController {
    private final WrongQuestionService wrongQuestionService;
    private final WrongQuestionExplanationService explanationService;

    @GetMapping
    public Result<PageResult<WrongQuestionVO>> page(@Valid WrongQuestionQueryDTO dto){
        return Result.success(wrongQuestionService.page(dto));
    }
    @PutMapping("/{questionId}/unmastered")
    public Result<Void> markUnmastered(@PathVariable Long questionId){
        wrongQuestionService.markUnmastered(questionId);
        return Result.success();
    }

    @PutMapping("/{questionId}/mastered")
    public Result<Void> markMastered(@PathVariable Long questionId) {
        wrongQuestionService.markMastered(questionId);
        return Result.success();
    }

    @DeleteMapping("/{questionId}")
    public Result<Void> remove(@PathVariable Long questionId){
        wrongQuestionService.remove(questionId);
        return Result.success();
    }

    @PostMapping("/{questionId}/explanation")
    public Result<WrongQuestionExplanationVO> explain(@PathVariable Long questionId){
        return Result.success(explanationService.explain(questionId));
    }
}
