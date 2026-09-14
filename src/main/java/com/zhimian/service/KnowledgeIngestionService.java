package com.zhimian.service;

import com.zhimian.model.dto.KnowledgeDocumentCreateDTO;

public interface KnowledgeIngestionService {
    Long submit(KnowledgeDocumentCreateDTO command);

    void retry(Long id);
}
