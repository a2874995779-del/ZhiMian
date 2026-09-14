package com.zhimian.rag.ingestion;

import com.zhimian.config.RagProperties;
import com.zhimian.mapper.KnowledgeChunkMapper;
import com.zhimian.model.dto.KnowledgeChunkDraft;
import com.zhimian.model.entity.KnowledgeChunk;
import com.zhimian.model.entity.KnowledgeDocument;
import com.zhimian.model.enums.KnowledgeDocumentStatus;
import com.zhimian.rag.support.MarkdownChunker;
import com.zhimian.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class KnowledgeIngestionWorker {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final KnowledgeDocumentService documentService;
    private final KnowledgeChunkMapper chunkMapper;
    private final MarkdownChunker markdownChunker;
    private final KnowledgeChunkPersistenceService persistenceService;
    private final KnowledgeVectorDocumentFactory documentFactory;
    private final KnowledgeIngestionFinalizer finalizer;
    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    public void process(Long documentId){
        List<String> currentVectorIds = List.of();
        try {
            KnowledgeDocument source = documentService.getById(documentId);
            ensureProcessing(source);

            List<KnowledgeChunk> previousChunks = chunkMapper.selectByDocumentId(documentId);
            deleteVectors(previousChunks.stream()
                    .map(chunk -> documentFactory.vectorId(chunk.getId()))
                    .toList());

            List<KnowledgeChunkDraft> drafts = markdownChunker.chunk(
                    source.getTitle(),
                    source.getSourceType(),
                    source.getContent()
            );

            List<KnowledgeChunk> chunks = persistenceService.replaceChunks(documentId,drafts);

            List<Document> vectorDocuments = chunks.stream()
                    .map(chunk -> documentFactory.create(source,chunk))
                    .toList();

            currentVectorIds = vectorDocuments.stream()
                    .map(Document:: getId)
                    .toList();

            addInBatches(vectorDocuments);

            List<Long> chunkIds = chunks.stream()
                    .map(KnowledgeChunk::getId)
                    .toList();

            finalizer.complete(documentId,chunkIds);
            log.info("RAG知识文档处理完成: documentId={}, chunks={}",
                    documentId, chunks.size());
        }catch (Exception e){
            String errorMessage = safeErrorMessage(e);
            bestEffortDelete(currentVectorIds);
            markFailedSafely(documentId,errorMessage);
            log.error("RAG知识文档处理失败: documentId={}",
                    documentId, e);
        }
    }

    private void addInBatches(List<Document> documents) {
        int batchSize = ragProperties.getVectorBatchSize();
        if(batchSize <=0){
            throw new IllegalStateException("vectorBatchSize必须大于0");
        }
        for(int from =0;from<documents.size();from+=batchSize){
            int to = Math.min(from + batchSize, documents.size());
            vectorStore.add(new ArrayList<>(documents.subList(from,to)));
        }
    }

    private void markFailedSafely(Long documentId, String errorMessage) {
        try {
            finalizer.fail(documentId,errorMessage);
        }catch (Exception stateException){
            log.error("RAG失败状态更新失败: documentId={}",documentId,stateException);
        }
    }

    private void bestEffortDelete(List<String> vectorIds) {
        try {
            deleteVectors(vectorIds);
        } catch (Exception cleanupException) {
            log.error("RAG失败补偿未能清理向量: ids={}",
                    vectorIds, cleanupException);
        }
    }

    private String safeErrorMessage(Exception exception) {
        String message = exception.getClass().getSimpleName();
        if(StringUtils.hasText(exception.getMessage())){
            message += ": " + exception.getMessage().strip();
        }
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0,MAX_ERROR_MESSAGE_LENGTH);
    }

    private void deleteVectors(List<String> vectorIds) {
        if(vectorIds !=null && !vectorIds.isEmpty()){
            vectorStore.delete(vectorIds);
        }
    }

    private void ensureProcessing(KnowledgeDocument source) {
        if(!Integer.valueOf(KnowledgeDocumentStatus.PROCESSING.getCode()).equals(source.getStatus())){
            throw new IllegalStateException("知识文档不处于处理中状态");
        }
    }
}
