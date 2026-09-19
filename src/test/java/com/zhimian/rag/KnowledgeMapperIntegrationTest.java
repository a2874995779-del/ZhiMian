package com.zhimian.rag;

import com.zhimian.mapper.KnowledgeChunkMapper;
import com.zhimian.mapper.KnowledgeDocumentMapper;
import com.zhimian.model.entity.KnowledgeChunk;
import com.zhimian.model.entity.KnowledgeDocument;
import com.zhimian.model.enums.KnowledgeChunkVectorStatus;
import com.zhimian.model.enums.KnowledgeDocumentStatus;
import com.zhimian.rag.retrieval.KnowledgeSearchRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = KnowledgeMapperIntegrationTest.TestApplication.class,
        properties = "spring.ai.vectorstore.redis.initialize-schema=false")
@EnabledIfEnvironmentVariable(named = "RUN_RAG_DATABASE_TESTS", matches = "true")
@Transactional
class KnowledgeMapperIntegrationTest {

    @Autowired
    private KnowledgeDocumentMapper documentMapper;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Test
    void documentMapperPersistsFieldsAndEnforcesStateTransitions() {
        KnowledgeDocument document = newDocument();

        assertThat(documentMapper.insert(document)).isEqualTo(1);
        assertThat(document.getId()).isNotNull();

        KnowledgeDocument saved = documentMapper.selectById(document.getId());
        assertThat(saved.getTitle()).isEqualTo(document.getTitle());
        assertThat(saved.getOriginalFilename()).isEqualTo(document.getOriginalFilename());
        assertThat(saved.getSourceType()).isEqualTo("MARKDOWN");
        assertThat(saved.getContent()).isEqualTo(document.getContent());
        assertThat(saved.getCreatedBy()).isEqualTo(1L);

        assertThat(documentMapper.markProcessing(
                document.getId(), "BAAI/bge-m3", 1024
        )).isEqualTo(1);
        assertThat(documentMapper.markProcessing(
                document.getId(), "BAAI/bge-m3", 1024
        )).isZero();
        assertThat(documentMapper.markCompleted(document.getId(), 2)).isEqualTo(1);
        assertThat(documentMapper.markDeleting(document.getId())).isEqualTo(1);
        assertThat(documentMapper.softDelete(document.getId())).isEqualTo(1);
        assertThat(documentMapper.selectById(document.getId())).isNull();
    }

    @Test
    void activeContentHashIsUniqueButCanBeReusedAfterSoftDelete() {
        KnowledgeDocument first = newDocument();
        assertThat(documentMapper.insert(first)).isEqualTo(1);

        KnowledgeDocument duplicate = newDocument();
        duplicate.setContentHash(first.getContentHash());
        assertThatThrownBy(() -> documentMapper.insert(duplicate))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(documentMapper.markDeleting(first.getId())).isEqualTo(1);
        assertThat(documentMapper.softDelete(first.getId())).isEqualTo(1);
        assertThat(documentMapper.insert(duplicate)).isEqualTo(1);
    }

    @Test
    void chunkMapperBatchInsertQueriesInOrderAndUpdatesStatuses() {
        KnowledgeDocument document = newDocument();
        assertThat(documentMapper.insert(document)).isEqualTo(1);

        KnowledgeChunk first = chunk(document.getId(), 0, "线程池参数");
        KnowledgeChunk second = chunk(document.getId(), 1, "线程池拒绝策略");
        assertThat(chunkMapper.batchInsert(List.of(first, second))).isEqualTo(2);

        List<KnowledgeChunk> saved = chunkMapper.selectByDocumentId(document.getId());
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(KnowledgeChunk::getChunkIndex).containsExactly(0, 1);
        assertThat(saved).extracting(KnowledgeChunk::getContent)
                .containsExactly("线程池参数", "线程池拒绝策略");

        assertThat(chunkMapper.markIndexed(List.of(saved.get(0).getId()))).isEqualTo(1);
        assertThat(chunkMapper.markFailed(saved.get(1).getId(), "测试失败")).isEqualTo(1);

        List<KnowledgeChunk> updated = chunkMapper.selectByDocumentId(document.getId());
        assertThat(updated.get(0).getVectorStatus())
                .isEqualTo(KnowledgeChunkVectorStatus.INDEXED.getCode());
        assertThat(updated.get(1).getVectorStatus())
                .isEqualTo(KnowledgeChunkVectorStatus.FAILED.getCode());
        assertThat(updated.get(1).getErrorMessage()).isEqualTo("测试失败");

        assertThat(chunkMapper.markAllFailedByDocumentId(
                document.getId(), "批量向量化失败"
        )).isEqualTo(2);
        assertThat(chunkMapper.selectByDocumentId(document.getId()))
                .allSatisfy(chunk -> {
                    assertThat(chunk.getVectorStatus())
                            .isEqualTo(KnowledgeChunkVectorStatus.FAILED.getCode());
                    assertThat(chunk.getErrorMessage()).isEqualTo("批量向量化失败");
                });

        assertThat(chunkMapper.softDeleteByDocumentId(document.getId())).isEqualTo(2);
        assertThat(chunkMapper.selectByDocumentId(document.getId())).isEmpty();
    }

