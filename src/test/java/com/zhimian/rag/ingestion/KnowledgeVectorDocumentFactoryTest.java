package com.zhimian.rag.ingestion;

import com.zhimian.model.entity.KnowledgeChunk;
import com.zhimian.model.entity.KnowledgeDocument;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeVectorDocumentFactoryTest {

    private final KnowledgeVectorDocumentFactory factory =
            new KnowledgeVectorDocumentFactory();

    @Test
    void createsStableVectorDocumentWithSearchMetadata() {
        KnowledgeDocument source = source(10L, "Java 并发手册");
        KnowledgeChunk chunk = chunk(21L, "Java > 线程池", "AbortPolicy 会拒绝任务。");

        Document result = factory.create(source, chunk);

        assertThat(result.getId()).isEqualTo("knowledge-chunk:21");
        assertThat(result.getText())
                .isEqualTo("知识路径：Java > 线程池\n\nAbortPolicy 会拒绝任务。");
        assertThat(result.getMetadata())
                .containsEntry("documentId", "10")
                .containsEntry("chunkId", "21")
                .containsEntry("chunkIndex", 3)
                .containsEntry("headingPath", "Java > 线程池")
                .containsEntry("documentTitle", "Java 并发手册")
                .containsEntry("sourceType", "MARKDOWN");
    }

    @Test
    void fallsBackToDocumentTitleWhenHeadingPathIsBlank() {
        KnowledgeDocument source = source(10L, "Redis 手册");
        KnowledgeChunk chunk = chunk(22L, "  ", "HNSW 用于近似搜索。");

        Document result = factory.create(source, chunk);

        assertThat(result.getText())
                .startsWith("知识路径：Redis 手册\n\n");
        assertThat(result.getMetadata())
                .containsEntry("headingPath", "Redis 手册");
    }

    @Test
    void rejectsMissingEntityOrDatabaseId() {
        KnowledgeDocument source = source(10L, "标题");
        KnowledgeChunk chunk = chunk(21L, "章节", "正文");

        assertThatThrownBy(() -> factory.create(null, chunk))
                .isInstanceOf(IllegalArgumentException.class);

        source.setId(null);
        assertThatThrownBy(() -> factory.create(source, chunk))
                .isInstanceOf(IllegalArgumentException.class);

        source.setId(10L);
        chunk.setId(null);
        assertThatThrownBy(() -> factory.create(source, chunk))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vectorIdIsDeterministicAndRejectsNull() {
        assertThat(factory.vectorId(99L)).isEqualTo("knowledge-chunk:99");
        assertThat(factory.vectorId(99L)).isEqualTo(factory.vectorId(99L));
        assertThatThrownBy(() -> factory.vectorId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private KnowledgeDocument source(Long id, String title) {
        KnowledgeDocument source = new KnowledgeDocument();
        source.setId(id);
        source.setTitle(title);
        source.setSourceType("MARKDOWN");
        return source;
    }

    private KnowledgeChunk chunk(Long id, String headingPath, String content) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setChunkIndex(3);
        chunk.setHeadingPath(headingPath);
        chunk.setContent(content);
        return chunk;
    }
}
