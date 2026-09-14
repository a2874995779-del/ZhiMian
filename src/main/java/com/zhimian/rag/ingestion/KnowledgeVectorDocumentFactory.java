package com.zhimian.rag.ingestion;

import com.zhimian.model.entity.KnowledgeChunk;
import com.zhimian.model.entity.KnowledgeDocument;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import org.springframework.ai.document.Document;
import java.util.HashMap;
import java.util.Map;

@Component
public class KnowledgeVectorDocumentFactory {
    private static final String VECTOR_ID_PREFIX = "knowledge-chunk:";

    public Document create(KnowledgeDocument source, KnowledgeChunk chunk){
        if(source == null || source.getId() == null){
            throw new IllegalArgumentException("知识文档不能为空");
        }
        if(chunk == null || chunk.getId() == null){
            throw new IllegalArgumentException("知识片段不能为空");
        }

        String headingPath = StringUtils.hasText(chunk.getHeadingPath())
                ? chunk.getHeadingPath().strip()
                : source.getTitle();

        String embeddingText = "知识路径："
                +headingPath
                +"\n\n"
                +chunk.getContent();

        Map<String,Object> metadata = new HashMap<>();
        metadata.put("documentId", source.getId().toString());
        metadata.put("chunkId", chunk.getId().toString());
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.put("headingPath", headingPath);
        metadata.put("documentTitle", source.getTitle());
        metadata.put("sourceType", source.getSourceType());

        return Document.builder()
                .id(vectorId(chunk.getId()))
                .text(embeddingText)
                .metadata(metadata)
                .build();
    }

    public String vectorId(Long chunkId) {
        if(chunkId == null){
            throw new IllegalArgumentException("chunkId 不能为空");
        }
        return VECTOR_ID_PREFIX + chunkId;
    }
}
