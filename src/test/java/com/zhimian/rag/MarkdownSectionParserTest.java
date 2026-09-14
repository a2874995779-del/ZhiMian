package com.zhimian.rag;

import com.zhimian.rag.support.MarkdownSection;
import com.zhimian.rag.support.MarkdownSectionParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownSectionParserTest {

    private final MarkdownSectionParser parser = new MarkdownSectionParser();

    @Test
    void shouldReturnEmptyForMissingMarkdown() {
        assertTrue(parser.parse(null, "Java 手册").isEmpty());
        assertTrue(parser.parse("  \n ", "Java 手册").isEmpty());
    }

    @Test
    void shouldBuildNestedHeadingPathAndClearDeeperLevels() {
        String markdown = """
                # Java
                Java 基础正文。
                ## 并发
                并发正文。
                ### AQS
                AQS 正文。
                ## JVM
                JVM 正文。
                """;

        List<MarkdownSection> sections = parser.parse(markdown, "Java 指南");

        assertEquals(4, sections.size());
        assertEquals("Java", sections.get(0).headingPath());
        assertEquals("Java > 并发", sections.get(1).headingPath());
        assertEquals("Java > 并发 > AQS", sections.get(2).headingPath());
        assertEquals("Java > JVM", sections.get(3).headingPath());
    }

    @Test
    void shouldUseDocumentTitleBeforeFirstHeading() {
        String markdown = "开场说明。\n\n# Java\nJava 正文。";

        List<MarkdownSection> sections = parser.parse(markdown, "后端手册");

        assertEquals(2, sections.size());
        assertEquals("后端手册", sections.get(0).headingPath());
        assertEquals("开场说明。", sections.get(0).content());
        assertEquals("Java", sections.get(1).headingPath());
    }

    @Test
    void shouldUseDefaultPathWhenTitleIsBlank() {
        List<MarkdownSection> sections = parser.parse("普通正文。", "  ");

        assertEquals(1, sections.size());
        assertEquals("正文", sections.get(0).headingPath());
    }

    @Test
    void shouldNotTreatHeadingInsideCodeFenceAsSection() {
        String markdown = """
                # Shell
                ```bash
                # 这是一行 shell 注释
                echo hello
                ```
                后续正文。
                """;

        List<MarkdownSection> sections = parser.parse(markdown, "命令手册");

        assertEquals(1, sections.size());
        assertEquals("Shell", sections.get(0).headingPath());
        assertTrue(sections.get(0).content().contains("# 这是一行 shell 注释"));
    }

    @Test
    void shouldNotCloseFenceWhenMarkerHasTrailingContent() {
        String markdown = "# Java\n```text\n```not-closing\n# still code\n```\n正文。";

        List<MarkdownSection> sections = parser.parse(markdown, "Java 手册");

        assertEquals(1, sections.size());
        assertEquals("Java", sections.get(0).headingPath());
        assertTrue(sections.get(0).content().contains("# still code"));
    }

    @Test
    void shouldPreserveHashInHeadingText() {
        List<MarkdownSection> sections = parser.parse(
                "# C#\nC# 基础正文。",
                "语言手册"
        );

        assertEquals(1, sections.size());
        assertEquals("C#", sections.get(0).headingPath());
    }

    @Test
    void shouldSupportClosingHashesAndIndentedHeading() {
        List<MarkdownSection> sections = parser.parse(
                "   ## JVM ##   \nJVM 正文。",
                "Java 手册"
        );

        assertEquals(1, sections.size());
        assertEquals("JVM", sections.get(0).headingPath());
    }
}
