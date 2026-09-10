package com.zhimian.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RagInfrastructureIntegrationTest.TestApplication.class)
@EnabledIfEnvironmentVariable(named = "RUN_RAG_INTEGRATION_TESTS", matches = "true")
class RagInfrastructureIntegrationTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorStore vectorStore;

    @Test
    void embeddingApiShouldReturnBgeM3Vector() {
        float[] vector = embeddingModel.embed("Java线程池有哪些拒绝策略？");

        assertThat(vector).hasSize(1024);
    }

    @Test
    void redisVectorStoreShouldReturnSemanticallyRelatedDocument() {
        assertThat(vectorStore).isInstanceOf(RedisVectorStore.class);

        List<Document> documents = List.of(
                new Document("CallerRunsPolicy会让提交任务的线程自己执行任务。"),
                new Document("AbortPolicy会直接抛出RejectedExecutionException异常。"),
                new Document("Spring事务可以通过@Transactional注解声明。")
        );
        try {
            vectorStore.add(documents);

            List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                    .query("线程池拒绝任务时由调用者执行是哪一种策略？")
                    .topK(1)
                    .similarityThreshold(0.50)
                    .build());

            assertThat(results)
                    .isNotEmpty()
                    .first()
                    .extracting(Document::getText)
                    .asString()
                    .contains("CallerRunsPolicy");
        }
        finally {
            vectorStore.delete(documents.stream().map(Document::getId).toList());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
