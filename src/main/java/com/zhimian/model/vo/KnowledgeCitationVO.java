package com.zhimian.model.vo;

public record KnowledgeCitationVO(
        int index,
        Long chunkId,
        Long documentId,
        String documentTitle,
        String headingPath,
        Double score,
        String excerpt
) {
}
