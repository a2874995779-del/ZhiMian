package com.zhimian.mapper;

import com.zhimian.model.dto.FavoriteQueryDTO;
import com.zhimian.model.vo.FavoriteQuestionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuestionFavoriteMapper {
    long countByUser(@Param("userId") Long userId,
                     @Param("query") FavoriteQueryDTO query);

    List<FavoriteQuestionVO> selectPage(@Param("userId")Long userId,
                                        @Param("query")FavoriteQueryDTO query,
                                        @Param("offset") int offset);

    void insertIgnore(@Param("userId") Long userId,
                      @Param("questionId") Long questionId);

    void deleteByUserAndQuestion(@Param("userId")Long userId,
                                 @Param("questionId")Long questionId);

    Boolean exists(@Param("userId")Long userId,
                   @Param("questionId")Long questionId);
}
