package com.zhimian.rag.ingestion;

import com.zhimian.mapper.KnowledgeChunkMapper;
import com.zhimian.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeIngestionFinalizer {
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentService documentService;

    @Transactional
    public void complete(Long documentId, List<Long> chunkIds){
        if(chunkIds == null || chunkIds.isEmpty()){
            throw new IllegalArgumentException("chunkIds 不能为空");
        }
        int updated = chunkMapper.markIndexed(chunkIds);
        if(updated !=chunkIds.size()){
            throw new IllegalStateException("知识片段索引状态更新不完整");
        }
        documentService.complete(documentId,chunkIds.size());
    }

    @Transactional
    public void fail(Long documentId,String errorMessage){
        chunkMapper.markAllFailedByDocumentId(documentId,errorMessage);
        documentService.fail(documentId,errorMessage);
    }
}
