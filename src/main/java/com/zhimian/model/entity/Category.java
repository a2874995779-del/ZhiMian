package com.zhimian.model.entity;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Data
public class Category {
    private Long id;
    private String name;
    private Long parentId;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
