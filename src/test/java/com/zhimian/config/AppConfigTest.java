package com.zhimian.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTest {

    @Test
    void createsBoundedRagExecutorFromProperties() throws InterruptedException {
        RagProperties properties = new RagProperties();
        properties.setIngestionCorePoolSize(1);
        properties.setIngestionMaxPoolSize(2);
        properties.setIngestionQueueCapacity(3);

        Executor executor = new AppConfig().ragIngestionExecutor(properties);
        ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);

        try {
            pool.execute(() -> {
                threadName.set(Thread.currentThread().getName());
                completed.countDown();
            });

            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(pool.getCorePoolSize()).isEqualTo(1);
            assertThat(pool.getMaximumPoolSize()).isEqualTo(2);
            assertThat(pool.getQueue().remainingCapacity()).isEqualTo(3);
            assertThat(pool.getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
            assertThat(threadName.get()).startsWith("rag-ingestion-");
        }
        finally {
            pool.shutdownNow();
        }
    }
}
