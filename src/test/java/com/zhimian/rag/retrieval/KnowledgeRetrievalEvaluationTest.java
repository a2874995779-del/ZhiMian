package com.zhimian.rag.retrieval;

import com.zhimian.ZhiMianApplication;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ZhiMianApplication.class)
@EnabledIfEnvironmentVariable(
        named = "RUN_RAG_EVALUATION_TESTS",
        matches = "true")
class KnowledgeRetrievalEvaluationTest {

    @Autowired
    private KnowledgeRetrievalService retrievalService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void importedKnowledgeShouldMeetRetrievalBaseline() throws Exception {
        List<EvaluationCase> cases = loadCases();
        assertThat(cases).isNotEmpty();

        int hitCount = 0;
        double reciprocalRankSum = 0.0;

        for (EvaluationCase evaluationCase : cases) {
            KnowledgeSearchResult result = retrievalService.search(
                    evaluationCase.query(), 5, 0.50);
            int rank = firstRelevantRank(
                    result.hits(),
                    evaluationCase.expectedHeadingContains());

            if (rank > 0) {
                hitCount++;
                reciprocalRankSum += 1.0 / rank;
            }

            System.out.printf(
                    "case=%s rank=%d top=%s%n",
                    evaluationCase.id(),
                    rank,
                    result.hits().isEmpty()
                            ? "NONE"
                            : result.hits().get(0).headingPath()
            );
        }

        double hitAt5 = (double) hitCount / cases.size();
        double mrr = reciprocalRankSum / cases.size();
        System.out.printf(
                "Retrieval baseline: Hit@5=%.3f, MRR=%.3f%n",
                hitAt5,
                mrr
        );

        assertThat(hitAt5).isGreaterThanOrEqualTo(0.80);
        assertThat(mrr).isGreaterThanOrEqualTo(0.60);
    }

    private List<EvaluationCase> loadCases() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/rag/retrieval-evaluation.json")) {
            assertThat(input)
                    .as("检索评估集必须存在")
                    .isNotNull();
            return objectMapper.readValue(input, new TypeReference<>() {
            });
        }
    }

    private int firstRelevantRank(
            List<KnowledgeSearchHit> hits,
            String expectedHeadingContains) {
        for (int index = 0; index < hits.size(); index++) {
            if (hits.get(index).headingPath()
                    .contains(expectedHeadingContains)) {
                return index + 1;
            }
        }
        return 0;
    }

    private record EvaluationCase(
            String id,
            String query,
            String expectedHeadingContains
    ) {
    }
}
