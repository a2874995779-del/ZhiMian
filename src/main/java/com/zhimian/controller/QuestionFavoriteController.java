package com.zhimian.controller;

import com.zhimian.common.PageResult;
import com.zhimian.common.Result;
import com.zhimian.model.dto.FavoriteQueryDTO;
import com.zhimian.model.vo.FavoriteQuestionVO;
import com.zhimian.service.QuestionFavoriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class QuestionFavoriteController {
    private final QuestionFavoriteService favoriteService;

    @GetMapping
    public Result<PageResult<FavoriteQuestionVO>> page(@Valid FavoriteQueryDTO query){
        return Result.success(favoriteService.page(query));
    }

    @PostMapping("/questions/{questionId}")
    public Result<Void> add(@PathVariable Long questionId){
        favoriteService.add(questionId);
        return Result.success();
    }

    @DeleteMapping("/questions/{questionId}")
    public Result<Void> remove(@PathVariable Long questionId){
        favoriteService.remove(questionId);
        return Result.success();
    }

    @GetMapping("/questions/{questionId}")
    public Result<Boolean> exists(@PathVariable Long questionId){
        return Result.success(favoriteService.isFavorited(questionId));
    }
}
