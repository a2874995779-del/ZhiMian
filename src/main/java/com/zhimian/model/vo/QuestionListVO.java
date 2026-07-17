package com.zhimian.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuestionListVO {
    private Long id;
    private String title;
    private Integer difficulty;
    private Long categoryId;
    private String categoryName;
    private List<String> tags;
    private Integer viewCount;
    private LocalDateTime createTime;
}
