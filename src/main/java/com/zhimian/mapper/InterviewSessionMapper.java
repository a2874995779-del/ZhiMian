package com.zhimian.mapper;

import com.zhimian.model.entity.InterviewSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InterviewSessionMapper {
    long countInProgress(@Param("userId") Long userId);

    void insert(InterviewSession session);
}
