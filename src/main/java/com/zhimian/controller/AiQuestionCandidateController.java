package com.zhimian.controller;

import com.zhimian.annotation.RequireAdmin;
import com.zhimian.common.PageResult;
import com.zhimian.common.Result;
import com.zhimian.model.dto.AiQuestionApproveDTO;
import com.zhimian.model.dto.AiQuestionCandidateQueryDTO;
import com.zhimian.model.vo.AiQuestionCandidateVO;
import com.zhimian.service.AiQuestionCandidateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ai-question-candidates")
@RequiredArgsConstructor
public class AiQuestionCandidateController {
    private final AiQuestionCandidateService candidateService;

    @RequireAdmin
    @GetMapping
    public Result<PageResult<AiQuestionCandidateVO>> page(
            @Valid AiQuestionCandidateQueryDTO query) {
        return Result.success(candidateService.page(query));
    }

    @RequireAdmin
    @PutMapping("/{id}/approve")
    public Result<Long> approve(@PathVariable Long id,
                                @Valid @RequestBody AiQuestionApproveDTO dto) {
        return Result.success(candidateService.approve(id, dto));
    }

    @RequireAdmin
    @PutMapping("/{id}/ignore")
    public Result<Void> ignore(@PathVariable Long id) {
        candidateService.ignore(id);
        return Result.success();
    }
}
