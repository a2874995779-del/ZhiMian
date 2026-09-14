package com.zhimian.rag;

import com.zhimian.rag.support.TokenWindowSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenWindowSplitterTest {

    private final TokenWindowSplitter splitter = new TokenWindowSplitter();

    @Test
    void shouldCountNullAsZeroAndIgnoreBlankInput() {
        assertEquals(0, splitter.countTokens(null));
        assertTrue(splitter.split(" \n\t ", 100, 20).isEmpty());
    }

    @Test
    void shouldKeepShortTextInOneChunk() {
        List<String> chunks = splitter.split(
                "volatile 保证可见性",
                100,
                20
        );

        assertEquals(List.of("volatile 保证可见性"), chunks);
    }

    @Test
    void shouldSplitLongTextWithinTokenLimit() {
        String content = "线程池用于复用工作线程。".repeat(200);

        List<String> chunks = splitter.split(content, 80, 10);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream()
                .allMatch(chunk -> splitter.countTokens(chunk) <= 80));
        assertFalse(chunks.stream().anyMatch(chunk -> chunk.contains("\uFFFD")));
    }

    @Test
    void shouldGenerateExpectedNumberOfWindows() {
        String content = "Java concurrency and thread pool. ".repeat(200);
        int maxTokens = 60;
        int overlapTokens = 15;
        int tokenCount = splitter.countTokens(content.strip());
        int expected = tokenCount <= maxTokens
                ? 1
                : 1 + (int) Math.ceil(
                        (tokenCount - maxTokens)
                                / (double) (maxTokens - overlapTokens));

        List<String> chunks = splitter.split(
                content,
                maxTokens,
                overlapTokens
        );

        assertEquals(expected, chunks.size());
    }

    @Test
    void shouldCreateMoreWindowsWhenOverlapIsEnabled() {
        String content = "Redis vector search. ".repeat(200);

        List<String> withoutOverlap = splitter.split(content, 50, 0);
        List<String> withOverlap = splitter.split(content, 50, 20);

        assertTrue(withOverlap.size() > withoutOverlap.size());
    }

    @Test
    void shouldPreserveAllUnicodeContentWithoutOverlap() {
        String content = "中文切片😀不会丢字符。".repeat(100);

        List<String> chunks = splitter.split(content, 25, 0);

        assertEquals(content, String.join("", chunks));
    }

    @Test
    void shouldRejectInvalidWindowArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> splitter.split("正文", 0, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> splitter.split("正文", 100, -1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> splitter.split("正文", 100, 100)
        );
    }

    @Test
    void shouldReturnImmutableResult() {
        List<String> chunks = splitter.split("短文本", 100, 20);

        assertThrows(
                UnsupportedOperationException.class,
                () -> chunks.add("额外片段")
        );
    }
}
