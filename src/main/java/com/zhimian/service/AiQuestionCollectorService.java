package com.zhimian.service;

public interface AiQuestionCollectorService {
    void collect(Long sessionId,
                 Long assistantMessageId,
                 String direction,
                 String question);
}
