package com.zhimian.rag.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Component
@Slf4j
public class KnowledgeIngestionLauncher {
    private final Executor executor;
    private final KnowledgeIngestionWorker worker;

    public KnowledgeIngestionLauncher(
            @Qualifier("ragIngestionExecutor") Executor executor,
            KnowledgeIngestionWorker worker){
        this.executor = executor;
        this.worker = worker;
    }

    public boolean submit(Long documentId){
        //异步处理
        try {
            executor.execute(() -> worker.process(documentId));
            return true;
        }catch (RejectedExecutionException exception){
            log.warn("RAG任务队列已满: documentId={}", documentId);
            return false;
        }
    }
}
