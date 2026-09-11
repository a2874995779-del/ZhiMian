package com.zhimian.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeContentHasherTest {

    private KnowledgeContentHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new KnowledgeContentHasher();
    }

    @Test
    void sameContentWithDifferentLineEndingsHasSameHash() {
        String windows = "# 线程池\r\n\r\n四种拒绝策略";
        String linux = "# 线程池\n\n四种拒绝策略";

        assertThat(hasher.sha256(windows)).isEqualTo(hasher.sha256(linux));
    }

    @Test
    void leadingAndTrailingWhitespaceDoesNotChangeHash() {
        assertThat(hasher.sha256("  Java线程池  "))
                .isEqualTo(hasher.sha256("Java线程池"));
    }

    @Test
    void differentContentHasDifferentHash() {
        assertThat(hasher.sha256("线程池"))
                .isNotEqualTo(hasher.sha256("Spring事务"));
    }

    @Test
    void sha256UsesSixtyFourLowercaseHexCharacters() {
        assertThat(hasher.sha256("智面知识库")).matches("[0-9a-f]{64}");
    }

    @Test
    void blankContentNormalizesToEmptyString() {
        assertThat(hasher.normalize(" \r\n ")).isEmpty();
        assertThat(hasher.normalize(null)).isEmpty();
    }
}
