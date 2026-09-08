package com.zhimian.service;

import com.zhimian.mapper.InterviewMessageMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.model.dto.InterviewAnswerEvaluation;
import com.zhimian.model.entity.InterviewMessage;
import com.zhimian.model.entity.InterviewSession;
import com.zhimian.model.entity.InterviewTurn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
class InterviewPersistenceServiceTest {
    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewMessageMapper messageMapper;
    @Mock
    private InterviewTurnService turnService;

    private InterviewPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new InterviewPersistenceService(sessionMapper, messageMapper, turnService);
    }

    @Test
    void createPersistsSessionMessagesAndFirstTurnTogether() {
        InterviewSession session = new InterviewSession();
        session.setId(11L);
        session.setUserId(22L);
        InterviewTurn firstTurn = turn(7L, 1);
        when(turnService.createWaitingTurn(11L, 22L, 1, "第一题")).thenReturn(firstTurn);

        InterviewPersistenceService.CreatedInterview created = service.create(
                session, "system", "opening", "第一题"
        );

        verify(sessionMapper).insert(session);
        ArgumentCaptor<InterviewMessage> messages = ArgumentCaptor.forClass(InterviewMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(messages.capture());
        assertThat(messages.getAllValues())
                .extracting(InterviewMessage::getRole)
                .containsExactly("system", "assistant");
        assertThat(created.turn()).isSameAs(firstTurn);
    }

    @Test
    void completeRoundCreatesNextTurnOnlyAfterCurrentTurnCompletes() {
        InterviewTurn current = turn(7L, 2);
        current.setUserId(22L);
        when(turnService.completeEvaluated(
                7L, 11L, "回答", new InterviewAnswerEvaluation(80, "正确")
        )).thenReturn(true);
        InterviewTurn next = turn(8L, 3);
        when(turnService.createWaitingTurn(11L, 22L, 3, "下一题")).thenReturn(next);

        InterviewPersistenceService.CompletedRound completed = service.completeRound(
                11L,
                current,
                "回答",
                "点评\n【下一题】\n下一题",
                new InterviewAnswerEvaluation(80, "正确"),
                null,
                "下一题",
                false
        );

        assertThat(completed.nextTurn()).isSameAs(next);
        verify(sessionMapper, never()).endSession(any(), any());
    }

    @Test
    void duplicateCompletionCannotAdvanceSession() {
        InterviewTurn current = turn(7L, 2);
        when(turnService.completeWithoutScore(7L, 11L, "回答", "评分失败"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.completeRound(
                11L, current, "回答", "回复", null, "评分失败", "下一题", false
        )).isInstanceOf(IllegalStateException.class);

        verify(turnService, never()).createWaitingTurn(any(), any(), any(Integer.class), any());
        verify(sessionMapper, never()).endSession(any(), any());
    }

    @Test
    void expireInactiveSkipsWaitingTurnOnlyWhenSessionWasExpired() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 8, 10, 0);
        when(sessionMapper.expireInactiveSession(
                11L, cutoff, InterviewExpirationService.FINISH_REASON
        )).thenReturn(1);

        assertThat(service.expireInactive(11L, cutoff)).isTrue();

        verify(turnService).skipWaiting(11L);
    }

    @Test
    void expireInactiveDoesNotSkipTurnWhenSessionBecameActiveAgain() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 8, 10, 0);
        when(sessionMapper.expireInactiveSession(
                11L, cutoff, InterviewExpirationService.FINISH_REASON
        )).thenReturn(0);

        assertThat(service.expireInactive(11L, cutoff)).isFalse();

        verify(turnService, never()).skipWaiting(11L);
    }

    private InterviewTurn turn(Long id, int roundNo) {
        InterviewTurn turn = new InterviewTurn();
        turn.setId(id);
        turn.setRoundNo(roundNo);
        return turn;
    }
}
