package com.zhimian.rag.retrieval;

public interface KnowledgeRetrievalService {
    KnowledgeSearchResult search(
            String query,
            Integer topK,
            Double similarityThreshold);
}
