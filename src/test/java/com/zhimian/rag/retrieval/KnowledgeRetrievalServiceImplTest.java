package com.zhimian.rag.retrieval;

import com.zhimian.config.RagProperties;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.KnowledgeChunkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeRetrievalServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private KnowledgeChunkMapper chunkMapper;

    private RagProperties properties;
    private KnowledgeRetrievalServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new RagProperties();
        properties.setTopK(5);
        properties.setSimilarityThreshold(0.70);
        service = new KnowledgeRetrievalServiceImpl(
                vectorStore, chunkMapper, properties);
    }

    @Test
    void usesDefaultsAndPreservesVectorScoreOrder() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        vectorDocument(12L, 0.91),
                        vectorDocument(10L, 0.84)
                ));
        when(chunkMapper.selectSearchableByIds(List.of(12L, 10L)))
                .thenReturn(List.of(
                        row(10L, "线程池"),
                        row(12L, "缓存击穿")
                ));

        KnowledgeSearchResult result =
                service.search("  热点缓存失效  ", null, null);

        assertThat(result.query()).isEqualTo("热点缓存失效");
        assertThat(result.topK()).isEqualTo(5);
        assertThat(result.similarityThreshold()).isEqualTo(0.70);
        assertThat(result.hits())
                .extracting(KnowledgeSearchHit::chunkId)
                .containsExactly(12L, 10L);
        assertThat(result.hits())
                .extracting(KnowledgeSearchHit::content)
                .containsExactly("缓存击穿", "线程池");

        ArgumentCaptor<SearchRequest> requestCaptor =
                ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getQuery())
                .isEqualTo("热点缓存失效");
        assertThat(requestCaptor.getValue().getTopK()).isEqualTo(15);
        assertThat(requestCaptor.getValue().getSimilarityThreshold())
                .isEqualTo(0.70);
    }

    @Test
    void returnsEmptyWithoutQueryingMysqlWhenVectorStoreHasNoHits() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        KnowledgeSearchResult result =
                service.search("不存在的知识", 3, 0.60);

        assertThat(result.hits()).isEmpty();
        verifyNoInteractions(chunkMapper);
    }

    @Test
    void ignoresInvalidLowScoreAndDuplicateMetadata() {
        Document missingChunkId = Document.builder()
                .text("缺少 chunkId")
                .score(0.95)
                .build();
        Document invalidChunkId = Document.builder()
                .text("错误 chunkId")
                .metadata("chunkId", "not-a-number")
                .score(0.93)
                .build();

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        missingChunkId,
                        invalidChunkId,
                        vectorDocument(10L, 0.80),
                        vectorDocument(10L, 0.90),
                        vectorDocument(11L, 0.69)
                ));
        when(chunkMapper.selectSearchableByIds(List.of(10L)))
                .thenReturn(List.of(row(10L, "有效正文")));

        KnowledgeSearchResult result =
                service.search("线程池", 5, 0.70);

        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).chunkId()).isEqualTo(10L);
        assertThat(result.hits().get(0).score()).isEqualTo(0.90);
        verify(chunkMapper).selectSearchableByIds(List.of(10L));
    }

    @Test
    void fallsBackToStableVectorIdWhenMetadataIsNotReturned() {
        Document documentWithoutMetadata = Document.builder()
                .id("knowledge-chunk:10")
                .text("向量库中的文本")
                .score(0.88)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(documentWithoutMetadata));
        when(chunkMapper.selectSearchableByIds(List.of(10L)))
                .thenReturn(List.of(row(10L, "稳定 ID 回退成功")));

        KnowledgeSearchResult result =
                service.search("线程池", 5, 0.70);

        assertThat(result.hits())
                .singleElement()
                .satisfies(hit -> {
                    assertThat(hit.chunkId()).isEqualTo(10L);
                    assertThat(hit.content()).isEqualTo("稳定 ID 回退成功");
                });
    }

    @Test
    void filtersRowsRejectedByMysqlAndRemovesExactDuplicateContent() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        vectorDocument(10L, 0.95),
                        vectorDocument(11L, 0.90),
                        vectorDocument(12L, 0.85)
                ));
        when(chunkMapper.selectSearchableByIds(List.of(10L, 11L, 12L)))
                .thenReturn(List.of(
                        row(11L, "相同正文"),
                        row(10L, "相同正文")
                ));

        KnowledgeSearchResult result =
                service.search("并发问题", 5, 0.70);

        assertThat(result.hits())
                .singleElement()
                .satisfies(hit -> {
                    assertThat(hit.chunkId()).isEqualTo(10L);
                    assertThat(hit.score()).isEqualTo(0.95);
                });
    }

    @Test
    void limitsCandidateCountToFifty() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        service.search("系统设计", 20, 0.50);

        ArgumentCaptor<SearchRequest> requestCaptor =
                ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTopK()).isEqualTo(50);
    }

    @Test
    void rejectsInvalidQueryBeforeCallingDependencies() {
        assertThatThrownBy(() -> service.search("   ", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("检索问题不能为空");
        assertThatThrownBy(() -> service.search("x".repeat(501), null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("检索问题最多 500 个字符");

        verifyNoInteractions(vectorStore, chunkMapper);
    }

    @Test
    void rejectsInvalidTopKAndThresholdBeforeSearching() {
        assertThatThrownBy(() -> service.search("问题", 0, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topK 必须在 1 到 20 之间");
        assertThatThrownBy(() -> service.search("问题", 21, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topK 必须在 1 到 20 之间");
        assertThatThrownBy(() -> service.search("问题", 5, -0.01))
                .isInstanceOf(BusinessException.class)
                .hasMessage("相似度阈值必须在 0 到 1 之间");
        assertThatThrownBy(() -> service.search("问题", 5, 1.01))
                .isInstanceOf(BusinessException.class)
                .hasMessage("相似度阈值必须在 0 到 1 之间");
        assertThatThrownBy(() -> service.search("问题", 5, Double.NaN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("相似度阈值必须在 0 到 1 之间");
        assertThatThrownBy(() -> service.search(
                "问题", 5, Double.POSITIVE_INFINITY))
                .isInstanceOf(BusinessException.class)
                .hasMessage("相似度阈值必须在 0 到 1 之间");

        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
        verifyNoInteractions(chunkMapper);
    }

    private Document vectorDocument(Long chunkId, double score) {
        return Document.builder()
                .id("knowledge-chunk:" + chunkId)
                .text("向量库中的文本")
                .metadata("chunkId", chunkId.toString())
                .score(score)
                .build();
    }

    private KnowledgeSearchRow row(Long chunkId, String content) {
        KnowledgeSearchRow row = new KnowledgeSearchRow();
        row.setChunkId(chunkId);
        row.setDocumentId(15L);
        row.setDocumentTitle("测试文档");
        row.setSourceType("MARKDOWN");
        row.setChunkIndex(chunkId.intValue());
        row.setHeadingPath("测试路径 > " + chunkId);
        row.setContent(content);
        row.setContentHash("hash-" + chunkId);
        row.setTokenCount(20);
        return row;
    }
}
