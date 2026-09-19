package com.zhimian.rag.retrieval;

import com.zhimian.common.ErrorCode;
import com.zhimian.config.RagProperties;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.KnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalServiceImpl
        implements KnowledgeRetrievalService {

    private static final String VECTOR_ID_PREFIX = "knowledge-chunk:";
    private static final int MAX_QUERY_LENGTH = 500;
    private static final int MAX_TOP_K = 20;
    private static final int MAX_CANDIDATE_K = 50;

    private final VectorStore vectorStore;
    private final KnowledgeChunkMapper chunkMapper;
    private final RagProperties ragProperties;

    @Override
    public KnowledgeSearchResult search(
            String query,
            Integer requestedTopK,
            Double requestedThreshold) {

        String normalizedQuery = normalizeQuery(query);
        int topK = resolveTopK(requestedTopK);
        double threshold = resolveThreshold(requestedThreshold);
        int candidateK = Math.min(MAX_CANDIDATE_K, topK * 3);

        SearchRequest request = SearchRequest.builder()
                .query(normalizedQuery)
                .topK(candidateK)
                .similarityThreshold(threshold)
                .build();

        List<Document> vectorHits =
                vectorStore.similaritySearch(request);

        if (vectorHits == null || vectorHits.isEmpty()) {
            return emptyResult(normalizedQuery, topK, threshold);
        }

        LinkedHashMap<Long, ScoredCandidate> candidates =
                collectCandidates(vectorHits, threshold);

        if (candidates.isEmpty()) {
            return emptyResult(normalizedQuery, topK, threshold);
        }

        List<KnowledgeSearchRow> rows =
                chunkMapper.selectSearchableByIds(
                        new ArrayList<>(candidates.keySet()));

        Map<Long, KnowledgeSearchRow> rowByChunkId = new HashMap<>();
        for (KnowledgeSearchRow row : rows) {
            rowByChunkId.put(row.getChunkId(), row);
        }

        List<KnowledgeSearchHit> hits = new ArrayList<>();
        Set<String> seenContents = new HashSet<>();

        for (ScoredCandidate candidate : candidates.values()) {
            KnowledgeSearchRow row =
                    rowByChunkId.get(candidate.chunkId());
            if (row == null) {
                continue;
            }
            if (!seenContents.add(row.getContent())) {
                continue;
            }

            hits.add(new KnowledgeSearchHit(
                    row.getChunkId(),
                    row.getDocumentId(),
                    row.getDocumentTitle(),
                    row.getChunkIndex(),
                    row.getHeadingPath(),
                    row.getContent(),
                    candidate.score()
            ));

            if (hits.size() >= topK) {
                break;
            }
        }

        return new KnowledgeSearchResult(
                normalizedQuery,
                topK,
                threshold,
                hits
        );
    }

    private LinkedHashMap<Long, ScoredCandidate> collectCandidates(
            List<Document> vectorHits,
            double threshold) {
        LinkedHashMap<Long, ScoredCandidate> candidates =
                new LinkedHashMap<>();

        for (Document document : vectorHits) {
            Long chunkId = readChunkId(document);
            Double score = document.getScore();

            if (chunkId == null || score == null || score < threshold) {
                continue;
            }

            ScoredCandidate current = candidates.get(chunkId);
            if (current == null || score > current.score()) {
                candidates.put(
                        chunkId,
                        new ScoredCandidate(chunkId, score)
                );
            }
        }
        return candidates;
    }

    private Long readChunkId(Document document) {
        Object rawValue = document.getMetadata().get("chunkId");
        Long metadataChunkId = parseLong(rawValue);
        if (metadataChunkId != null) {
            return metadataChunkId;
        }

        String vectorId = document.getId();
        if (!StringUtils.hasText(vectorId)
                || !vectorId.startsWith(VECTOR_ID_PREFIX)) {
            return null;
        }
        return parseLong(vectorId.substring(VECTOR_ID_PREFIX.length()));
    }

    private Long parseLong(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(rawValue.toString().strip());
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            throw new BusinessException(
                    ErrorCode.PARAMS_ERROR,
                    "检索问题不能为空");
        }
        String normalized = query.strip();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new BusinessException(
                    ErrorCode.PARAMS_ERROR,
                    "检索问题最多 500 个字符");
        }
        return normalized;
    }

    private int resolveTopK(Integer requestedTopK) {
        int topK = requestedTopK == null
                ? ragProperties.getTopK()
                : requestedTopK;
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new BusinessException(
                    ErrorCode.PARAMS_ERROR,
                    "topK 必须在 1 到 20 之间");
        }
        return topK;
    }

    private double resolveThreshold(Double requestedThreshold) {
        double threshold = requestedThreshold == null
                ? ragProperties.getSimilarityThreshold()
                : requestedThreshold;
        if (!Double.isFinite(threshold)
                || threshold < 0.0
                || threshold > 1.0) {
            throw new BusinessException(
                    ErrorCode.PARAMS_ERROR,
                    "相似度阈值必须在 0 到 1 之间");
        }
        return threshold;
    }

    private KnowledgeSearchResult emptyResult(
            String query,
            int topK,
            double threshold) {
        return new KnowledgeSearchResult(
                query,
                topK,
                threshold,
                List.of()
        );
    }

    private record ScoredCandidate(Long chunkId, Double score) {
    }
}
