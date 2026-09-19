package com.zhimian.controller;

import com.zhimian.annotation.RequireAdmin;
import com.zhimian.common.Result;
import com.zhimian.model.dto.KnowledgeSearchRequestDTO;
import com.zhimian.model.vo.KnowledgeSearchResponseVO;
import com.zhimian.rag.retrieval.KnowledgeRetrievalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/rag")
@RequiredArgsConstructor
public class KnowledgeRetrievalController {
    private final KnowledgeRetrievalService retrievalService;

    @RequireAdmin
    @PostMapping("/search")
    public Result<KnowledgeSearchResponseVO> search(
            @Valid @RequestBody KnowledgeSearchRequestDTO request){
        return Result.success(
                KnowledgeSearchResponseVO.from(
                        retrievalService.search(
                                request.getQuery(),
                                request.getTopK(),
                                request.getSimilarityThreshold()
                        )
                )
        );
    }
}