    @Test
    void searchableChunkQueryUsesMysqlAsTheSourceOfTruth() {
        KnowledgeDocument completed = newDocument();
        assertThat(documentMapper.insert(completed)).isEqualTo(1);
        assertThat(documentMapper.markProcessing(
                completed.getId(), "BAAI/bge-m3", 1024)).isEqualTo(1);

        KnowledgeChunk indexed = chunk(completed.getId(), 0, "有效知识片段");
        KnowledgeChunk pending = chunk(completed.getId(), 1, "尚未向量化片段");
        assertThat(chunkMapper.batchInsert(List.of(indexed, pending))).isEqualTo(2);
        List<KnowledgeChunk> completedChunks =
                chunkMapper.selectByDocumentId(completed.getId());
        assertThat(chunkMapper.markIndexed(
                List.of(completedChunks.get(0).getId()))).isEqualTo(1);
        assertThat(documentMapper.markCompleted(completed.getId(), 2)).isEqualTo(1);

        KnowledgeDocument unfinished = newDocument();
        assertThat(documentMapper.insert(unfinished)).isEqualTo(1);
        KnowledgeChunk unfinishedChunk =
                chunk(unfinished.getId(), 0, "文档状态未完成");
        assertThat(chunkMapper.batchInsert(List.of(unfinishedChunk))).isEqualTo(1);
        Long unfinishedChunkId = chunkMapper
                .selectByDocumentId(unfinished.getId()).get(0).getId();
        assertThat(chunkMapper.markIndexed(
                List.of(unfinishedChunkId))).isEqualTo(1);

        KnowledgeDocument deleted = newDocument();
        assertThat(documentMapper.insert(deleted)).isEqualTo(1);
        assertThat(documentMapper.markProcessing(
                deleted.getId(), "BAAI/bge-m3", 1024)).isEqualTo(1);
        KnowledgeChunk deletedChunk =
                chunk(deleted.getId(), 0, "已经删除的片段");
        assertThat(chunkMapper.batchInsert(List.of(deletedChunk))).isEqualTo(1);
        Long deletedChunkId = chunkMapper
                .selectByDocumentId(deleted.getId()).get(0).getId();
        assertThat(chunkMapper.markIndexed(List.of(deletedChunkId))).isEqualTo(1);
        assertThat(documentMapper.markCompleted(deleted.getId(), 1)).isEqualTo(1);
        assertThat(chunkMapper.softDeleteByDocumentId(deleted.getId())).isEqualTo(1);

        List<KnowledgeSearchRow> result =
                chunkMapper.selectSearchableByIds(List.of(
                        completedChunks.get(0).getId(),
                        completedChunks.get(1).getId(),
                        unfinishedChunkId,
                        deletedChunkId
                ));

        assertThat(result)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getChunkId())
                            .isEqualTo(completedChunks.get(0).getId());
                    assertThat(row.getDocumentId()).isEqualTo(completed.getId());
                    assertThat(row.getDocumentTitle()).isEqualTo(completed.getTitle());
                    assertThat(row.getSourceType()).isEqualTo("MARKDOWN");
                    assertThat(row.getContent()).isEqualTo("有效知识片段");
                });
    }

    private KnowledgeDocument newDocument() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle("RAG Mapper测试");
        document.setOriginalFilename("mapper-test.md");
        document.setSourceType("MARKDOWN");
        document.setContent("# Mapper测试\n" + UUID.randomUUID());
        document.setContentHash(UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", ""));
        document.setStatus(KnowledgeDocumentStatus.PENDING.getCode());
        document.setChunkCount(0);
        document.setCreatedBy(1L);
        return document;
    }

    private KnowledgeChunk chunk(Long documentId, int chunkIndex, String content) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setHeadingPath("Java > 线程池");
        chunk.setContent(content);
        chunk.setContentHash(UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", ""));
        chunk.setTokenCount(10);
        chunk.setVectorStatus(KnowledgeChunkVectorStatus.PENDING.getCode());
        return chunk;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = KnowledgeDocumentMapper.class)
    static class TestApplication {
    }
}
