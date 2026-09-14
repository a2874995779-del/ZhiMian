package com.zhimian.controller;

import com.zhimian.annotation.RequireAdmin;
import com.zhimian.common.Result;
import com.zhimian.common.UserContext;
import com.zhimian.model.dto.KnowledgeDocumentCreateDTO;
import com.zhimian.model.dto.KnowledgeDocumentImportDTO;
import com.zhimian.model.entity.KnowledgeDocument;
import com.zhimian.model.vo.KnowledgeDocumentStatusVO;
import com.zhimian.service.KnowledgeDocumentService;
import com.zhimian.service.KnowledgeIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/knowledge-documents")
@RequiredArgsConstructor
public class KnowledgeDocumentController {
    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeDocumentService documentService;

    @RequireAdmin
    @PostMapping
    public Result<Long> submit(
            @Valid @RequestBody KnowledgeDocumentImportDTO request){
        KnowledgeDocumentCreateDTO command = new KnowledgeDocumentCreateDTO();
        command.setTitle(request.getTitle());
        command.setOriginalFilename(request.getOriginalFilename());
        command.setSourceType(request.getSourceType());
        command.setContent(request.getContent());
        command.setCreatedBy(UserContext.getUserId());
        return Result.success(ingestionService.submit(command));
    }

    @RequireAdmin
    @PutMapping("/{id}/retry")
    public Result<Void> retry(@PathVariable Long id){
        ingestionService.retry(id);
        return Result.success();
    }

    @RequireAdmin
    @GetMapping("/{id}")
    public Result<KnowledgeDocumentStatusVO> status(@PathVariable Long id){
        return Result.success(KnowledgeDocumentStatusVO.from(documentService.getById(id)));
    }

}
