package com.zhimian.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.ErrorCode;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.AiQuestionCandidateMapper;
import com.zhimian.mapper.QuestionMapper;
import com.zhimian.model.dto.AiQuestionApproveDTO;
import com.zhimian.model.dto.QuestionAddDTO;
import com.zhimian.model.entity.AiQuestionCandidate;
import com.zhimian.service.QuestionService;
import com.zhimian.util.QuestionTextNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiQuestionCandidateServiceImplTest {
    @Mock
    private AiQuestionCandidateMapper candidateMapper;
    @Mock
    private QuestionService questionService;
    @Mock
    private QuestionMapper questionMapper;

    private AiQuestionCandidateServiceImpl candidateService;

    @BeforeEach
    void setUp() {
        candidateService = new AiQuestionCandidateServiceImpl(
                candidateMapper,
                questionService,
                new ObjectMapper(),
                questionMapper,
                new QuestionTextNormalizer()
        );
    }

    @Test
    void approveCreatesOfficialQuestionAndMarksCandidateAccepted() {
        AiQuestionCandidate candidate = candidate(10L, 1, null);
        when(candidateMapper.selectById(10L)).thenReturn(candidate);
        when(candidateMapper.markPublishing(10L)).thenReturn(1);
        when(questionService.addQuestion(org.mockito.ArgumentMatchers.any())).thenReturn(88L);
        when(candidateMapper.markAccepted(10L, 88L)).thenReturn(1);

        AiQuestionApproveDTO dto = approveDto();
        Long questionId = candidateService.approve(10L, dto);

        assertThat(questionId).isEqualTo(88L);
        ArgumentCaptor<QuestionAddDTO> captor = ArgumentCaptor.forClass(QuestionAddDTO.class);
        verify(questionService).addQuestion(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("什么是 JVM？");
        assertThat(captor.getValue().getTagIds()).containsExactly(2L, 3L);
        verify(candidateMapper).markAccepted(10L, 88L);
    }

    @Test
    void alreadyAcceptedCandidateReturnsExistingQuestionId() {
        when(candidateMapper.selectById(10L)).thenReturn(candidate(10L, 2, 88L));

        Long questionId = candidateService.approve(10L, approveDto());

        assertThat(questionId).isEqualTo(88L);
        verify(candidateMapper, never()).markPublishing(10L);
        verify(questionService, never()).addQuestion(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void concurrentApprovalIsRejectedBeforeQuestionCreation() {
        when(candidateMapper.selectById(10L)).thenReturn(candidate(10L, 1, null));
        when(candidateMapper.markPublishing(10L)).thenReturn(0);

        assertThatThrownBy(() -> candidateService.approve(10L, approveDto()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.CONFLICT.getCode());
        verify(questionService, never()).addQuestion(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void normalizedDuplicateOfficialQuestionIsRejected() {
        when(candidateMapper.selectById(10L)).thenReturn(candidate(10L, 1, null));
        when(questionMapper.selectAllTitles()).thenReturn(List.of("什么是 JVM"));

        assertThatThrownBy(() -> candidateService.approve(10L, approveDto()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.CONFLICT.getCode());

        verify(candidateMapper, never()).markPublishing(10L);
        verify(questionService, never()).addQuestion(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingCandidateReturnsNotFound() {
        when(candidateMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> candidateService.ignore(404L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }

    private AiQuestionCandidate candidate(Long id, int status, Long questionId) {
        AiQuestionCandidate candidate = new AiQuestionCandidate();
        candidate.setId(id);
        candidate.setStatus(status);
        candidate.setQuestionId(questionId);
        return candidate;
    }

    private AiQuestionApproveDTO approveDto() {
        AiQuestionApproveDTO dto = new AiQuestionApproveDTO();
        dto.setTitle(" 什么是 JVM？ ");
        dto.setAnswer("JVM 是 Java 虚拟机。");
        dto.setDifficulty(1);
        dto.setCategoryId(1L);
        dto.setTagIds(List.of(2L, 3L));
        return dto;
    }
}
