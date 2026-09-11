package com.zhimian.mapper;

import com.zhimian.model.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeDocumentMapper {
    int insert(KnowledgeDocument document);

    KnowledgeDocument selectById(@Param("id") Long id);

    KnowledgeDocument selectActiveByContentHash(@Param("contentHash") String contentHash);

    int markProcessing(@Param("id") Long id,
                       @Param("embeddingModel") String embeddingModel,
                       @Param("vectorDimension") Integer vectorDimension);

    int markCompleted(@Param("id") Long id,
                      @Param("chunkCount") Integer chunkCount);

    int markFailed(@Param("id") Long id,
                   @Param("errorMessage")String errorMessage);

    int markDeleting(@Param("id") Long id);

    int softDelete(@Param("id") Long id);
}
