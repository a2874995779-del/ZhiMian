package com.zhimian.service;

import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.mapper.InterviewTurnMapper;
import com.zhimian.model.dto.InterviewAnswerEvaluation;
import com.zhimian.model.entity.InterviewTurn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewTurnService {
    public static final int STATUS_WAITING = 0;

    private final InterviewTurnMapper interviewTurnMapper;
    private final InterviewSessionMapper interviewSessionMapper;

    public InterviewTurn createWaitingTurn(Long sessionId, Long userId, int roundNo, String questionText) {
        InterviewTurn turn = new InterviewTurn();
        turn.setSessionId(sessionId);
        turn.setUserId(userId);
        turn.setRoundNo(roundNo);
        turn.setQuestionText(questionText);
        turn.setStatus(STATUS_WAITING);
        interviewTurnMapper.insert(turn);
        return turn;
    }

    @Transactional
    public boolean completeEvaluated(Long turnId, Long sessionId, String answer,
                                     InterviewAnswerEvaluation evaluation) {
        int score = Math.max(0, Math.min(100, evaluation.score()));
        int result = score >= 60 ? 1 : 0;
        int updated = interviewTurnMapper.markEvaluated(
                turnId, answer, score, result, evaluation.evaluation()
        );
        if (updated == 0) {
            return false;
        }
        ensureSessionIncremented(sessionId);
        return true;
    }

    @Transactional
    public boolean completeWithoutScore(Long turnId, Long sessionId, String answer, String reason) {
        int updated = interviewTurnMapper.markEvaluationFailed(turnId, answer, reason);
        if (updated == 0) {
            return false;
        }
        ensureSessionIncremented(sessionId);
        return true;
    }

    public void skipWaiting(Long sessionId) {
        interviewTurnMapper.skipWaiting(sessionId);
    }

    private void ensureSessionIncremented(Long sessionId) {
        if (interviewSessionMapper.incrementAnsweredCount(sessionId) != 1) {
            throw new IllegalStateException("面试会话状态已变化，无法记录本轮回答");
        }
    }
}
