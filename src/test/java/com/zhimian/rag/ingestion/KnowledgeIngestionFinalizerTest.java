package com.zhimian.rag.ingestion;

import com.zhimian.mapper.KnowledgeChunkMapper;
import com.zhimian.service.KnowledgeDocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionFinalizerTest {

    @Mock
    private KnowledgeChunkMapper chunkMapper;

    @Mock
    private KnowledgeDocumentService documentService;

    @InjectMocks
    private KnowledgeIngestionFinalizer finalizer;

    @Test
    void completesChunkAndDocumentStatusesTogether() {
        List<Long> chunkIds = List.of(11L, 12L);
        when(chunkMapper.markIndexed(chunkIds)).thenReturn(2);

        finalizer.complete(10L, chunkIds);

        InOrder order = inOrder(chunkMapper, documentService);
        order.verify(chunkMapper).markIndexed(chunkIds);
        order.verify(documentService).complete(10L, 2);
    }

    @Test
    void rejectsEmptyChunkIdsBeforeUpdatingState() {
        assertThatThrownBy(() -> finalizer.complete(10L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> finalizer.complete(10L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(chunkMapper, documentService);
    }

    @Test
    void doesNotCompleteDocumentWhenChunkUpdateIsIncomplete() {
        List<Long> chunkIds = List.of(11L, 12L);
        when(chunkMapper.markIndexed(chunkIds)).thenReturn(1);

        assertThatThrownBy(() -> finalizer.complete(10L, chunkIds))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("状态更新不完整");

        verify(documentService, never()).complete(10L, 2);
    }

    @Test
    void marksAllChunksAndDocumentFailed() {
        finalizer.fail(10L, "Embedding API 超时");

        InOrder order = inOrder(chunkMapper, documentService);
        order.verify(chunkMapper).markAllFailedByDocumentId(
                10L, "Embedding API 超时");
        order.verify(documentService).fail(10L, "Embedding API 超时");
    }
}
