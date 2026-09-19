package com.zhimian.service.impl;

import com.zhimian.common.ErrorCode;
import com.zhimian.common.UserContext;
import com.zhimian.config.RagProperties;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.QuestionMapper;
import com.zhimian.mapper.WrongQuestionMapper;
import com.zhimian.model.entity.Question;
import com.zhimian.model.enums.RagDegradedReason;
import com.zhimian.model.enums.RagExplanationMode;
import com.zhimian.model.vo.KnowledgeCitationVO;
import com.zhimian.model.vo.WrongQuestionExplanationVO;
import com.zhimian.rag.generation.AssembledKnowledgeContext;
import com.zhimian.rag.generation.KnowledgeContextAssembler;
import com.zhimian.rag.generation.RagExplanationGenerator;
import com.zhimian.rag.generation.RagExplanationOutput;
import com.zhimian.rag.retrieval.KnowledgeRetrievalService;
import com.zhimian.rag.retrieval.KnowledgeSearchHit;
import com.zhimian.rag.retrieval.KnowledgeSearchResult;
import com.zhimian.service.WrongQuestionExplanationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WrongQuestionExplanationServiceImpl
        implements WrongQuestionExplanationService {

    private static final int MAX_RETRIEVAL_QUERY_LENGTH = 500;
    private static final int MAX_EXCERPT_LENGTH = 180;

    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionMapper questionMapper;
    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgeContextAssembler contextAssembler;
    private final RagExplanationGenerator explanationGenerator;
    private final RagProperties ragProperties;

    @Override
    public WrongQuestionExplanationVO explain(Long questionId) {
        validateQuestionId(questionId);
        Long userId = requireUserId();
        assertOwnWrongQuestion(userId, questionId);

        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "错题不存在");
        }

        List<RagDegradedReason> degradedReasons = new ArrayList<>();
        List<KnowledgeSearchHit> hits = retrieveKnowledge(
                question,
                degradedReasons
        );
        AssembledKnowledgeContext context = contextAssembler.assemble(hits);

        try {
            RagExplanationOutput output = explanationGenerator.generate(
                    question,
                    context.promptContext()
            );
            RagExplanationMode mode = context.hasKnowledge()
                    ? RagExplanationMode.RAG
                    : RagExplanationMode.MODEL_ONLY;

            return toResponse(
                    question,
                    output,
                    mode,
                    context.hasKnowledge(),
                    true,
                    degradedReasons,
                    context.includedHits()
            );
        }
        catch (RuntimeException exception) {
            log.error(
                    "错题讲解 AI 生成失败: userId={}, questionId={}",
                    userId,
                    questionId,
                    exception
            );
            addDegradedReason(
                    degradedReasons,
                    RagDegradedReason.AI_UNAVAILABLE
            );
            return fallbackResponse(question, degradedReasons);
        }
    }

    private List<KnowledgeSearchHit> retrieveKnowledge(
            Question question,
            List<RagDegradedReason> degradedReasons) {
        if (!ragProperties.isEnabled()) {
            addDegradedReason(
                    degradedReasons,
                    RagDegradedReason.RAG_DISABLED
            );
            return List.of();
        }

        try {
            KnowledgeSearchResult result = retrievalService.search(
                    buildRetrievalQuery(question),
                    null,
                    null
            );
            List<KnowledgeSearchHit> hits = result == null
                    ? List.of()
                    : result.hits();
            if (hits.isEmpty()) {
                addDegradedReason(
                        degradedReasons,
                        RagDegradedReason.NO_RELEVANT_KNOWLEDGE
                );
            }
            return hits;
        }
        catch (RuntimeException exception) {
            log.warn(
                    "错题讲解检索降级: questionId={}",
                    question.getId(),
                    exception
            );
            addDegradedReason(
                    degradedReasons,
                    RagDegradedReason.RETRIEVAL_UNAVAILABLE
            );
            return List.of();
        }
    }

    private WrongQuestionExplanationVO toResponse(
            Question question,
            RagExplanationOutput output,
            RagExplanationMode mode,
            boolean ragApplied,
            boolean aiGenerated,
            List<RagDegradedReason> degradedReasons,
            List<KnowledgeSearchHit> includedHits) {
        return new WrongQuestionExplanationVO(
                question.getId(),
                question.getTitle(),
                output.summary().strip(),
                output.keyPoints(),
                output.commonMistakes(),
                output.reviewAdvice().strip(),
                mode,
                ragApplied,
                aiGenerated,
                degradedReasons,
                toCitations(includedHits)
        );
    }

    private WrongQuestionExplanationVO fallbackResponse(
            Question question,
            List<RagDegradedReason> degradedReasons) {
        String answer = StringUtils.hasText(question.getAnswer())
                ? question.getAnswer().strip()
                : "当前暂时无法生成讲解，题库也没有可用参考答案。";
        RagExplanationOutput fallback = new RagExplanationOutput(
                answer,
                List.of(),
                List.of(),
                "请稍后重试 AI 讲解，并先根据参考答案整理核心概念。"
        );

        return toResponse(
                question,
                fallback,
                RagExplanationMode.REFERENCE_ANSWER,
                false,
                false,
                degradedReasons,
                List.of()
        );
    }

    private List<KnowledgeCitationVO> toCitations(
            List<KnowledgeSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }

        List<KnowledgeCitationVO> citations = new ArrayList<>();
        for (int index = 0; index < hits.size(); index++) {
            KnowledgeSearchHit hit = hits.get(index);
            citations.add(new KnowledgeCitationVO(
                    index + 1,
                    hit.chunkId(),
                    hit.documentId(),
                    hit.documentTitle(),
                    hit.headingPath(),
                    hit.score(),
                    excerpt(hit.content())
            ));
        }
        return List.copyOf(citations);
    }

    private String buildRetrievalQuery(Question question) {
        String title = StringUtils.hasText(question.getTitle())
                ? question.getTitle().strip()
                : "";
        String content = StringUtils.hasText(question.getContent())
                ? question.getContent().strip()
                : "";
        String query = (title + "\n" + content).strip();

        if (query.length() <= MAX_RETRIEVAL_QUERY_LENGTH) {
            return query;
        }
        return query.substring(0, MAX_RETRIEVAL_QUERY_LENGTH);
    }

    private String excerpt(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String stripped = content.strip();
        if (stripped.length() <= MAX_EXCERPT_LENGTH) {
            return stripped;
        }
        return stripped.substring(0, MAX_EXCERPT_LENGTH) + "...";
    }

    private void assertOwnWrongQuestion(Long userId, Long questionId) {
        if (!wrongQuestionMapper.existsByUserAndQuestion(userId, questionId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "错题不存在");
        }
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "请先登录");
        }
        return userId;
    }

    private void validateQuestionId(Long questionId) {
        if (questionId == null || questionId <= 0) {
            throw new BusinessException(
                    ErrorCode.PARAMS_ERROR,
                    "题目 id 不合法"
            );
        }
    }

    private void addDegradedReason(
            List<RagDegradedReason> degradedReasons,
            RagDegradedReason reason) {
        if (!degradedReasons.contains(reason)) {
            degradedReasons.add(reason);
        }
    }
}
