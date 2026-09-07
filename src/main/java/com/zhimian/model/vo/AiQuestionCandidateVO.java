package com.zhimian.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiQuestionCandidateVO {
    private Long id;
    private String title;
    private String normalizedTitle;
    private String answer;
    private Integer difficulty;
    private Long categoryId;
    private List<Long> tagIds;
    private String direction;
    private Long sourceSessionId;
    private Long sourceMessageId;
    private Integer duplicateCount;
    private Integer status;
    private String errorMessage;
    private Long questionId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
