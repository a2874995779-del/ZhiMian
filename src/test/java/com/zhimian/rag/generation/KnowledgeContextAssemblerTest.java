package com.zhimian.rag.generation;

import com.zhimian.config.RagProperties;
import com.zhimian.rag.retrieval.KnowledgeSearchHit;
import com.zhimian.rag.support.TokenWindowSplitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeContextAssemblerTest {

    @Mock
    private TokenWindowSplitter tokenCounter;

    private RagProperties properties;
    private KnowledgeContextAssembler assembler;

    @BeforeEach
    void setUp() {
        properties = new RagProperties();
        properties.setMaxContextTokens(100);
        properties.setMaxCitations(2);
        assembler = new KnowledgeContextAssembler(tokenCounter, properties);
    }

    @Test
    void returnsEmptyContextWithoutCountingTokensForNoHits() {
        AssembledKnowledgeContext result = assembler.assemble(List.of());

        assertThat(result.hasKnowledge()).isFalse();
        assertThat(result.promptContext()).isEmpty();
        assertThat(result.tokenCount()).isZero();
        verify(tokenCounter, never()).countTokens(anyString());
    }

    @Test
    void keepsRetrievalOrderAndLimitsCitationCount() {
        when(tokenCounter.countTokens(anyString())).thenReturn(20);

        AssembledKnowledgeContext result = assembler.assemble(List.of(
                hit(12L, "缓存击穿"),
                hit(10L, "缓存穿透"),
                hit(9L, "缓存雪崩")
        ));

        assertThat(result.includedHits())
                .extracting(KnowledgeSearchHit::chunkId)
                .containsExactly(12L, 10L);
        assertThat(result.promptContext())
                .contains("[资料1]", "缓存击穿")
                .contains("[资料2]", "缓存穿透")
                .doesNotContain("缓存雪崩");
        assertThat(result.tokenCount()).isEqualTo(40);
    }

    @Test
    void stopsBeforeExceedingTokenBudget() {
        properties.setMaxContextTokens(30);
        when(tokenCounter.countTokens(anyString())).thenReturn(20);

        AssembledKnowledgeContext result = assembler.assemble(List.of(
                hit(12L, "缓存击穿"),
                hit(10L, "缓存穿透")
        ));

        assertThat(result.includedHits())
                .extracting(KnowledgeSearchHit::chunkId)
                .containsExactly(12L);
        assertThat(result.tokenCount()).isEqualTo(20);
    }

    @Test
    void skipsBlankHitsAndUsesFallbackHeading() {
        when(tokenCounter.countTokens(anyString())).thenReturn(10);
        KnowledgeSearchHit blank = hit(1L, "   ");
        KnowledgeSearchHit withoutHeading = new KnowledgeSearchHit(
                2L,
                6L,
                "缓存资料",
                2,
                null,
                "有效正文",
                0.80
        );

        AssembledKnowledgeContext result = assembler.assemble(
                List.of(blank, withoutHeading)
        );

        assertThat(result.includedHits())
                .extracting(KnowledgeSearchHit::chunkId)
                .containsExactly(2L);
        assertThat(result.promptContext()).contains("未命名章节");
    }

    @Test
    void rejectsInvalidContextConfiguration() {
        properties.setMaxContextTokens(0);

        assertThatThrownBy(() -> assembler.assemble(List.of(
                hit(12L, "缓存击穿")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RAG上下文配置必须大于0");
    }

    private KnowledgeSearchHit hit(Long chunkId, String content) {
        return new KnowledgeSearchHit(
                chunkId,
                6L,
                "Java 高并发与 Redis 缓存",
                chunkId.intValue(),
                "缓存 > 常见问题",
                content,
                0.90
        );
    }
}
