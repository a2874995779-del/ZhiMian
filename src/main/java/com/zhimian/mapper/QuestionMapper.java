package com.zhimian.mapper;

import com.zhimian.model.dto.QuestionQueryDTO;
import com.zhimian.model.entity.Question;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuestionMapper {
    void insert(Question question);

    int update(Question question);

    int logicalDelete(Long id);
    
    Question selectById(Long id);

    long count(QuestionQueryDTO dto);

    List<Question> selectPage(QuestionQueryDTO query);

    void incrementView(@Param("id") Long questionId,@Param("delta") int delta);

    List<Question> selectHot(@Param("limit") int hotLimit);
}
