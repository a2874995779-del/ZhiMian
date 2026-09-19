package com.zhimian.mapper;

import com.zhimian.model.entity.KnowledgeChunk;
import com.zhimian.rag.retrieval.KnowledgeSearchRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper {
    int batchInsert(@Param("chunks") List<KnowledgeChunk> chunks);

    KnowledgeChunk selectById(@Param("id") Long id);

    List<KnowledgeChunk> selectByDocumentId(
            @Param("documentId") Long documentId);

    int markIndexed(@Param("ids") List<Long> ids);

    int markFailed(@Param("id") Long id,
                   @Param("errorMessage") String errorMessage);

    int softDeleteByDocumentId(@Param("documentId") Long documentId);

    int deleteByDocumentId(@Param("documentId") Long documentId);

    int markAllFailedByDocumentId(
            @Param("documentId") Long documentId,
            @Param("errorMessage") String errorMessage
    );

    List<KnowledgeSearchRow> selectSearchableByIds(@Param("ids") List<Long> ids);
}
