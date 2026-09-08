package com.zhimian.service;

import com.zhimian.ai.InterviewContextManager;
import com.zhimian.mapper.InterviewSessionMapper;
import com.zhimian.redis.RedisLockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewExpirationServiceTest {
    @Mock
    private InterviewSessionMapper sessionMapper;
    @Mock
    private InterviewPersistenceService persistenceService;
    @Mock
    private InterviewContextManager contextManager;
    @Mock
    private RedisLockManager redisLockManager;

    private InterviewExpirationService service;

    @BeforeEach
    void setUp() {
        service = new InterviewExpirationService(
                sessionMapper, persistenceService, contextManager, redisLockManager, 120
        );
    }

    @Test
    void userScanExpiresUnlockedSessionsAndEvictsTheirContext() {
        when(sessionMapper.selectExpiredIdsByUserId(eq(7L), any(LocalDateTime.class), eq(200)))
                .thenReturn(List.of(11L, 12L));
        when(redisLockManager.tryLock(eq("zhimian:interview:chat-lock:11"), any()))
                .thenReturn("token-11");
        when(redisLockManager.tryLock(eq("zhimian:interview:chat-lock:12"), any()))
                .thenReturn(null);
        when(persistenceService.expireInactive(eq(11L), any(LocalDateTime.class))).thenReturn(true);

        assertThat(service.expireInactiveForUser(7L)).isEqualTo(1);

        verify(contextManager).evict(11L);
        verify(persistenceService, never()).expireInactive(eq(12L), any(LocalDateTime.class));
        verify(redisLockManager).unlock("zhimian:interview:chat-lock:11", "token-11");
    }

    @Test
    void sessionUpdatedAfterCandidateSelectionIsNotEvicted() {
        when(redisLockManager.tryLock(eq("zhimian:interview:chat-lock:11"), any()))
                .thenReturn("token-11");
        when(persistenceService.expireInactive(eq(11L), any(LocalDateTime.class))).thenReturn(false);

        assertThat(service.expireIfInactive(11L)).isFalse();

        verify(contextManager, never()).evict(11L);
        verify(redisLockManager).unlock("zhimian:interview:chat-lock:11", "token-11");
    }

    @Test
    void callerHoldingChatLockCanExpireWithoutReacquiringIt() {
        when(persistenceService.expireInactive(eq(11L), any(LocalDateTime.class))).thenReturn(true);

        assertThat(service.expireWhileHoldingChatLock(11L)).isTrue();

        verify(contextManager).evict(11L);
        verify(redisLockManager, never()).tryLock(any(), any());
    }
}
