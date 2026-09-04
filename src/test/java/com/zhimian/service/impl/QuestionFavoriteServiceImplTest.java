package com.zhimian.service.impl;

import com.zhimian.common.ErrorCode;
import com.zhimian.common.UserContext;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.QuestionFavoriteMapper;
import com.zhimian.mapper.QuestionMapper;
import com.zhimian.mapper.QuestionTagMapper;
import com.zhimian.model.dto.FavoriteQueryDTO;
import com.zhimian.model.entity.Question;
import com.zhimian.model.entity.QuestionTagName;
import com.zhimian.model.vo.FavoriteQuestionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionFavoriteServiceImplTest {

    @Mock
    private QuestionFavoriteMapper favoriteMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionTagMapper questionTagMapper;

    private QuestionFavoriteServiceImpl favoriteService;

    @BeforeEach
    void setUp() {
        favoriteService = new QuestionFavoriteServiceImpl(
                favoriteMapper,
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
    void addExistingQuestionUsesCurrentUser() {
        Question question = new Question();
        question.setId(100L);
        when(questionMapper.selectById(100L)).thenReturn(question);

        favoriteService.add(100L);

        verify(favoriteMapper).insertIgnore(42L, 100L);
    }

    @Test
    void addMissingQuestionDoesNotInsert() {
        when(questionMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> favoriteService.add(404L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
        verify(favoriteMapper, never()).insertIgnore(42L, 404L);
    }

    @Test
    void pageLoadsTagsForEachFavoriteQuestion() {
        FavoriteQueryDTO query = new FavoriteQueryDTO();
        query.setPageNum(1);
        query.setPageSize(10);

        FavoriteQuestionVO record = new FavoriteQuestionVO();
        record.setQuestionId(100L);
        when(favoriteMapper.countByUser(42L, query)).thenReturn(1L);
        when(favoriteMapper.selectPage(42L, query, 0)).thenReturn(List.of(record));

        QuestionTagName javaTag = tag(100L, "Java");
        QuestionTagName jvmTag = tag(100L, "JVM");
        when(questionTagMapper.selectTagNamesByQuestionIds(List.of(100L)))
                .thenReturn(List.of(javaTag, jvmTag));

        var result = favoriteService.page(query);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(record.getTags()).containsExactly("Java", "JVM");
    }

    @Test
    void pageSkipsPageQueryWhenThereAreNoFavorites() {
        FavoriteQueryDTO query = new FavoriteQueryDTO();
        query.setPageNum(1);
        query.setPageSize(10);
        when(favoriteMapper.countByUser(42L, query)).thenReturn(0L);

        var result = favoriteService.page(query);

        assertThat(result.getRecords()).isEmpty();
        verify(favoriteMapper, never()).selectPage(42L, query, 0);
        verify(questionTagMapper, never()).selectTagNamesByQuestionIds(List.of());
    }

    private QuestionTagName tag(Long questionId, String name) {
        QuestionTagName tag = new QuestionTagName();
        tag.setQuestionId(questionId);
        tag.setName(name);
        return tag;
    }
}
