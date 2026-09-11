package com.zhimian.service.impl;

import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.KnowledgeDocumentMapper;
import com.zhimian.model.dto.KnowledgeDocumentCreateDTO;
import com.zhimian.model.entity.KnowledgeDocument;
import com.zhimian.model.enums.KnowledgeDocumentStatus;
import com.zhimian.util.KnowledgeContentHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentServiceImplTest {

    @Mock
    private KnowledgeDocumentMapper documentMapper;

    private KnowledgeContentHasher contentHasher;
    private KnowledgeDocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        contentHasher = new KnowledgeContentHasher();
        service = new KnowledgeDocumentServiceImpl(documentMapper, contentHasher);
    }

    @Test
    void createPendingPersistsNormalizedCompleteDocument() {
        KnowledgeDocumentCreateDTO dto = createDTO();
        String expectedHash = contentHasher.sha256(dto.getContent());
        when(documentMapper.selectActiveByContentHash(expectedHash)).thenReturn(null);
        when(documentMapper.insert(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(10L);
            return 1;
        });

        Long id = service.createPending(dto);

        assertThat(id).isEqualTo(10L);
        ArgumentCaptor<KnowledgeDocument> captor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(documentMapper).insert(captor.capture());
        KnowledgeDocument saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("Java线程池");
        assertThat(saved.getOriginalFilename()).isEqualTo("thread-pool.md");
        assertThat(saved.getSourceType()).isEqualTo("MARKDOWN");
        assertThat(saved.getContent()).isEqualTo("# 线程池\n\n四种拒绝策略");
        assertThat(saved.getContentHash()).isEqualTo(expectedHash);
        assertThat(saved.getStatus()).isEqualTo(KnowledgeDocumentStatus.PENDING.getCode());
        assertThat(saved.getChunkCount()).isZero();
        assertThat(saved.getCreatedBy()).isEqualTo(1L);
    }

    @Test
    void incompleteCreateRequestIsRejectedBeforeCallingMapper() {
        KnowledgeDocumentCreateDTO dto = createDTO();
        dto.setContent("  ");

        assertThatThrownBy(() -> service.createPending(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识文档参数不完整");
        verifyNoInteractions(documentMapper);
    }

    @Test
    void unsupportedSourceTypeIsRejected() {
        KnowledgeDocumentCreateDTO dto = createDTO();
        dto.setSourceType("PDF");

        assertThatThrownBy(() -> service.createPending(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前只支持MARKDOWN和TEXT文档");
        verifyNoInteractions(documentMapper);
    }

    @Test
    void existingActiveContentIsRejected() {
        KnowledgeDocumentCreateDTO dto = createDTO();
        String hash = contentHasher.sha256(dto.getContent());
        when(documentMapper.selectActiveByContentHash(hash)).thenReturn(new KnowledgeDocument());

        assertThatThrownBy(() -> service.createPending(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("相同内容的知识文档已经存在了");
        verify(documentMapper, never()).insert(any());
    }

    @Test
    void concurrentDuplicateInsertIsConvertedToBusinessConflict() {
        KnowledgeDocumentCreateDTO dto = createDTO();
        String hash = contentHasher.sha256(dto.getContent());
        when(documentMapper.selectActiveByContentHash(hash)).thenReturn(null);
        when(documentMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.createPending(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("相同内容的知识文档已经存在了");
    }

    @Test
    void missingDocumentIsReportedAsNotFound() {
        when(documentMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识文档不存在");
    }

    @Test
    void startProcessingChecksExistenceAndUsesConditionalUpdate() {
        when(documentMapper.selectById(10L)).thenReturn(document(10L));
        when(documentMapper.markProcessing(10L, "BAAI/bge-m3", 1024)).thenReturn(1);

        service.startProcessing(10L, " BAAI/bge-m3 ", 1024);

        verify(documentMapper).markProcessing(10L, "BAAI/bge-m3", 1024);
    }

    @Test
    void failedConditionalUpdateIsReportedAsStateConflict() {
        when(documentMapper.selectById(10L)).thenReturn(document(10L));
        when(documentMapper.markProcessing(10L, "BAAI/bge-m3", 1024)).thenReturn(0);

        assertThatThrownBy(() -> service.startProcessing(10L, "BAAI/bge-m3", 1024))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识文档状态已变化，请刷新后重试");
    }

    @Test
    void completedDocumentMustContainAtLeastOneChunk() {
        assertThatThrownBy(() -> service.complete(10L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("完成处理时chunk数量必须大于0");
        verify(documentMapper, never()).markCompleted(any(), any());
    }

    @Test
    void completeMovesProcessingDocumentToCompleted() {
        when(documentMapper.markCompleted(10L, 3)).thenReturn(1);

        service.complete(10L, 3);

        verify(documentMapper).markCompleted(10L, 3);
    }

    @Test
    void failTruncatesLongErrorMessageToDatabaseLimit() {
        String longMessage = "x".repeat(600);
        when(documentMapper.markFailed(any(), any())).thenReturn(1);

        service.fail(10L, longMessage);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(documentMapper).markFailed(org.mockito.ArgumentMatchers.eq(10L), captor.capture());
        assertThat(captor.getValue()).hasSize(500);
    }

    @Test
    void blankFailureReasonUsesSafeDefaultMessage() {
        when(documentMapper.markFailed(10L, "未知处理异常")).thenReturn(1);

        service.fail(10L, "  ");

        verify(documentMapper).markFailed(10L, "未知处理异常");
    }

    @Test
    void beginDeleteChecksExistenceBeforeChangingState() {
        when(documentMapper.selectById(10L)).thenReturn(document(10L));
        when(documentMapper.markDeleting(10L)).thenReturn(1);

        service.beginDelete(10L);

        verify(documentMapper).markDeleting(10L);
    }

    private KnowledgeDocumentCreateDTO createDTO() {
        KnowledgeDocumentCreateDTO dto = new KnowledgeDocumentCreateDTO();
        dto.setTitle(" Java线程池 ");
        dto.setOriginalFilename(" thread-pool.md ");
        dto.setSourceType("markdown");
        dto.setContent("  # 线程池\r\n\r\n四种拒绝策略  ");
        dto.setCreatedBy(1L);
        return dto;
    }

    private KnowledgeDocument document(Long id) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(id);
        return document;
    }
}
