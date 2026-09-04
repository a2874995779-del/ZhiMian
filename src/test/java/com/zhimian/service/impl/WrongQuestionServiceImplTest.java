package com.zhimian.service.impl;

import com.zhimian.common.UserContext;
import com.zhimian.mapper.QuestionMapper;
import com.zhimian.mapper.QuestionTagMapper;
import com.zhimian.mapper.WrongQuestionMapper;
import com.zhimian.model.dto.WrongQuestionQueryDTO;
import com.zhimian.model.entity.QuestionTagName;
import com.zhimian.model.vo.WrongQuestionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WrongQuestionServiceImplTest {

    @Mock
    private WrongQuestionMapper wrongQuestionMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionTagMapper questionTagMapper;

    private WrongQuestionServiceImpl wrongQuestionService;

    @BeforeEach
    void setUp() {
        wrongQuestionService = new WrongQuestionServiceImpl(
                wrongQuestionMapper,
                questionMapper,
                questionTagMapper
        );
        UserContext.set(42L, "user", "jti", System.currentTimeMillis() + 3_600_000);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void pageLoadsTagsForEachQuestion() {
        WrongQuestionQueryDTO query = new WrongQuestionQueryDTO();
        query.setPageNum(1);
        query.setPageSize(10);

        WrongQuestionVO record = new WrongQuestionVO();
        record.setQuestionId(100L);
        when(wrongQuestionMapper.countByUser(42L, query)).thenReturn(1L);
        when(wrongQuestionMapper.selectPage(42L, query, 0)).thenReturn(List.of(record));

        QuestionTagName javaTag = tag(100L, "Java");
        QuestionTagName jvmTag = tag(100L, "JVM");
        when(questionTagMapper.selectTagNamesByQuestionIds(List.of(100L)))
                .thenReturn(List.of(javaTag, jvmTag));

        var result = wrongQuestionService.page(query);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).containsExactly(record);
        assertThat(record.getTags()).containsExactly("Java", "JVM");
    }

    @Test
    void pageSkipsPageQueryWhenThereAreNoWrongQuestions() {
        WrongQuestionQueryDTO query = new WrongQuestionQueryDTO();
        query.setPageNum(1);
        query.setPageSize(10);
        when(wrongQuestionMapper.countByUser(42L, query)).thenReturn(0L);

        var result = wrongQuestionService.page(query);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
        verify(wrongQuestionMapper, never()).selectPage(42L, query, 0);
        verify(questionTagMapper, never()).selectTagNamesByQuestionIds(List.of());
    }

    private QuestionTagName tag(Long questionId, String name) {
        QuestionTagName tag = new QuestionTagName();
        tag.setQuestionId(questionId);
        tag.setName(name);
        return tag;
    }
}
