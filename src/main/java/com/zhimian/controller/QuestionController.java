package com.zhimian.controller;

import com.zhimian.annotation.Public;
import com.zhimian.annotation.RequireAdmin;
import com.zhimian.common.PageResult;
import com.zhimian.common.Result;
import com.zhimian.model.dto.QuestionAddDTO;
import com.zhimian.model.dto.QuestionQueryDTO;
import com.zhimian.model.vo.QuestionDetailVO;
import com.zhimian.model.vo.QuestionListVO;
import com.zhimian.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {
    private final QuestionService questionService;

    @RequireAdmin
    @PostMapping
    public Result<Long> add(@Valid @RequestBody QuestionAddDTO dto) {
        return Result.success(questionService.addQuestion(dto));
    }

    @RequireAdmin
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,@Valid @RequestBody QuestionAddDTO dto) {
        questionService.updateQuestion(id,dto);
        return Result.success();
    }

    @RequireAdmin
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        questionService.deleteQuestion(id);
        return Result.success();
    }

    @Public
    @GetMapping
    public  Result<PageResult<QuestionListVO>> page(@Valid QuestionQueryDTO dto){
        return Result.success(questionService.pageQuestions(dto));
    }

    @Public
    @GetMapping("/{id}")
    public Result<QuestionDetailVO> detail(@PathVariable Long id){
        return Result.success(questionService.getDetail(id));
    }

    @Public
    @GetMapping("/hot")
    public Result<List<QuestionListVO>> hot(){
        return Result.success(questionService.getHotQuestions());
    }

}
