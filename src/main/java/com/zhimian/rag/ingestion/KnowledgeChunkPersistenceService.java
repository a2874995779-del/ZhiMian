package com.zhimian.rag.ingestion;

import com.zhimian.mapper.KnowledgeChunkMapper;
import com.zhimian.model.dto.KnowledgeChunkDraft;
import com.zhimian.model.entity.KnowledgeChunk;
import com.zhimian.model.enums.KnowledgeChunkVectorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
/*
负责把 KnowledgeChunkDraft 保存到 MySQL
 */
@Service
@RequiredArgsConstructor
public class KnowledgeChunkPersistenceService {
    private final KnowledgeChunkMapper chunkMapper;

    @Transactional
    public List<KnowledgeChunk> replaceChunks(
            Long documentId,
            List<KnowledgeChunkDraft> drafts){
        if(documentId == null){
            throw new IllegalArgumentException("documentId 不能为空");
        }
        if(drafts == null || drafts.isEmpty()){
            throw new IllegalArgumentException("切片结果不能为空");
        }

        chunkMapper.deleteByDocumentId(documentId);

        List<KnowledgeChunk> chunks = drafts.stream()
                .map(draft -> toEntity(documentId,draft))
                .toList();

        int inserted = chunkMapper.batchInsert(chunks);
        if(inserted != chunks.size()){
            throw new IllegalStateException("知识片段批量保存不完整");
        }
        List<KnowledgeChunk> saved = chunkMapper.selectByDocumentId(documentId);
        if(saved.size() != chunks.size() || saved.stream().anyMatch(chunk -> chunk.getId() == null)){
            throw new IllegalStateException("知识片段保存后回查失败");
        }
        return List.copyOf(saved);
    }

    private KnowledgeChunk toEntity(Long documentId, KnowledgeChunkDraft draft) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(draft.chunkIndex());
        chunk.setHeadingPath(draft.headingPath());
        chunk.setContent(draft.content());
        chunk.setContentHash(draft.contentHash());
        chunk.setTokenCount(draft.tokenCount());
        chunk.setVectorStatus(
                KnowledgeChunkVectorStatus.PENDING.getCode());
        return chunk;
    }


}
