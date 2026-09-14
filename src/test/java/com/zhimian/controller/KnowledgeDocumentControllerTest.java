package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.common.UserContext;
import com.zhimian.model.dto.KnowledgeDocumentCreateDTO;
import com.zhimian.model.dto.KnowledgeDocumentImportDTO;
import com.zhimian.model.entity.KnowledgeDocument;
import com.zhimian.model.vo.KnowledgeDocumentStatusVO;
import com.zhimian.service.KnowledgeDocumentService;
import com.zhimian.service.KnowledgeIngestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentControllerTest {

    @Mock
    private KnowledgeIngestionService ingestionService;

    @Mock
    private KnowledgeDocumentService documentService;

    @InjectMocks
    private KnowledgeDocumentController controller;

    @BeforeEach
    void setUpUserContext() {
        UserContext.set(42L, "admin", "test-jti", Long.MAX_VALUE);
    }

    @AfterEach
    void clearUserContext() {
        UserContext.remove();
    }

    @Test
    void convertsRequestAndCapturesAdminIdBeforeSubmitting() {
        KnowledgeDocumentImportDTO request = new KnowledgeDocumentImportDTO();
        request.setTitle("Java 手册");
        request.setOriginalFilename("java.md");
        request.setSourceType("MARKDOWN");
        request.setContent("# Java\n正文");
        when(ingestionService.submit(
                org.mockito.ArgumentMatchers.any())).thenReturn(10L);

        Result<Long> result = controller.submit(request);

        assertThat(result.getData()).isEqualTo(10L);
        ArgumentCaptor<KnowledgeDocumentCreateDTO> command =
                ArgumentCaptor.forClass(KnowledgeDocumentCreateDTO.class);
        verify(ingestionService).submit(command.capture());
        assertThat(command.getValue())
                .extracting(
                        KnowledgeDocumentCreateDTO::getTitle,
                        KnowledgeDocumentCreateDTO::getOriginalFilename,
                        KnowledgeDocumentCreateDTO::getSourceType,
                        KnowledgeDocumentCreateDTO::getContent,
                        KnowledgeDocumentCreateDTO::getCreatedBy
                )
                .containsExactly(
                        "Java 手册", "java.md", "MARKDOWN",
                        "# Java\n正文", 42L
                );
    }

    @Test
    void delegatesRetryToIngestionService() {
        Result<Void> result = controller.retry(10L);

        assertThat(result.getData()).isNull();
        verify(ingestionService).retry(10L);
    }

    @Test
    void mapsDocumentToStatusView() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(10L);
        document.setTitle("Java 手册");
        document.setStatus(2);
        document.setChunkCount(3);
        document.setEmbeddingModel("BAAI/bge-m3");
        document.setVectorDimension(1024);
        when(documentService.getById(10L)).thenReturn(document);

        Result<KnowledgeDocumentStatusVO> result = controller.status(10L);

        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().id()).isEqualTo(10L);
        assertThat(result.getData().title()).isEqualTo("Java 手册");
        assertThat(result.getData().status()).isEqualTo(2);
        assertThat(result.getData().chunkCount()).isEqualTo(3);
        assertThat(result.getData().embeddingModel()).isEqualTo("BAAI/bge-m3");
        assertThat(result.getData().vectorDimension()).isEqualTo(1024);
    }
}
