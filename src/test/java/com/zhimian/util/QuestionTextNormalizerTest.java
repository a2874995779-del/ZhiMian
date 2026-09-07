package com.zhimian.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionTextNormalizerTest {
    private final QuestionTextNormalizer normalizer = new QuestionTextNormalizer();

    @Test
    void equivalentQuestionsProduceSameFingerprint() {
        String first = normalizer.normalize("请问，什么是 JVM 垃圾回收？");
        String second = normalizer.normalize("什么是JVM垃圾回收?");

        assertThat(first).isEqualTo("什么是jvm垃圾回收");
        assertThat(normalizer.fingerprint(first))
                .isEqualTo(normalizer.fingerprint(second))
                .hasSize(64);
    }

    @Test
    void normalizesFullWidthCharacters() {
        assertThat(normalizer.normalize("ＪＶＭ 和 JVM"))
                .isEqualTo("jvm和jvm");
    }

    @Test
    void emptyTextCannotProduceFingerprint() {
        assertThat(normalizer.normalize("   ")).isEmpty();
        assertThatThrownBy(() -> normalizer.fingerprint(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
