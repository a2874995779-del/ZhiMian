package com.zhimian.util;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
public class AiQuestionExtractor {
    public static final String QUESTION_MARKER = "【下一题】";

    public Optional<String> extract(String assistantReply) {
        if (!StringUtils.hasText(assistantReply)) {
            return Optional.empty();
        }

        int markerIndex = assistantReply.lastIndexOf(QUESTION_MARKER);
        if (markerIndex < 0) {
            return Optional.empty();
        }

        String afterMarker = assistantReply
                .substring(markerIndex + QUESTION_MARKER.length())
                .strip();
        if (!StringUtils.hasText(afterMarker)) {
            return Optional.empty();
        }

        String firstLine = afterMarker.lines()
                .map(String::strip)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");

        String question = firstLine
                .replaceFirst("^[-*]+\\s*", "")
                .strip();

        if (question.length() < 4 || question.length() > 256) {
            return Optional.empty();
        }
        return Optional.of(question);
    }
}
