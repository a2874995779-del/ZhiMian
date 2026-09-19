package com.zhimian.mapper;

import com.zhimian.model.dto.WrongQuestionQueryDTO;
import com.zhimian.model.vo.WrongQuestionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WrongQuestionMapper {

    long countByUser(@Param("userId") Long userId,
                     @Param("query") WrongQuestionQueryDTO query);

    List<WrongQuestionVO> selectPage(@Param("userId") Long userId,
                                     @Param("query") WrongQuestionQueryDTO query,
                                     @Param("offset") int offset);

    void markUnmastered(@Param("userId") Long userId,
                        @Param("questionId") Long questionId);

    void markMastered(@Param("userId")Long userId,
                      @Param("questionId")Long questionId);

    void deleteByUserAndQuestion(@Param("userId")Long userId,
                                 @Param("questionId")Long questionId);

    void recordWrong(@Param("userId") Long userId,
                     @Param("questionId") long questionId);

    void recordCorrect(@Param("userId")Long userId,
                       @Param("questionId")long questionId);

    boolean existsByUserAndQuestion(
            @Param("userId") Long userId,
            @Param("questionId")Long questionId);
}
