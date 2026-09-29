package com.zhimian.service;

public interface AiQuestionCollectorService {
    void collect(Long userId,
                 Long sessionId,
                 Long assistantMessageId,
                 String direction,
                 String question);
}
