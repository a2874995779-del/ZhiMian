package com.zhimian.rag.ingestion;

import com.zhimian.mapper.KnowledgeChunkMapper;
import com.zhimian.model.dto.KnowledgeChunkDraft;
import com.zhimian.model.entity.KnowledgeChunk;
import com.zhimian.model.enums.KnowledgeChunkVectorStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeChunkPersistenceServiceTest {

    @Mock
    private KnowledgeChunkMapper chunkMapper;

    @InjectMocks
    private KnowledgeChunkPersistenceService service;

    @Test
    @SuppressWarnings("unchecked")
    void replacesChunksAndReturnsDatabaseRowsWithIds() {
        List<KnowledgeChunkDraft> drafts = List.of(
                draft(0, "Java", "Java 正文", "hash-0"),
                draft(1, "Java > JVM", "JVM 正文", "hash-1")
        );
        List<KnowledgeChunk> saved = List.of(
                savedChunk(101L, 10L, 0),
                savedChunk(102L, 10L, 1)
        );
        when(chunkMapper.batchInsert(anyList())).thenReturn(2);
        when(chunkMapper.selectByDocumentId(10L)).thenReturn(saved);

        List<KnowledgeChunk> result = service.replaceChunks(10L, drafts);

        ArgumentCaptor<List<KnowledgeChunk>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(chunkMapper).batchInsert(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(0))
                .extracting(
                        KnowledgeChunk::getDocumentId,
                        KnowledgeChunk::getChunkIndex,
                        KnowledgeChunk::getHeadingPath,
                        KnowledgeChunk::getContent,
                        KnowledgeChunk::getContentHash,
                        KnowledgeChunk::getTokenCount,
                        KnowledgeChunk::getVectorStatus
                )
                .containsExactly(
                        10L, 0, "Java", "Java 正文", "hash-0", 10,
                        KnowledgeChunkVectorStatus.PENDING.getCode()
                );
        assertThat(result).containsExactlyElementsOf(saved);
        assertThatThrownBy(() -> result.add(new KnowledgeChunk()))
                .isInstanceOf(UnsupportedOperationException.class);

        InOrder order = inOrder(chunkMapper);
        order.verify(chunkMapper).deleteByDocumentId(10L);
        order.verify(chunkMapper).batchInsert(anyList());
        order.verify(chunkMapper).selectByDocumentId(10L);
    }

    @Test
    void rejectsMissingInputBeforeTouchingDatabase() {
        assertThatThrownBy(() -> service.replaceChunks(null, List.of(draft(
                0, "标题", "正文", "hash"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.replaceChunks(10L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.replaceChunks(10L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(chunkMapper);
    }

    @Test
    void failsWhenBatchInsertCountIsIncomplete() {
        List<KnowledgeChunkDraft> drafts = List.of(
                draft(0, "Java", "正文一", "hash-0"),
                draft(1, "Java", "正文二", "hash-1")
        );
        when(chunkMapper.batchInsert(anyList())).thenReturn(1);

        assertThatThrownBy(() -> service.replaceChunks(10L, drafts))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("批量保存不完整");

        verify(chunkMapper, never()).selectByDocumentId(10L);
    }

    @Test
    void failsWhenReloadedChunksDoNotContainDatabaseIds() {
        List<KnowledgeChunkDraft> drafts = List.of(
                draft(0, "Java", "正文", "hash-0")
        );
        KnowledgeChunk rowWithoutId = savedChunk(null, 10L, 0);
        when(chunkMapper.batchInsert(anyList())).thenReturn(1);
        when(chunkMapper.selectByDocumentId(10L))
                .thenReturn(List.of(rowWithoutId));

        assertThatThrownBy(() -> service.replaceChunks(10L, drafts))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("回查失败");
    }

    private KnowledgeChunkDraft draft(
            int index, String path, String content, String hash) {
        return new KnowledgeChunkDraft(index, path, content, hash, 10);
    }

    private KnowledgeChunk savedChunk(Long id, Long documentId, int index) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(index);
        return chunk;
    }
}
