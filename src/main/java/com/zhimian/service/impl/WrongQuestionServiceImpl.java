package com.zhimian.service.impl;

import com.zhimian.common.ErrorCode;
import com.zhimian.common.PageResult;
import com.zhimian.common.UserContext;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.QuestionMapper;
import com.zhimian.mapper.QuestionTagMapper;
import com.zhimian.mapper.WrongQuestionMapper;
import com.zhimian.model.dto.WrongQuestionQueryDTO;
import com.zhimian.model.entity.Question;
import com.zhimian.model.entity.QuestionTagName;
import com.zhimian.model.vo.WrongQuestionVO;
import com.zhimian.service.WrongQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WrongQuestionServiceImpl implements WrongQuestionService {
    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;

    @Override
    public PageResult<WrongQuestionVO> page(WrongQuestionQueryDTO query) {
        Long userId = UserContext.getUserId();
        int offset = (query.getPageNum() - 1) * query.getPageSize();
        long total = wrongQuestionMapper.countByUser(userId, query);
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }

        List<WrongQuestionVO> records = wrongQuestionMapper.selectPage(userId, query, offset);
        if (records.isEmpty()) {
            return PageResult.of(records, total, query.getPageNum(), query.getPageSize());
        }

        List<Long> questionIds = records.stream()
                .map(WrongQuestionVO::getQuestionId)
                .toList();
        Map<Long, List<String>> tagsByQuestionId = new HashMap<>();
        for (QuestionTagName tag : questionTagMapper.selectTagNamesByQuestionIds(questionIds)) {
            tagsByQuestionId.computeIfAbsent(tag.getQuestionId(), ignored -> new ArrayList<>())
                    .add(tag.getName());
        }
        for (WrongQuestionVO record : records) {
            record.setTags(tagsByQuestionId.getOrDefault(record.getQuestionId(), List.of()));
        }
        return PageResult.of(records, total, query.getPageNum(), query.getPageSize());
    }

    @Override
    public void markUnmastered(Long questionId) {
        assertQuestionExists(questionId);
        wrongQuestionMapper.markUnmastered(UserContext.getUserId(),questionId);
    }

    @Override
    public void markMastered(Long questionId) {
        assertQuestionExists(questionId);
        wrongQuestionMapper.markMastered(UserContext.getUserId(),questionId);
    }

    @Override
    public void remove(Long questionId) {
        wrongQuestionMapper.deleteByUserAndQuestion(UserContext.getUserId(),questionId);
    }

    @Override
    public void recordAnswer(long questionId, Integer result) {
        Long userId = UserContext.getUserId();
        if(result != null && result == 0){
            wrongQuestionMapper.recordWrong(userId,questionId);
        }else if(result !=null && result == 1){
            wrongQuestionMapper.recordCorrect(userId,questionId);
        }
    }

    private void assertQuestionExists(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if(question == null){
            throw new BusinessException(ErrorCode.NOT_FOUND,"题目不存在");
        }
    }

}
