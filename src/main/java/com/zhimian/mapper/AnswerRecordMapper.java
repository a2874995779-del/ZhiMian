package com.zhimian.mapper;

import com.zhimian.model.entity.AnswerRecord;
import com.zhimian.model.dto.AnswerDailyStatDTO;
import com.zhimian.model.dto.RankStatDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AnswerRecordMapper {
    void insert(AnswerRecord record);

    int countCorrectByUser(@Param("userId") Long userId);

    int countCorrectByUserBetween(@Param("userId") Long userId,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    List<AnswerDailyStatDTO> selectDailyStats(@Param("userId") Long userId,
                                              @Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    List<LocalDate> selectCorrectDates(@Param("userId") Long userId);

    List<RankStatDTO> selectCorrectRank(@Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end,
                                        @Param("limit") int limit);
}
