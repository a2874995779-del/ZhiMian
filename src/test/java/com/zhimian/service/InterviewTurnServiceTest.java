package com.zhimian.service;

import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.mapper.InterviewTurnMapper;
import com.zhimian.model.dto.InterviewAnswerEvaluation;
import com.zhimian.model.entity.InterviewTurn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewTurnServiceTest {
    @Mock
    private InterviewTurnMapper interviewTurnMapper;
    @Mock
    private InterviewSessionMapper interviewSessionMapper;

    private InterviewTurnService service;

    @BeforeEach
    void setUp() {
        service = new InterviewTurnService(interviewTurnMapper, interviewSessionMapper);
    }

    @Test
    void createWaitingTurnPersistsRoundMetadata() {
        service.createWaitingTurn(11L, 22L, 3, "什么是 AQS？");

        ArgumentCaptor<InterviewTurn> captor = ArgumentCaptor.forClass(InterviewTurn.class);
        verify(interviewTurnMapper).insert(captor.capture());
        assertThat(captor.getValue().getSessionId()).isEqualTo(11L);
        assertThat(captor.getValue().getUserId()).isEqualTo(22L);
        assertThat(captor.getValue().getRoundNo()).isEqualTo(3);
        assertThat(captor.getValue().getStatus()).isZero();
    }

    @Test
    void evaluatedTurnUsesSixtyAsCorrectThresholdAndIncrementsSessionOnce() {
        when(interviewTurnMapper.markEvaluated(7L, "回答", 60, 1, "核心结论正确"))
                .thenReturn(1);
        when(interviewSessionMapper.incrementAnsweredCount(11L)).thenReturn(1);

        boolean completed = service.completeEvaluated(
                7L, 11L, "回答", new InterviewAnswerEvaluation(60, "核心结论正确")
        );

        assertThat(completed).isTrue();
        verify(interviewSessionMapper).incrementAnsweredCount(11L);
    }

    @Test
    void duplicateCompletionDoesNotIncrementSessionAgain() {
        when(interviewTurnMapper.markEvaluated(7L, "回答", 80, 1, "正确"))
                .thenReturn(0);

        boolean completed = service.completeEvaluated(
                7L, 11L, "回答", new InterviewAnswerEvaluation(80, "正确")
        );

        assertThat(completed).isFalse();
        verify(interviewSessionMapper, never()).incrementAnsweredCount(11L);
    }

    @Test
    void failedEvaluationStillCountsAsAnsweredButHasNoResult() {
        when(interviewTurnMapper.markEvaluationFailed(7L, "回答", "评分失败"))
                .thenReturn(1);
        when(interviewSessionMapper.incrementAnsweredCount(11L)).thenReturn(1);

        assertThat(service.completeWithoutScore(7L, 11L, "回答", "评分失败")).isTrue();

        verify(interviewSessionMapper).incrementAnsweredCount(11L);
    }
}
