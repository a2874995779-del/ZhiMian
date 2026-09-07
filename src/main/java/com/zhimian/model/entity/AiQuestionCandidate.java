package com.zhimian.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiQuestionCandidate {
    private Long id;
    private String title;
    private String normalizedTitle;
    private String fingerprint;
    private String answer;
    private Integer difficulty;
    private Long categoryId;

    // 数据库列 tag_ids 是 JSON。Mapper 查询时把它转成字符串，Service 再用 ObjectMapper 解析。
    private String tagIdsJson;
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
