package com.zhimian.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KnowledgeDocumentImportDTO {
    @NotBlank(message = "文档标题不能为空")
    @Size(max = 200, message = "文档标题不能超过200个字符")
    private String title;

    @NotBlank(message = "原始文件名不能为空")
    @Size(max = 255, message = "原始文件名不能超过255个字符")
    private String originalFilename;

    @NotBlank(message = "来源类型不能为空")
    private String sourceType;

    @NotBlank(message = "文档内容不能为空")
    @Size(max = 2_000_000, message = "文档内容过大")
    private String content;
}
