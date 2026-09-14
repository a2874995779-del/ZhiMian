package com.zhimian.rag.ingestion;

import com.zhimian.config.RagProperties;
import com.zhimian.mapper.KnowledgeChunkMapper;
import com.zhimian.model.dto.KnowledgeChunkDraft;
import com.zhimian.model.entity.KnowledgeChunk;
import com.zhimian.model.entity.KnowledgeDocument;
import com.zhimian.model.enums.KnowledgeDocumentStatus;
import com.zhimian.rag.support.MarkdownChunker;
import com.zhimian.service.KnowledgeDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionWorkerTest {

    @Mock
    private KnowledgeDocumentService documentService;

    @Mock
    private KnowledgeChunkMapper chunkMapper;

    @Mock
    private MarkdownChunker markdownChunker;

    @Mock
    private KnowledgeChunkPersistenceService persistenceService;

    @Mock
    private KnowledgeIngestionFinalizer finalizer;

    @Mock
    private VectorStore vectorStore;

    private RagProperties properties;
    private KnowledgeVectorDocumentFactory documentFactory;
    private KnowledgeIngestionWorker worker;

    @BeforeEach
    void setUp() {
        properties = new RagProperties();
        properties.setVectorBatchSize(1);
        documentFactory = new KnowledgeVectorDocumentFactory();
        worker = new KnowledgeIngestionWorker(
                documentService,
                chunkMapper,
                markdownChunker,
                persistenceService,
                documentFactory,
                finalizer,
                vectorStore,
                properties
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void processesDocumentInBatchesAndCompletesStatus() {
        KnowledgeDocument source = processingDocument();
        KnowledgeChunk previous = chunk(8L, 0, "旧内容");
        List<KnowledgeChunkDraft> drafts = drafts();
        List<KnowledgeChunk> chunks = chunks();
        stubPipeline(source, List.of(previous), drafts, chunks);

        worker.process(10L);

        verify(vectorStore).delete(List.of("knowledge-chunk:8"));

        ArgumentCaptor<List<Document>> batches =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore, org.mockito.Mockito.times(2))
                .add(batches.capture());
        assertThat(batches.getAllValues()).allSatisfy(batch ->
                assertThat(batch).hasSize(1));
        assertThat(batches.getAllValues())
                .flatExtracting(batch -> batch)
                .extracting(Document::getId)
                .containsExactly("knowledge-chunk:11", "knowledge-chunk:12");

        InOrder completionOrder = inOrder(vectorStore, finalizer);
        completionOrder.verify(vectorStore, org.mockito.Mockito.times(2))
                .add(anyList());
        completionOrder.verify(finalizer)
                .complete(10L, List.of(11L, 12L));
        verify(finalizer, never()).fail(eq(10L), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void compensatesAllCurrentVectorIdsWhenVectorWriteFails() {
        KnowledgeDocument source = processingDocument();
        List<KnowledgeChunkDraft> drafts = drafts();
        List<KnowledgeChunk> chunks = chunks();
        stubPipeline(source, List.of(), drafts, chunks);
        doThrow(new IllegalStateException("Embedding API timeout"))
                .when(vectorStore).add(anyList());

        worker.process(10L);

        verify(vectorStore).delete(List.of(
                "knowledge-chunk:11", "knowledge-chunk:12"));
        verify(finalizer).fail(
                eq(10L),
                argThat(message -> message.contains("Embedding API timeout"))
        );
        verify(finalizer, never()).complete(eq(10L), anyList());
    }

    @Test
    void compensatesVectorsWhenFinalMysqlTransitionFails() {
        KnowledgeDocument source = processingDocument();
        List<KnowledgeChunkDraft> drafts = drafts();
        List<KnowledgeChunk> chunks = chunks();
        stubPipeline(source, List.of(), drafts, chunks);
        doThrow(new IllegalStateException("状态已变化"))
                .when(finalizer).complete(10L, List.of(11L, 12L));

        worker.process(10L);

        verify(vectorStore).delete(List.of(
                "knowledge-chunk:11", "knowledge-chunk:12"));
        verify(finalizer).fail(
                eq(10L),
                argThat(message -> message.contains("状态已变化"))
        );
    }

    @Test
    void rejectsNonProcessingDocumentBeforeChunking() {
        KnowledgeDocument source = processingDocument();
        source.setStatus(KnowledgeDocumentStatus.COMPLETED.getCode());
        when(documentService.getById(10L)).thenReturn(source);

        worker.process(10L);

        verify(finalizer).fail(
                eq(10L),
                argThat(message -> message.contains("不处于处理中状态"))
        );
        verifyNoInteractions(markdownChunker, persistenceService, vectorStore);
    }

    @Test
    void stillMarksMysqlFailedWhenRedisCompensationAlsoFails() {
        KnowledgeDocument source = processingDocument();
        List<KnowledgeChunkDraft> drafts = drafts();
        List<KnowledgeChunk> chunks = chunks();
        stubPipeline(source, List.of(), drafts, chunks);
        doThrow(new IllegalStateException("vector write failed"))
                .when(vectorStore).add(anyList());
        doThrow(new IllegalStateException("redis unavailable"))
                .when(vectorStore).delete(anyList());

        worker.process(10L);

        verify(finalizer).fail(
                eq(10L),
                argThat(message -> message.contains("vector write failed"))
        );
    }

    @Test
    void truncatesFailureMessageToDatabaseColumnLimit() {
        KnowledgeDocument source = processingDocument();
        when(documentService.getById(10L)).thenReturn(source);
        when(chunkMapper.selectByDocumentId(10L)).thenReturn(List.of());
        when(markdownChunker.chunk("Java 手册", "MARKDOWN", "# Java\n正文"))
                .thenThrow(new IllegalStateException("x".repeat(600)));

        worker.process(10L);

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(finalizer).fail(eq(10L), error.capture());
        assertThat(error.getValue()).hasSize(500);
    }

    private void stubPipeline(
            KnowledgeDocument source,
            List<KnowledgeChunk> previous,
            List<KnowledgeChunkDraft> drafts,
            List<KnowledgeChunk> chunks) {
        when(documentService.getById(10L)).thenReturn(source);
        when(chunkMapper.selectByDocumentId(10L)).thenReturn(previous);
        when(markdownChunker.chunk("Java 手册", "MARKDOWN", "# Java\n正文"))
                .thenReturn(drafts);
        when(persistenceService.replaceChunks(10L, drafts)).thenReturn(chunks);
    }

    private KnowledgeDocument processingDocument() {
        KnowledgeDocument source = new KnowledgeDocument();
        source.setId(10L);
        source.setTitle("Java 手册");
        source.setSourceType("MARKDOWN");
        source.setContent("# Java\n正文");
        source.setStatus(KnowledgeDocumentStatus.PROCESSING.getCode());
        return source;
    }

    private List<KnowledgeChunkDraft> drafts() {
        return List.of(
                new KnowledgeChunkDraft(0, "Java", "正文一", "hash-1", 10),
                new KnowledgeChunkDraft(1, "Java > JVM", "正文二", "hash-2", 10)
        );
    }

    private List<KnowledgeChunk> chunks() {
        return List.of(
                chunk(11L, 0, "正文一"),
                chunk(12L, 1, "正文二")
        );
    }

    private KnowledgeChunk chunk(Long id, int index, String content) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setDocumentId(10L);
        chunk.setChunkIndex(index);
        chunk.setHeadingPath(index == 0 ? "Java" : "Java > JVM");
        chunk.setContent(content);
        return chunk;
    }
}
