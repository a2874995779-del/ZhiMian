package com.zhimian.rag;

import com.zhimian.rag.support.KnowledgeTextCleaner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeTextCleanerTest {

    private final KnowledgeTextCleaner cleaner = new KnowledgeTextCleaner();

    @Test
    void shouldReturnEmptyForMissingText() {
        assertEquals("", cleaner.clean(null));
        assertEquals("", cleaner.clean(""));
        assertEquals("", cleaner.clean(" \n\t "));
    }

    @Test
    void shouldNormalizeLineEndingsAndRemoveBom() {
        String input = "\uFEFF# Java\r\n\r正文\r";

        String result = cleaner.clean(input);

        assertEquals("# Java\n\n正文", result);
    }

    @Test
    void shouldCollapseExcessBlankLinesOutsideCodeFence() {
        String input = "第一段\n\n\n\n第二段";

        String result = cleaner.clean(input);

        assertEquals("第一段\n\n\n第二段", result);
    }

    @Test
    void shouldPreserveWhitespaceInsideCodeFence() {
        String input = "```text\nvalue  \n\n\n\nnext  \n```";

        String result = cleaner.clean(input);

        assertEquals(input, result);
    }

    @Test
    void shouldNotCloseFenceWhenMarkerHasTrailingContent() {
        String input = "```text\n```not-closing  \nvalue  \n```\nafter  ";

        String result = cleaner.clean(input);

        assertEquals(
                "```text\n```not-closing  \nvalue  \n```\nafter",
                result
        );
    }

    @Test
    void shouldRespectOpeningFenceLength() {
        String input = "````markdown\n```java\n# code\n```\n````";

        String result = cleaner.clean(input);

        assertEquals(input, result);
    }

    @Test
    void shouldBeIdempotent() {
        String input = "\uFEFF第一段\r\n\r\n\r\n\r\n第二段  ";

        String once = cleaner.clean(input);
        String twice = cleaner.clean(once);

        assertEquals(once, twice);
    }
}
