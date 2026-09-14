package com.zhimian.model.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeDocumentImportDTOTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void acceptsCompleteDocumentWithinSizeLimits() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void rejectsBlankRequiredFields() {
        KnowledgeDocumentImportDTO request = validRequest();
        request.setTitle(" ");
        request.setOriginalFilename("");
        request.setSourceType(null);
        request.setContent("\n");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder(
                        "title", "originalFilename", "sourceType", "content");
    }

    @Test
    void rejectsOversizedTitleFilenameAndContent() {
        KnowledgeDocumentImportDTO request = validRequest();
        request.setTitle("t".repeat(201));
        request.setOriginalFilename("f".repeat(256));
        request.setContent("c".repeat(2_000_001));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder(
                        "title", "originalFilename", "content");
    }

    private KnowledgeDocumentImportDTO validRequest() {
        KnowledgeDocumentImportDTO request = new KnowledgeDocumentImportDTO();
        request.setTitle("Java 手册");
        request.setOriginalFilename("java.md");
        request.setSourceType("MARKDOWN");
        request.setContent("# Java\n正文");
        return request;
    }
}
