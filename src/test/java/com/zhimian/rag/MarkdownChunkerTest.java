package com.zhimian.rag;

import com.zhimian.config.RagProperties;
import com.zhimian.model.dto.KnowledgeChunkDraft;
import com.zhimian.rag.support.MarkdownChunker;
import com.zhimian.rag.support.MarkdownSectionParser;
import com.zhimian.rag.support.KnowledgeTextCleaner;
import com.zhimian.rag.support.TokenWindowSplitter;
import com.zhimian.util.KnowledgeContentHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownChunkerTest {

    private RagProperties properties;
    private MarkdownChunker chunker;

    @BeforeEach
    void setUp() {
        properties = new RagProperties();
        properties.setChunkSizeTokens(80);
        properties.setChunkOverlapTokens(10);
        properties.setMaxChunksPerDocument(100);

        chunker = new MarkdownChunker(
                new KnowledgeTextCleaner(),
                new MarkdownSectionParser(),
                new TokenWindowSplitter(),
                new KnowledgeContentHasher(),
                properties
        );
    }

    @Test
    void shouldChunkMarkdownByHeading() {
        String markdown = """
                # Java
                Java 是面向对象语言。
                ## JVM
                JVM 负责执行字节码。
                """;

        List<KnowledgeChunkDraft> drafts = chunker.chunk(
                "Java 手册",
                "markdown",
                markdown
        );

        assertEquals(2, drafts.size());
        assertEquals(0, drafts.get(0).chunkIndex());
        assertEquals(1, drafts.get(1).chunkIndex());
        assertEquals("Java", drafts.get(0).headingPath());
        assertEquals("Java > JVM", drafts.get(1).headingPath());
    }

    @Test
    void shouldTreatTextAsSingleSectionBeforeTokenSplit() {
        List<KnowledgeChunkDraft> drafts = chunker.chunk(
                "数据库说明",
                " TEXT ",
                "MySQL 使用 B+Tree 索引。"
        );

        assertEquals(1, drafts.size());
        assertEquals("数据库说明", drafts.get(0).headingPath());
        assertEquals("MySQL 使用 B+Tree 索引。", drafts.get(0).content());
    }

    @Test
    void shouldProduceStableResultForEquivalentLineEndings() {
        List<KnowledgeChunkDraft> windowsResult = chunker.chunk(
                "Redis 手册",
                "MARKDOWN",
                "# Redis\r\nRedis 是内存数据结构存储。\r\n"
        );
        List<KnowledgeChunkDraft> linuxResult = chunker.chunk(
                "Redis 手册",
                "MARKDOWN",
                "# Redis\nRedis 是内存数据结构存储。\n"
        );

        assertEquals(windowsResult, linuxResult);
        assertFalse(windowsResult.get(0).contentHash().isBlank());
        assertEquals(64, windowsResult.get(0).contentHash().length());
    }

    @Test
    void shouldIncludeHeadingPathInChunkIdentity() {
        List<KnowledgeChunkDraft> first = chunker.chunk(
                "手册",
                "MARKDOWN",
                "# Java\n默认值为 10。"
        );
        List<KnowledgeChunkDraft> second = chunker.chunk(
                "手册",
                "MARKDOWN",
                "# Redis\n默认值为 10。"
        );

        assertFalse(first.get(0).contentHash()
                .equals(second.get(0).contentHash()));
    }

    @Test
    void shouldSplitOversizedSectionAndKeepTokenLimit() {
        String markdown = "# 线程池\n"
                + "线程池用于复用工作线程。".repeat(200);

        List<KnowledgeChunkDraft> drafts = chunker.chunk(
                "并发手册",
                "MARKDOWN",
                markdown
        );

        assertTrue(drafts.size() > 1);
        assertTrue(drafts.stream()
                .allMatch(draft -> draft.tokenCount() <= 80));
        for (int index = 0; index < drafts.size(); index++) {
            assertEquals(index, drafts.get(index).chunkIndex());
        }
    }

    @Test
    void shouldRejectMissingInput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> chunker.chunk(null, "MARKDOWN", "正文")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> chunker.chunk("标题", " ", "正文")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> chunker.chunk("标题", "TEXT", "\n ")
        );
    }

    @Test
    void shouldRejectUnsupportedSourceType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> chunker.chunk("PDF 文档", "PDF", "正文")
        );
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        properties.setChunkOverlapTokens(
                properties.getChunkSizeTokens()
        );

        assertThrows(
                IllegalStateException.class,
                () -> chunker.chunk("标题", "TEXT", "正文")
        );
    }

    @Test
    void shouldStopWhenChunkCountExceedsLimit() {
        properties.setMaxChunksPerDocument(1);
        String markdown = "# Java\nJava 正文。\n# Redis\nRedis 正文。";

        assertThrows(
                IllegalArgumentException.class,
                () -> chunker.chunk("后端手册", "MARKDOWN", markdown)
        );
    }

    @Test
    void shouldReturnImmutableResult() {
        List<KnowledgeChunkDraft> drafts = chunker.chunk(
                "标题",
                "TEXT",
                "正文"
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> drafts.clear()
        );
    }
}
