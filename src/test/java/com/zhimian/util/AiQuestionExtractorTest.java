package com.zhimian.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiQuestionExtractorTest {
    private final AiQuestionExtractor extractor = new AiQuestionExtractor();

    @Test
    void extractsFirstNonBlankLineAfterLastMarker() {
        String reply = """
                你的回答基本正确。
                【下一题】

                - 什么是 JVM 垃圾回收？
                后面这行不应进入题目。
                """;

        assertThat(extractor.extract(reply)).contains("什么是 JVM 垃圾回收？");
    }

    @Test
    void returnsEmptyWhenMarkerIsMissingOrQuestionIsBlank() {
        assertThat(extractor.extract("请继续回答")).isEmpty();
        assertThat(extractor.extract("【下一题】\n  ")).isEmpty();
        assertThat(extractor.extract(null)).isEmpty();
    }

    @Test
    void usesLastMarker() {
        String reply = "点评中提到【下一题】三个字。\n【下一题】\n解释一下线程池拒绝策略？";

        assertThat(extractor.extract(reply)).contains("解释一下线程池拒绝策略？");
    }

    @Test
    void rejectsQuestionsOutsideLengthBoundary() {
        assertThat(extractor.extract("【下一题】\n好吧")).isEmpty();
        assertThat(extractor.extract("【下一题】\n" + "题".repeat(257))).isEmpty();
    }
}
