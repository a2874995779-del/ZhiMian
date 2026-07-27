package com.zhimian.mapper;

import com.zhimian.model.entity.InterviewReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InterviewReportMapper {
    void insert(InterviewReport report);

    InterviewReport selectBySessionId(@Param("sessionId") Long sessionId);
}
