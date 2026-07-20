package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.model.dto.AnswerSubmitDTO;
import com.zhimian.service.AnswerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class AnswerController {
    private final AnswerService answerService;

    @PostMapping("/{id}/answer")
    public Result<Void> submit(@PathVariable long id, @Valid @RequestBody AnswerSubmitDTO dto){
        answerService.submitAnswer(id,dto);
        return Result.success();
    }
}
