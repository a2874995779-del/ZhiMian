package com.zhimian.service.impl;
import com.zhimian.common.UserContext;
import com.zhimian.config.RagProperties;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.QuestionMapper;
import com.zhimian.mapper.WrongQuestionMapper;
import com.zhimian.model.entity.Question;
import com.zhimian.model.enums.RagDegradedReason;
import com.zhimian.model.enums.RagExplanationMode;
import com.zhimian.model.vo.WrongQuestionExplanationVO;
import com.zhimian.rag.generation.AssembledKnowledgeContext;
import com.zhimian.rag.generation.KnowledgeContextAssembler;
import com.zhimian.rag.generation.RagExplanationGenerator;
import com.zhimian.rag.generation.RagExplanationOutput;
import com.zhimian.rag.retrieval.KnowledgeRetrievalService;
import com.zhimian.rag.retrieval.KnowledgeSearchHit;
import com.zhimian.rag.retrieval.KnowledgeSearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WrongQuestionExplanationServiceImplTest {

    @Mock
    private WrongQuestionMapper wrongQuestionMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private KnowledgeRetrievalService retrievalService;
    @Mock
    private KnowledgeContextAssembler contextAssembler;
    @Mock
    private RagExplanationGenerator explanationGenerator;

    private RagProperties properties;
    private WrongQuestionExplanationServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new RagProperties();
        properties.setEnabled(true);
        service = new WrongQuestionExplanationServiceImpl(
                wrongQuestionMapper,
                questionMapper,
                retrievalService,
                contextAssembler,
                explanationGenerator,
                properties
        );
        UserContext.set(7L, "USER", "test-jti", Long.MAX_VALUE);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void returnsRagExplanationWithServerBuiltCitation() {
        Question question = question();
        KnowledgeSearchHit hit = hit();
        when(wrongQuestionMapper.existsByUserAndQuestion(7L, 12L))
                .thenReturn(true);
        when(questionMapper.selectById(12L)).thenReturn(question);
        when(retrievalService.search(anyString(), any(), any()))
                .thenReturn(new KnowledgeSearchResult(
                        "缓存击穿",
                        5,
                        0.70,
                        List.of(hit)
                ));
        when(contextAssembler.assemble(List.of(hit)))
                .thenReturn(new AssembledKnowledgeContext(
                        "[资料1] 缓存击穿资料",
                        30,
                        List.of(hit)
                ));
        when(explanationGenerator.generate(
                question,
                "[资料1] 缓存击穿资料"
        )).thenReturn(output());

        WrongQuestionExplanationVO result = service.explain(12L);

        assertThat(result.mode()).isEqualTo(RagExplanationMode.RAG);
        assertThat(result.ragApplied()).isTrue();
        assertThat(result.aiGenerated()).isTrue();
        assertThat(result.degradedReasons()).isEmpty();
        assertThat(result.citations())
                .singleElement()
                .satisfies(citation -> {
                    assertThat(citation.index()).isEqualTo(1);
                    assertThat(citation.chunkId()).isEqualTo(18L);
                });
    }

    @Test
    void rejectsQuestionOutsideCurrentUsersWrongBook() {
        when(wrongQuestionMapper.existsByUserAndQuestion(7L, 12L))
                .thenReturn(false);

        assertThatThrownBy(() -> service.explain(12L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("错题不存在");

        verify(questionMapper, never()).selectById(any());
        verify(retrievalService, never())
                .search(anyString(), any(), any());
        verify(explanationGenerator, never())
                .generate(any(), anyString());
    }

    @Test
    void usesModelOnlyWhenRetrievalHasNoHits() {
        Question question = question();
        allowQuestion(question);
        when(retrievalService.search(anyString(), any(), any()))
                .thenReturn(new KnowledgeSearchResult(
                        "缓存击穿", 5, 0.70, List.of()));
        when(contextAssembler.assemble(List.of()))
                .thenReturn(new AssembledKnowledgeContext(
                        "", 0, List.of()));
        when(explanationGenerator.generate(question, ""))
                .thenReturn(output());

        WrongQuestionExplanationVO result = service.explain(12L);

        assertThat(result.mode())
                .isEqualTo(RagExplanationMode.MODEL_ONLY);
        assertThat(result.ragApplied()).isFalse();
        assertThat(result.aiGenerated()).isTrue();
        assertThat(result.degradedReasons())
                .containsExactly(
                        RagDegradedReason.NO_RELEVANT_KNOWLEDGE
                );
    }

    @Test
    void degradesWhenRetrievalIsUnavailable() {
        Question question = question();
        allowQuestion(question);
        when(retrievalService.search(anyString(), any(), any()))
                .thenThrow(new IllegalStateException("Redis unavailable"));
        when(contextAssembler.assemble(List.of()))
                .thenReturn(new AssembledKnowledgeContext(
                        "", 0, List.of()));
        when(explanationGenerator.generate(question, ""))
                .thenReturn(output());

        WrongQuestionExplanationVO result = service.explain(12L);

        assertThat(result.mode())
                .isEqualTo(RagExplanationMode.MODEL_ONLY);
        assertThat(result.degradedReasons())
                .containsExactly(
                        RagDegradedReason.RETRIEVAL_UNAVAILABLE
                );
    }

    @Test
    void fallsBackToReferenceAnswerWhenAiFails() {
        Question question = question();
        KnowledgeSearchHit hit = hit();
        allowQuestion(question);
        when(retrievalService.search(anyString(), any(), any()))
                .thenReturn(new KnowledgeSearchResult(
                        "缓存击穿", 5, 0.70, List.of(hit)));
        when(contextAssembler.assemble(List.of(hit)))
                .thenReturn(new AssembledKnowledgeContext(
                        "[资料1] 缓存击穿资料",
                        30,
                        List.of(hit)
                ));
        when(explanationGenerator.generate(any(), anyString()))
                .thenThrow(new IllegalStateException("AI unavailable"));

        WrongQuestionExplanationVO result = service.explain(12L);

        assertThat(result.mode())
                .isEqualTo(RagExplanationMode.REFERENCE_ANSWER);
        assertThat(result.ragApplied()).isFalse();
        assertThat(result.aiGenerated()).isFalse();
        assertThat(result.summary()).isEqualTo(question.getAnswer());
        assertThat(result.degradedReasons())
                .contains(RagDegradedReason.AI_UNAVAILABLE);
    }

    @Test
    void skipsRetrievalWhenRagIsDisabled() {
        Question question = question();
        allowQuestion(question);
        properties.setEnabled(false);
        when(contextAssembler.assemble(List.of()))
                .thenReturn(new AssembledKnowledgeContext(
                        "", 0, List.of()));
        when(explanationGenerator.generate(question, ""))
                .thenReturn(output());

        WrongQuestionExplanationVO result = service.explain(12L);

        verify(retrievalService, never())
                .search(anyString(), any(), any());
        assertThat(result.degradedReasons())
                .containsExactly(RagDegradedReason.RAG_DISABLED);
    }

    @Test
    void rejectsInvalidQuestionIdBeforeCallingDependencies() {
        assertThatThrownBy(() -> service.explain(0L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("题目 id 不合法");

        verifyNoInteractions(
                wrongQuestionMapper,
                questionMapper,
                retrievalService,
                contextAssembler,
                explanationGenerator
        );
    }

    @Test
    void requiresLoggedInUserBeforeCheckingOwnership() {
        UserContext.remove();

        assertThatThrownBy(() -> service.explain(12L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先登录");

        verifyNoInteractions(
                wrongQuestionMapper,
                questionMapper,
                retrievalService,
                contextAssembler,
                explanationGenerator
        );
    }

    @Test
    void rejectsDeletedQuestionAfterOwnershipCheck() {
        when(wrongQuestionMapper.existsByUserAndQuestion(7L, 12L))
                .thenReturn(true);
        when(questionMapper.selectById(12L)).thenReturn(null);

        assertThatThrownBy(() -> service.explain(12L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("错题不存在");

        verifyNoInteractions(
                retrievalService,
                contextAssembler,
                explanationGenerator
        );
    }

    @Test
    void limitsRetrievalQueryAndDoesNotIncludeReferenceAnswer() {
        Question question = question();
        question.setContent("题干".repeat(300));
        question.setAnswer("不能进入检索问题的标准答案");
        allowQuestion(question);
        when(retrievalService.search(anyString(), any(), any()))
                .thenReturn(new KnowledgeSearchResult(
                        "query", 5, 0.70, List.of()));
        when(contextAssembler.assemble(List.of()))
                .thenReturn(new AssembledKnowledgeContext(
                        "", 0, List.of()));
        when(explanationGenerator.generate(question, ""))
                .thenReturn(output());

        service.explain(12L);

        ArgumentCaptor<String> queryCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(retrievalService).search(
                queryCaptor.capture(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull()
        );
        assertThat(queryCaptor.getValue()).hasSize(500);
        assertThat(queryCaptor.getValue())
                .startsWith(question.getTitle())
                .doesNotContain(question.getAnswer());
    }

    @Test
    void keepsBothReasonsWhenRetrievalAndAiAreUnavailable() {
        Question question = question();
        allowQuestion(question);
        when(retrievalService.search(anyString(), any(), any()))
                .thenThrow(new IllegalStateException("Redis unavailable"));
        when(contextAssembler.assemble(List.of()))
                .thenReturn(new AssembledKnowledgeContext(
                        "", 0, List.of()));
        when(explanationGenerator.generate(question, ""))
                .thenThrow(new IllegalStateException("AI unavailable"));

        WrongQuestionExplanationVO result = service.explain(12L);

        assertThat(result.mode())
                .isEqualTo(RagExplanationMode.REFERENCE_ANSWER);
        assertThat(result.degradedReasons()).containsExactly(
                RagDegradedReason.RETRIEVAL_UNAVAILABLE,
                RagDegradedReason.AI_UNAVAILABLE
        );
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void usesFixedFallbackWhenQuestionHasNoReferenceAnswer() {
        Question question = question();
        question.setAnswer("  ");
        allowQuestion(question);
        when(retrievalService.search(anyString(), any(), any()))
                .thenReturn(new KnowledgeSearchResult(
                        "query", 5, 0.70, List.of()));
        when(contextAssembler.assemble(List.of()))
                .thenReturn(new AssembledKnowledgeContext(
                        "", 0, List.of()));
        when(explanationGenerator.generate(question, ""))
                .thenThrow(new IllegalStateException("AI unavailable"));

        WrongQuestionExplanationVO result = service.explain(12L);

        assertThat(result.summary())
                .isEqualTo("当前暂时无法生成讲解，题库也没有可用参考答案。");
        assertThat(result.aiGenerated()).isFalse();
    }

    private void allowQuestion(Question question) {
        when(wrongQuestionMapper.existsByUserAndQuestion(7L, 12L))
                .thenReturn(true);
        when(questionMapper.selectById(12L)).thenReturn(question);
    }

    private Question question() {
        Question question = new Question();
        question.setId(12L);
        question.setTitle("什么是 Redis 缓存击穿？");
        question.setContent("说明原因和解决办法");
        question.setAnswer("热点 Key 失效后大量请求同时访问数据库");
        return question;
    }

    private KnowledgeSearchHit hit() {
        return new KnowledgeSearchHit(
                18L,
                6L,
                "Java 高并发与 Redis 缓存",
                3,
                "缓存问题 > 缓存击穿",
                "缓存击穿发生在热点 Key 失效时",
                0.89
        );
    }

    private RagExplanationOutput output() {
        return new RagExplanationOutput(
                "缓存击穿的核心是单个热点 Key 失效。",
                List.of("热点 Key", "高并发回源"),
                List.of("不要和缓存穿透混淆"),
                "对比穿透、击穿和雪崩。"
        );
    }
}
