package com.zhimian.service.impl;

import com.zhimian.config.RagProperties;
import com.zhimian.model.dto.KnowledgeDocumentCreateDTO;
import com.zhimian.model.entity.KnowledgeDocument;
import com.zhimian.rag.ingestion.KnowledgeIngestionLauncher;
import com.zhimian.service.KnowledgeDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionServiceImplTest {

    @Mock
    private KnowledgeDocumentService documentService;

    @Mock
    private KnowledgeIngestionLauncher launcher;

    private RagProperties properties;
    private KnowledgeIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new RagProperties();
        properties.setEmbeddingModel("BAAI/bge-m3");
        properties.setEmbeddingDimensions(1024);
        service = new KnowledgeIngestionServiceImpl(
                documentService, launcher, properties);
    }

    @Test
    void createsStartsAndDispatchesNewDocument() {
        KnowledgeDocumentCreateDTO dto = createDto();
        when(documentService.createPending(dto)).thenReturn(10L);
        when(launcher.submit(10L)).thenReturn(true);

        Long documentId = service.submit(dto);

        assertThat(documentId).isEqualTo(10L);
        InOrder order = inOrder(documentService, launcher);
        order.verify(documentService).createPending(dto);
        order.verify(documentService)
                .startProcessing(10L, "BAAI/bge-m3", 1024);
        order.verify(launcher).submit(10L);
    }

    @Test
    void marksDocumentFailedWhenQueueRejectsNewTask() {
        KnowledgeDocumentCreateDTO dto = createDto();
        when(documentService.createPending(dto)).thenReturn(10L);
        when(launcher.submit(10L)).thenReturn(false);

        Long documentId = service.submit(dto);

        assertThat(documentId).isEqualTo(10L);
        verify(documentService).fail(
                10L, "RAG任务队列已满，请稍后再试");
    }

    @Test
    void retriesExistingDocumentWithoutCreatingDuplicate() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(10L);
        when(documentService.getById(10L)).thenReturn(document);
        when(launcher.submit(10L)).thenReturn(true);

        service.retry(10L);

        InOrder order = inOrder(documentService, launcher);
        order.verify(documentService).getById(10L);
        order.verify(documentService)
                .startProcessing(10L, "BAAI/bge-m3", 1024);
        order.verify(launcher).submit(10L);
        verify(documentService, never()).createPending(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotDispatchWhenStateTransitionFails() {
        KnowledgeDocumentCreateDTO dto = createDto();
        when(documentService.createPending(dto)).thenReturn(10L);
        org.mockito.Mockito.doThrow(new IllegalStateException("state conflict"))
                .when(documentService)
                .startProcessing(10L, "BAAI/bge-m3", 1024);

        assertThatThrownBy(() -> service.submit(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("state conflict");

        verifyNoInteractions(launcher);
    }

    @Test
    void rejectsBlankModelConfigurationBeforeCreatingTask() {
        properties.setEmbeddingModel("  ");

        assertThatThrownBy(() -> service.submit(createDto()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("配置不完整");

        verifyNoInteractions(documentService, launcher);
    }

    @Test
    void rejectsInvalidDimensionBeforeRetryLookup() {
        properties.setEmbeddingDimensions(0);

        assertThatThrownBy(() -> service.retry(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("配置不完整");

        verifyNoInteractions(documentService, launcher);
    }

    private KnowledgeDocumentCreateDTO createDto() {
        KnowledgeDocumentCreateDTO dto = new KnowledgeDocumentCreateDTO();
        dto.setTitle("Java 手册");
        dto.setOriginalFilename("java.md");
        dto.setSourceType("MARKDOWN");
        dto.setContent("# Java\n正文");
        dto.setCreatedBy(1L);
        return dto;
    }
}
