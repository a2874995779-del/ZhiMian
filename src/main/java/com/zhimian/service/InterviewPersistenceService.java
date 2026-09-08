package com.zhimian.service;

import com.zhimian.mapper.InterviewMessageMapper;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.model.dto.InterviewAnswerEvaluation;
import com.zhimian.model.entity.InterviewMessage;
import com.zhimian.model.entity.InterviewSession;
import com.zhimian.model.entity.InterviewTurn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InterviewPersistenceService {
    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewTurnService turnService;

    @Transactional
    public CreatedInterview create(InterviewSession session,
                                   String systemPrompt,
                                   String openingMessage,
                                   String openingQuestion) {
        sessionMapper.insert(session);
        InterviewMessage systemMessage = buildMessage(session.getId(), "system", systemPrompt);
        InterviewMessage assistantMessage = buildMessage(session.getId(), "assistant", openingMessage);
        messageMapper.insert(systemMessage);
        messageMapper.insert(assistantMessage);
        InterviewTurn turn = turnService.createWaitingTurn(
                session.getId(), session.getUserId(), 1, openingQuestion
        );
        return new CreatedInterview(session, assistantMessage, turn);
    }

    @Transactional
    public CompletedRound completeRound(Long sessionId,
                                        InterviewTurn currentTurn,
                                        String userContent,
                                        String assistantContent,
                                        InterviewAnswerEvaluation evaluation,
                                        String evaluationFailureReason,
                                        String nextQuestion,
                                        boolean finalRound) {
        InterviewMessage userMessage = buildMessage(sessionId, "user", userContent);
        InterviewMessage assistantMessage = buildMessage(sessionId, "assistant", assistantContent);
        messageMapper.insert(userMessage);
        messageMapper.insert(assistantMessage);

        boolean completed = evaluation == null
                ? turnService.completeWithoutScore(
                        currentTurn.getId(), sessionId, userContent, evaluationFailureReason
                )
                : turnService.completeEvaluated(
                        currentTurn.getId(), sessionId, userContent, evaluation
                );
        if (!completed) {
            throw new IllegalStateException("本轮回答已经被处理，请刷新会话");
        }

        InterviewTurn nextTurn = null;
        if (finalRound) {
            if (sessionMapper.endSession(sessionId, "AUTO_LIMIT") != 1) {
                throw new IllegalStateException("面试会话状态已变化，无法自动结束");
            }
        } else {
            nextTurn = turnService.createWaitingTurn(
                    sessionId,
                    currentTurn.getUserId(),
                    currentTurn.getRoundNo() + 1,
                    nextQuestion
            );
        }
        return new CompletedRound(assistantMessage, nextTurn);
    }

    @Transactional
    public void finishByUser(Long sessionId) {
        turnService.skipWaiting(sessionId);
        sessionMapper.endSession(sessionId, "USER_STOP");
    }

    @Transactional
    public boolean expireInactive(Long sessionId, LocalDateTime cutoff) {
        int expired = sessionMapper.expireInactiveSession(
                sessionId, cutoff, InterviewExpirationService.FINISH_REASON
        );
        if (expired == 0) {
            return false;
        }
        turnService.skipWaiting(sessionId);
        return true;
    }

    private InterviewMessage buildMessage(Long sessionId, String role, String content) {
        InterviewMessage message = new InterviewMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    public record CreatedInterview(
            InterviewSession session,
            InterviewMessage assistantMessage,
            InterviewTurn turn
    ) {
    }

    public record CompletedRound(
            InterviewMessage assistantMessage,
            InterviewTurn nextTurn
    ) {
    }
}
