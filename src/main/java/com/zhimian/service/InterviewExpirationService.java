package com.zhimian.service;

import com.zhimian.ai.InterviewContextManager;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.redis.RedisLockManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class InterviewExpirationService {
    public static final String FINISH_REASON = "INACTIVITY_TIMEOUT";
    private static final String CHAT_LOCK_PREFIX = "zhimian:interview:chat-lock:";
    private static final Duration EXPIRATION_LOCK_TTL = Duration.ofSeconds(30);
    private static final int SCAN_BATCH_SIZE = 200;

    private final InterviewSessionMapper sessionMapper;
    private final InterviewPersistenceService persistenceService;
    private final InterviewContextManager contextManager;
    private final RedisLockManager redisLockManager;
    private final long inactiveTimeoutMinutes;

    public InterviewExpirationService(
            InterviewSessionMapper sessionMapper,
            InterviewPersistenceService persistenceService,
            InterviewContextManager contextManager,
            RedisLockManager redisLockManager,
            @Value("${zhimian.interview.inactive-timeout-minutes:120}") long inactiveTimeoutMinutes
    ) {
        this.sessionMapper = sessionMapper;
        this.persistenceService = persistenceService;
        this.contextManager = contextManager;
        this.redisLockManager = redisLockManager;
        this.inactiveTimeoutMinutes = Math.max(1, inactiveTimeoutMinutes);
    }

    public int expireInactiveForUser(Long userId) {
        LocalDateTime cutoff = cutoff();
        return expireCandidates(
                sessionMapper.selectExpiredIdsByUserId(userId, cutoff, SCAN_BATCH_SIZE), cutoff
        );
    }

    public int expireInactiveSessions() {
        LocalDateTime cutoff = cutoff();
        return expireCandidates(sessionMapper.selectExpiredIds(cutoff, SCAN_BATCH_SIZE), cutoff);
    }

    public boolean expireIfInactive(Long sessionId) {
        return expireOne(sessionId, cutoff());
    }

    /**
     * 调用方已经持有该会话的聊天锁时使用，避免重复获取同一把非可重入锁。
     */
    public boolean expireWhileHoldingChatLock(Long sessionId) {
        return expirePersistedSession(sessionId, cutoff());
    }

    private int expireCandidates(List<Long> sessionIds, LocalDateTime cutoff) {
        int expired = 0;
        for (Long sessionId : sessionIds) {
            if (expireOne(sessionId, cutoff)) {
                expired++;
            }
        }
        return expired;
    }

    private boolean expireOne(Long sessionId, LocalDateTime cutoff) {
        String lockKey = CHAT_LOCK_PREFIX + sessionId;
        String lockToken = redisLockManager.tryLock(lockKey, EXPIRATION_LOCK_TTL);
        if (lockToken == null) {
            return false;
        }
        try {
            return expirePersistedSession(sessionId, cutoff);
        } finally {
            redisLockManager.unlock(lockKey, lockToken);
        }
    }

    private boolean expirePersistedSession(Long sessionId, LocalDateTime cutoff) {
        boolean expired = persistenceService.expireInactive(sessionId, cutoff);
        if (expired) {
            evictContextQuietly(sessionId);
        }
        return expired;
    }

    private LocalDateTime cutoff() {
        return LocalDateTime.now().minusMinutes(inactiveTimeoutMinutes);
    }

    private void evictContextQuietly(Long sessionId) {
        try {
            contextManager.evict(sessionId);
        } catch (RuntimeException e) {
            log.warn("清理过期面试上下文失败:sessionId={}", sessionId, e);
        }
    }
}
