package com.zhimian.mapper;

import com.zhimian.model.entity.InterviewTurn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InterviewTurnMapper {
    void insert(InterviewTurn turn);

    InterviewTurn selectWaitingBySessionId(@Param("sessionId") Long sessionId);

    int markEvaluated(@Param("id") Long id,
                      @Param("answerText") String answerText,
                      @Param("score") Integer score,
                      @Param("result") Integer result,
                      @Param("evaluation") String evaluation);

    int markEvaluationFailed(@Param("id") Long id,
                             @Param("answerText") String answerText,
                             @Param("evaluation") String evaluation);

    int skipWaiting(@Param("sessionId") Long sessionId);

    List<InterviewTurn> selectAnsweredBySessionId(@Param("sessionId") Long sessionId);

    Double averageScoreBySessionId(@Param("sessionId") Long sessionId);
}
