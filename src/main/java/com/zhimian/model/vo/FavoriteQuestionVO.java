package com.zhimian.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FavoriteQuestionVO {
    private Long questionId;
    private String title;
    private Integer difficulty;
    private Long categoryId;
    private String categoryName;
    private Integer viewCount;
    private List<String> tags;
    private LocalDateTime favoriteTime;
}
