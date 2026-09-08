package com.zhimian.task;

import com.zhimian.service.InterviewExpirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewExpirationTask {
    private final InterviewExpirationService expirationService;

    @Scheduled(fixedDelayString = "${zhimian.interview.expire-scan-interval-ms:60000}")
    public void expireInactiveSessions() {
        int expired = expirationService.expireInactiveSessions();
        if (expired > 0) {
            log.info("已自动结束{}场长时间无操作的面试，不生成评价报告", expired);
        }
    }
}
