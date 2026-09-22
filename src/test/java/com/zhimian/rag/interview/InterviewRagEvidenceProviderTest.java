package com.zhimian.rag.interview;

import com.zhimian.config.RagProperties;
import com.zhimian.model.interview.InterviewPlanItem;
import com.zhimian.rag.generation.AssembledKnowledgeContext;
import com.zhimian.rag.generation.KnowledgeContextAssembler;
import com.zhimian.rag.retrieval.InterviewRagEvidence;
import com.zhimian.rag.retrieval.KnowledgeRetrievalService;
import com.zhimian.rag.retrieval.KnowledgeSearchHit;
import com.zhimian.rag.retrieval.KnowledgeSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewRagEvidenceProviderTest {

    @Mock
    private KnowledgeRetrievalService retrievalService;
    @Mock
    private KnowledgeContextAssembler contextAssembler;

    private RagProperties properties;
    private InterviewRagEvidenceProvider provider;

    @BeforeEach
    void setUp() {
        properties = new RagProperties();
        properties.setTopK(5);
        properties.setSimilarityThreshold(0.70);
        provider = new InterviewRagEvidenceProvider(
                properties,
                new InterviewKnowledgeQueryBuilder(),
                retrievalService,
                contextAssembler
        );
    }

    @Test
    void returnsDisabledWithoutCallingRetrieval() {
        properties.setEnabled(false);

        InterviewRagEvidence evidence = provider.load("什么是索引？", item());

        assertThat(evidence.status())
                .isEqualTo(InterviewRagEvidenceStatus.RAG_DISABLED);
        assertThat(evidence.applied()).isFalse();
        verify(retrievalService, never()).search("什么是索引？", 5, 0.70);
    }

    @Test
    void returnsAppliedEvidenceWhenKnowledgeIsFound() {
        KnowledgeSearchHit hit = hit();
        when(retrievalService.search("MySQL；索引和查询；工程实践；什么是索引？", 5, 0.70))
                .thenReturn(new KnowledgeSearchResult(
                        "query", 5, 0.70, List.of(hit)));
        when(contextAssembler.assemble(List.of(hit)))
                .thenReturn(new AssembledKnowledgeContext(
                        "[资料1] 索引知识", 10, List.of(hit)));

        InterviewRagEvidence evidence = provider.load("什么是索引？", item());

        assertThat(evidence.status()).isEqualTo(InterviewRagEvidenceStatus.APPLIED);
        assertThat(evidence.applied()).isTrue();
        assertThat(evidence.promptContext()).contains("索引知识");
        assertThat(evidence.sources()).containsExactly(hit);
    }

    @Test
    void degradesWhenThereIsNoRelevantKnowledge() {
        when(retrievalService.search(
                "MySQL；索引和查询；工程实践；什么是索引？", 5, 0.70))
                .thenReturn(new KnowledgeSearchResult(
                        "query", 5, 0.70, List.of()));

        InterviewRagEvidence evidence = provider.load("什么是索引？", item());

        assertThat(evidence.status())
                .isEqualTo(InterviewRagEvidenceStatus.NO_RELEVANT_KNOWLEDGE);
        verify(contextAssembler, never()).assemble(List.of());
    }

    @Test
    void degradesOnlyKnownRedisFailure() {
        when(retrievalService.search(
                "MySQL；索引和查询；工程实践；什么是索引？", 5, 0.70))
                .thenThrow(new DataAccessResourceFailureException(
                        "Redis unavailable"));

        InterviewRagEvidence evidence = provider.load("什么是索引？", item());

        assertThat(evidence.status())
                .isEqualTo(InterviewRagEvidenceStatus.RETRIEVAL_UNAVAILABLE);
    }

    private InterviewPlanItem item() {
        return new InterviewPlanItem(
                1, "mysql", "MySQL", "索引和查询", "工程实践", 2, "原题", 1);
    }

    private KnowledgeSearchHit hit() {
        return new KnowledgeSearchHit(
                1L, 2L, "MySQL 知识", 0, "索引", "索引知识", 0.90);
    }
}
