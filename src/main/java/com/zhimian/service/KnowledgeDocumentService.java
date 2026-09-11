package com.zhimian.service;

import com.zhimian.model.dto.KnowledgeDocumentCreateDTO;
import com.zhimian.model.entity.KnowledgeDocument;

public interface KnowledgeDocumentService {
    Long createPending(KnowledgeDocumentCreateDTO dto);

    KnowledgeDocument getById(Long id);

    void startProcessing(Long id, String embeddingModel, int vectorDimension);

    void complete(Long id,int chunkCount);

    void fail(Long id,String errorMessage);

    void beginDelete(Long id);
}
