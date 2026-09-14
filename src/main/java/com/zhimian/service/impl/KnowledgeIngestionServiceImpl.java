package com.zhimian.service.impl;

import com.zhimian.config.RagProperties;
import com.zhimian.model.dto.KnowledgeDocumentCreateDTO;
import com.zhimian.rag.ingestion.KnowledgeIngestionLauncher;
import com.zhimian.service.KnowledgeDocumentService;
import com.zhimian.service.KnowledgeIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class KnowledgeIngestionServiceImpl implements KnowledgeIngestionService {
    private final KnowledgeDocumentService documentService;
    private final KnowledgeIngestionLauncher launcher;
    private final RagProperties ragProperties;


    @Override
    public Long submit(KnowledgeDocumentCreateDTO dto) {
        validateModelConfiguration();
        Long documentId = documentService.createPending(dto);
        startAndDispatch(documentId);
        return documentId;
    }

    @Override
    public void retry(Long documentId) {
        validateModelConfiguration();
        documentService.getById(documentId);
        startAndDispatch(documentId);
    }

    private void startAndDispatch(Long documentId) {
        documentService.startProcessing(
                documentId,ragProperties.getEmbeddingModel(),ragProperties.getEmbeddingDimensions()
        );
        if (!launcher.submit(documentId)){
            documentService.fail(
                    documentId,"RAG任务队列已满，请稍后再试"
            );
        }
    }

    private void validateModelConfiguration() {
        if(!StringUtils.hasText(ragProperties.getEmbeddingModel()) || ragProperties.getEmbeddingDimensions() <=0){
            throw new IllegalStateException("RAG Embedding模型配置不完整");
        }
    }
}
