package com.zhimian.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.mapper.AiQuestionCandidateMapper;
import com.zhimian.mapper.CategoryMapper;
import com.zhimian.mapper.QuestionMapper;
import com.zhimian.mapper.TagMapper;
import com.zhimian.model.entity.AiQuestionCandidate;
import com.zhimian.util.QuestionTextNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiQuestionCollectorServiceImplTest {
    @Mock
    private AiQuestionCandidateMapper candidateMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private ChatClient chatClient;

    private AiQuestionCollectorServiceImpl collectorService;

    @BeforeEach
    void setUp() {
        collectorService = new AiQuestionCollectorServiceImpl(
                candidateMapper,
                questionMapper,
                categoryMapper,
                tagMapper,
                new QuestionTextNormalizer(),
                chatClient,
                new ObjectMapper()
        );
    }

    @Test
    void repeatedCandidateOnlyIncrementsCounter() {
        when(candidateMapper.existsBySourceMessage(10L)).thenReturn(false);
        when(questionMapper.selectAllTitles()).thenReturn(java.util.List.of());
        when(candidateMapper.insertIgnore(any(AiQuestionCandidate.class))).thenReturn(0);

        collectorService.collect(1L, 10L, "jvm", "什么是 JVM？");

        verify(candidateMapper).incrementDuplicate(anyString());
        verifyNoInteractions(categoryMapper, tagMapper, chatClient);
    }

    @Test
    void existingOfficialQuestionIsSkipped() {
        when(candidateMapper.existsBySourceMessage(10L)).thenReturn(false);
        when(questionMapper.selectAllTitles()).thenReturn(java.util.List.of("什么是 JVM"));

        collectorService.collect(1L, 10L, "jvm", "什么是 JVM？");

        verify(candidateMapper, never()).insertIgnore(any());
        verifyNoInteractions(categoryMapper, tagMapper, chatClient);
    }

    @Test
    void processedSourceMessageIsIdempotent() {
        when(candidateMapper.existsBySourceMessage(10L)).thenReturn(true);

        collectorService.collect(1L, 10L, "jvm", "什么是 JVM？");

        verify(questionMapper, never()).selectAllTitles();
        verify(candidateMapper, never()).insertIgnore(any());
        verifyNoInteractions(categoryMapper, tagMapper, chatClient);
    }
}
