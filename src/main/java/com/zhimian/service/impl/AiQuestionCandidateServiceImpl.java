package com.zhimian.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.ErrorCode;
import com.zhimian.common.PageResult;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.AiQuestionCandidateMapper;
import com.zhimian.mapper.QuestionMapper;
import com.zhimian.model.dto.AiQuestionApproveDTO;
import com.zhimian.model.dto.AiQuestionCandidateQueryDTO;
import com.zhimian.model.dto.QuestionAddDTO;
import com.zhimian.model.entity.AiQuestionCandidate;
import com.zhimian.model.vo.AiQuestionCandidateVO;
import com.zhimian.service.AiQuestionCandidateService;
import com.zhimian.service.QuestionService;
import com.zhimian.util.QuestionTextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiQuestionCandidateServiceImpl implements AiQuestionCandidateService {
    private final AiQuestionCandidateMapper candidateMapper;
    private final QuestionService questionService;
    private final ObjectMapper objectMapper;
    private final QuestionMapper questionMapper;
    private final QuestionTextNormalizer normalizer;

    @Override
    public PageResult<AiQuestionCandidateVO> page(AiQuestionCandidateQueryDTO query) {
        long total = candidateMapper.count(query);
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }

        List<AiQuestionCandidate> candidates = candidateMapper.selectPage(
                query,
                query.getOffset()
        );
        List<AiQuestionCandidateVO> records = new ArrayList<>();
        for (AiQuestionCandidate candidate : candidates) {
            records.add(toVO(candidate));
        }
        return PageResult.of(records, total, query.getPageNum(), query.getPageSize());
    }

    @Transactional
    @Override
    public Long approve(Long id, AiQuestionApproveDTO dto) {
        AiQuestionCandidate candidate = mustFind(id);
        if (Integer.valueOf(2).equals(candidate.getStatus())) {
            return candidate.getQuestionId();
        }
        if (Integer.valueOf(3).equals(candidate.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "已忽略的候选题不能直接收录");
        }

        String normalizedTitle = normalizer.normalize(dto.getTitle());
        boolean duplicate = questionMapper.selectAllTitles().stream()
                .map(normalizer::normalize)
                .anyMatch(normalizedTitle::equals);
        if (duplicate) {
            throw new BusinessException(ErrorCode.CONFLICT, "正式题库中已经存在相同题目");
        }

        // 条件更新相当于抢审核锁，避免连续点击或两个管理员同时通过。
        if (candidateMapper.markPublishing(id) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "候选题状态已变化，请刷新后重试");
        }

        QuestionAddDTO questionDTO = new QuestionAddDTO();
        questionDTO.setTitle(dto.getTitle().strip());
        questionDTO.setContent(dto.getContent());
        questionDTO.setAnswer(dto.getAnswer().strip());
        questionDTO.setDifficulty(dto.getDifficulty());
        questionDTO.setCategoryId(dto.getCategoryId());
        questionDTO.setTagIds(dto.getTagIds());

        Long questionId = questionService.addQuestion(questionDTO);
        if (candidateMapper.markAccepted(id, questionId) == 0) {
            throw new IllegalStateException("正式题目已创建，但候选题状态更新失败");
        }
        return questionId;
    }

    @Override
    public void ignore(Long id) {
        mustFind(id);
        if (candidateMapper.markIgnored(id) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不能忽略，请刷新后重试");
        }
    }

    private AiQuestionCandidate mustFind(Long id) {
        AiQuestionCandidate candidate = candidateMapper.selectById(id);
        if (candidate == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "候选题不存在");
        }
        return candidate;
    }

    private AiQuestionCandidateVO toVO(AiQuestionCandidate candidate) {
        AiQuestionCandidateVO vo = new AiQuestionCandidateVO();
        BeanUtils.copyProperties(candidate, vo);
        vo.setTagIds(parseTagIds(candidate.getTagIdsJson()));
        return vo;
    }

    private List<Long> parseTagIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("候选题标签JSON无法解析", e);
        }
    }
}
