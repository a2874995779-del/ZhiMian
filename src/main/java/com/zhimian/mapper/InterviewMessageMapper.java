package com.zhimian.mapper;

import com.zhimian.model.entity.InterviewMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InterviewMessageMapper {
    void insert(InterviewMessage systemMsg);

    List<InterviewMessage> selectBySessionId(@Param("sessionId") Long sessionId);

    InterviewMessage selectLastAssistantBySessionId(@Param("sessionId") Long sessionId);
}
