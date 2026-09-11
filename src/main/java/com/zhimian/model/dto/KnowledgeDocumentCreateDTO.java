package com.zhimian.model.dto;

import lombok.Data;

@Data
public class KnowledgeDocumentCreateDTO {
    private String title;
    private String originalFilename;
    private String sourceType;
    private String content;
    private Long createdBy;
}
