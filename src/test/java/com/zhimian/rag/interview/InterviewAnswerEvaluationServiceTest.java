package com.zhimian.rag.interview;

import com.zhimian.common.ErrorCode;
import com.zhimian.config.RagProperties;
import com.zhimian.exception.BusinessException;
import com.zhimian.model.interview.InterviewPlanItem;
import com.zhimian.rag.retrieval.InterviewRagEvidence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewAnswerEvaluationServiceTest {

    @Mock
    private InterviewRagEvidenceProvider evidenceProvider;
    @Mock
    private InterviewEvaluationGenerator generator;

    private ExecutorService executor;
    private RagProperties properties;
    private InterviewAnswerEvaluationService service;
    private InterviewPlanItem item;
    private InterviewRagEvidence evidence;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        properties = new RagProperties();
        properties.setEvaluationTimeoutSeconds(2);
        service = new InterviewAnswerEvaluationService(
                evidenceProvider, generator, executor, properties);
        item = new InterviewPlanItem(
                1, "redis", "Redis", "缓存", "原理", 2, "原题", 1);
        evidence = InterviewRagEvidence.degraded(
                "缓存", InterviewRagEvidenceStatus.NO_RELEVANT_KNOWLEDGE);
        when(evidenceProvider.load("问题", item)).thenReturn(evidence);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void convertsCoveredAndMissingPointsToLegacyEvaluation() {
        GroundedEvaluationOutput output = output();
        when(generator.generate("问题", "回答", evidence)).thenReturn(output);

        InterviewEvaluationResult result = service.evaluate(
                "问题", "回答", item);

        assertThat(result.evaluation().score()).isEqualTo(82);
        assertThat(result.evaluation().evaluation())
                .contains("已覆盖:缓存穿透")
                .contains("建议补充:缓存击穿")
                .contains("回答基本正确");
        assertThat(result.evidence()).isSameAs(evidence);
    }

    @Test
    void retriesGeneratorWithoutRepeatingRetrieval() {
        GroundedEvaluationOutput output = output();
        when(generator.generate("问题", "回答", evidence))
                .thenThrow(new IllegalStateException("第一次失败"))
                .thenReturn(output);

        service.evaluate("问题", "回答", item);

        verify(evidenceProvider, times(1)).load("问题", item);
        verify(generator, times(2)).generate(
                eq("问题"), eq("回答"), same(evidence));
    }

    @Test
    void throwsAiServiceErrorAfterBothAttemptsFail() {
        when(generator.generate("问题", "回答", evidence))
                .thenThrow(new IllegalStateException("模型失败"));

        assertThatThrownBy(() -> service.evaluate("问题", "回答", item))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;
                    assertThat(businessException.getCode())
                            .isEqualTo(ErrorCode.AI_SERVICE_ERROR.getCode());
                });
        verify(generator, times(2)).generate(
                eq("问题"), eq("回答"), same(evidence));
    }

    @Test
    void returnsTimeoutWhenEvaluationDoesNotFinish() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        properties.setEvaluationTimeoutSeconds(1);
        when(generator.generate("问题", "回答", evidence))
                .thenAnswer(invocation -> {
                    release.await(5, TimeUnit.SECONDS);
                    return output();
                });

        assertThatThrownBy(() -> service.evaluate("问题", "回答", item))
                .isInstanceOf(BusinessException.class)
                .hasMessage("单题评分超时");
        release.countDown();
    }

    private GroundedEvaluationOutput output() {
        return new GroundedEvaluationOutput(
                82,
                "回答基本正确",
                List.of("缓存穿透"),
                List.of("缓存击穿"),
                false
        );
    }
}
