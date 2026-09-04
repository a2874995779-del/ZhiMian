package com.zhimian.mapper;

import com.zhimian.model.entity.InterviewReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InterviewReportMapper {
    void insert(InterviewReport report);

    InterviewReport selectBySessionId(@Param("sessionId") Long sessionId);

    void insertGenerating(@Param("sessionId") Long sessionId);

    void markSuccess(@Param("sessionId") Long sessionId,
                     @Param("score") Integer score,
                     @Param("content") String s);

    void markFailed(@Param("sessionId") Long sessionId,
                    @Param("errorMessage") String errorMessage);
}
