package com.zhimian.mapper;

import com.zhimian.model.entity.InterviewMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewMessageMapper {
    void insert(InterviewMessage systemMsg);
}
