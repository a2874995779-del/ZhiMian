package com.zhimian.service.impl;

import com.zhimian.common.ErrorCode;
import com.zhimian.common.PageResult;
import com.zhimian.common.UserContext;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.QuestionFavoriteMapper;
import com.zhimian.mapper.QuestionMapper;
import com.zhimian.mapper.QuestionTagMapper;
import com.zhimian.model.dto.FavoriteQueryDTO;
import com.zhimian.model.entity.Question;
import com.zhimian.model.entity.QuestionTagName;
import com.zhimian.model.vo.FavoriteQuestionVO;
import com.zhimian.service.QuestionFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuestionFavoriteServiceImpl implements QuestionFavoriteService {
    private final QuestionFavoriteMapper favoriteMapper;
    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;

    @Override
    public PageResult<FavoriteQuestionVO> page(FavoriteQueryDTO query) {
        Long userId = UserContext.getUserId();
        int offset = (query.getPageNum() - 1) * query.getPageSize();
        long total = favoriteMapper.countByUser(userId,query);
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }

        List<FavoriteQuestionVO> records = favoriteMapper.selectPage(userId, query, offset);
        if (records.isEmpty()) {
            return PageResult.of(records, total, query.getPageNum(), query.getPageSize());
        }

        List<Long> questionIds = records.stream()
                .map(FavoriteQuestionVO::getQuestionId)
                .toList();
        Map<Long, List<String>> tagsByQuestionId = new HashMap<>();
        for (QuestionTagName tag : questionTagMapper.selectTagNamesByQuestionIds(questionIds)) {
            tagsByQuestionId.computeIfAbsent(tag.getQuestionId(), ignored -> new ArrayList<>())
                    .add(tag.getName());
        }
        for (FavoriteQuestionVO record : records) {
            record.setTags(tagsByQuestionId.getOrDefault(record.getQuestionId(), List.of()));
        }
        return PageResult.of(records, total, query.getPageNum(), query.getPageSize());
    }

    @Override
    public void add(Long questionId) {
        assertQuestionExists(questionId);
        favoriteMapper.insertIgnore(UserContext.getUserId(),questionId);
    }

    @Override
    public void remove(Long questionId) {
        favoriteMapper.deleteByUserAndQuestion(UserContext.getUserId(),questionId);
    }

    @Override
    public Boolean isFavorited(Long questionId) {
        return favoriteMapper.exists(UserContext.getUserId(),questionId);
    }

    private void assertQuestionExists(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if(question == null){
            throw new BusinessException(ErrorCode.NOT_FOUND,"题目不存在");
        }
    }
}
