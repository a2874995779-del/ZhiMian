package com.zhimian.service.impl;

import com.zhimian.common.ErrorCode;
import com.zhimian.common.PageResult;
import com.zhimian.common.UserContext;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.CategoryMapper;
import com.zhimian.mapper.QuestionMapper;
import com.zhimian.mapper.QuestionTagMapper;
import com.zhimian.model.dto.QuestionAddDTO;
import com.zhimian.model.dto.QuestionQueryDTO;
import com.zhimian.model.entity.Category;
import com.zhimian.model.entity.Question;
import com.zhimian.model.entity.QuestionTagName;
import com.zhimian.model.vo.QuestionListVO;
import com.zhimian.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {
    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;
    private final CategoryMapper categoryMapper;

    @Transactional
    @Override
    public Long addQuestion(QuestionAddDTO dto) {
        Question question = new Question();
        question.setTitle(dto.getTitle());
        question.setContent(dto.getContent());
        question.setAnswer(dto.getAnswer());
        question.setDifficulty(dto.getDifficulty());
        question.setCategoryId(dto.getCategoryId());
        question.setCreateUserId(UserContext.getUserId());
        questionMapper.insert(question);
        // 空列表必须在这里挡住:<foreach> 对空列表会生成非法 SQL
        if(!CollectionUtils.isEmpty(dto.getTagIds())){
            questionTagMapper.batchInsert(question.getId(),dto.getTagIds());
        }
        return question.getId();
    }
    @Transactional
    @Override
    public void updateQuestion(Long id, QuestionAddDTO dto) {
        Question question = new Question();
        question.setId(id);
        question.setTitle(dto.getTitle());
        question.setContent(dto.getContent());
        question.setAnswer(dto.getAnswer());
        question.setDifficulty(dto.getDifficulty());
        question.setCategoryId(dto.getCategoryId());
        // update 的 WHERE 带 is_deleted = 0,影响行数为 0 说明题目不存在或已删除
        if(questionMapper.update(question) == 0){
            throw new BusinessException(ErrorCode.NOT_FOUND,"题目不存在");
        }
        questionTagMapper.deleteByQuestionId(id);
        if(!CollectionUtils.isEmpty(dto.getTagIds())){
            questionTagMapper.batchInsert(id,dto.getTagIds());
        }
    }

    @Transactional
    @Override
    public void deleteQuestion(Long id) {
        if(questionMapper.logicalDelete(id) == 0){
            throw new BusinessException(ErrorCode.NOT_FOUND,"题目不存在");
        }
        // 关联记录留不留都不影响正确性(所有查询以 question.is_deleted 为准),
        // 这里选择顺手清掉,让关联表只存有效关系
        questionTagMapper.deleteByQuestionId(id);
    }

    @Override
    public PageResult<QuestionListVO> pageQuestions(QuestionQueryDTO dto) {
        long total = questionMapper.count(dto);
        if (total == 0) {
            return PageResult.of(List.of(), 0, dto.getPageNum(), dto.getPageSize());
        }
        List<Question> records = questionMapper.selectPage(dto);

        Map<Long, String> categoryNameMap = new HashMap<>();
        for (Category c : categoryMapper.selectAll()) {
            categoryNameMap.put(c.getId(), c.getName());
        }

        // 补齐 tags:当页题目 id 一次 IN 查询,再按 questionId 分组
        List<Long> questionIds = new ArrayList<>();
        for (Question q : records) {
            questionIds.add(q.getId());
        }
        Map<Long, List<String>> tagMap = new HashMap<>();
        for (QuestionTagName row : questionTagMapper.selectTagNamesByQuestionIds(questionIds)) {
            tagMap.computeIfAbsent(row.getQuestionId(), k -> new ArrayList<>()).add(row.getName());
        }

        List<QuestionListVO> voList = new ArrayList<>();
        for (Question q : records) {
            QuestionListVO vo = new QuestionListVO();
            BeanUtils.copyProperties(q, vo);
            vo.setCategoryName(categoryNameMap.get(q.getCategoryId()));
            vo.setTags(tagMap.getOrDefault(q.getId(), List.of()));
            voList.add(vo);
        }
        return PageResult.of(voList, total, dto.getPageNum(), dto.getPageSize());
    }
}
