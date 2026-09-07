package com.zhimian.ai;

import com.zhimian.service.AiQuestionCollectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
public class AiQuestionCollectDispatcher {
    private final Executor questionCollectExecutor;
    private final AiQuestionCollectorService collectorService;

    public AiQuestionCollectDispatcher(
            @Qualifier("questionCollectExecutor") Executor questionCollectExecutor,
            AiQuestionCollectorService collectorService) {
        this.questionCollectExecutor = questionCollectExecutor;
        this.collectorService = collectorService;
    }

    public void dispatch(Long sessionId,
                         Long assistantMessageId,
                         String direction,
                         String question) {
        try {
            questionCollectExecutor.execute(() -> {
                try {
                    collectorService.collect(sessionId, assistantMessageId, direction, question);
                } catch (Exception e) {
                    log.error("候选题收集任务执行失败:messageId={}", assistantMessageId, e);
                }
            });
        } catch (RejectedExecutionException e) {
            // 收集失败不能反过来让面试聊天失败。
            log.warn("候选题收集队列已满:messageId={}", assistantMessageId);
        }
    }
}
