package com.zhimian.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.mapper.CategoryMapper;
import com.zhimian.mapper.QuestionMapper;
import com.zhimian.mapper.QuestionTagMapper;
import com.zhimian.model.dto.QuestionAddDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceImplTest {
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionTagMapper questionTagMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private StringRedisTemplate redisTemplate;

    private QuestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuestionServiceImpl(
                questionMapper,
                questionTagMapper,
                categoryMapper,
                redisTemplate,
                new ObjectMapper()
        );
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void updateEvictsDetailAndHotCacheOnlyAfterCommit() {
        when(questionMapper.update(any())).thenReturn(1);

        service.updateQuestion(9L, questionDto());

        verifyNoInteractions(redisTemplate);
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(redisTemplate).delete(java.util.List.of(
                "zhimian:cache:question:detail:9",
                "zhimian:cache:question:hot"
        ));
    }

    private QuestionAddDTO questionDto() {
        QuestionAddDTO dto = new QuestionAddDTO();
        dto.setTitle("标题");
        dto.setAnswer("答案");
        dto.setDifficulty(1);
        dto.setCategoryId(1L);
        return dto;
    }
}
