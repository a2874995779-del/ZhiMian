package com.zhimian.mapper;

import com.zhimian.model.dto.AiQuestionCandidateQueryDTO;
import com.zhimian.model.entity.AiQuestionCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiQuestionCandidateMapper {
    boolean existsBySourceMessage(@Param("sourceMessageId") Long sourceMessageId);

    int insertIgnore(AiQuestionCandidate candidate);

    int incrementDuplicate(@Param("fingerprint") String fingerprint);

    int markEnriched(@Param("id") Long id,
                     @Param("answer") String answer,
                     @Param("difficulty") Integer difficulty,
                     @Param("categoryId") Long categoryId,
                     @Param("tagIdsJson") String tagIdsJson);

    int markFailed(@Param("id") Long id,
                   @Param("errorMessage") String errorMessage);

    AiQuestionCandidate selectById(@Param("id") Long id);

    long count(@Param("query") AiQuestionCandidateQueryDTO query);

    List<AiQuestionCandidate> selectPage(@Param("query") AiQuestionCandidateQueryDTO query,
                                         @Param("offset") int offset);

    int markPublishing(@Param("id") Long id);

    int markAccepted(@Param("id") Long id,
                     @Param("questionId") Long questionId);

    int markIgnored(@Param("id") Long id);
}
