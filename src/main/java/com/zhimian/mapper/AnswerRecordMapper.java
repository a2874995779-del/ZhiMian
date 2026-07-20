package com.zhimian.mapper;

import com.zhimian.model.entity.AnswerRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnswerRecordMapper {
    void insert(AnswerRecord record);
}
