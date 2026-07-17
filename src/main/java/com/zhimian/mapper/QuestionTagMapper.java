package com.zhimian.mapper;

import com.zhimian.model.entity.QuestionTagName;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuestionTagMapper {
    void batchInsert(@Param("questionId") Long id,@Param("tagIds") List<Long> tagIds);

    void deleteByQuestionId(Long id);

    List<QuestionTagName> selectTagNamesByQuestionIds(@Param("questionIds") List<Long> questionIds);
}
