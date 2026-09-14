package com.zhimian.rag.ingestion;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class KnowledgeIngestionLauncherTest {

    private final Executor executor = mock(Executor.class);
    private final KnowledgeIngestionWorker worker =
            mock(KnowledgeIngestionWorker.class);
    private final KnowledgeIngestionLauncher launcher =
            new KnowledgeIngestionLauncher(executor, worker);

    @Test
    void submitsDocumentIdToWorker() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        boolean accepted = launcher.submit(10L);

        assertThat(accepted).isTrue();
        verify(worker).process(10L);
    }

    @Test
    void returnsFalseWhenExecutorQueueRejectsTask() {
        doThrow(new RejectedExecutionException("queue full"))
                .when(executor).execute(any(Runnable.class));

        boolean accepted = launcher.submit(10L);

        assertThat(accepted).isFalse();
        verify(worker, never()).process(10L);
    }
}
