package com.zhimian.rag.interview;

import com.zhimian.common.ErrorCode;
import com.zhimian.config.RagProperties;
import com.zhimian.exception.BusinessException;
import com.zhimian.model.dto.InterviewAnswerEvaluation;
import com.zhimian.model.interview.InterviewPlanItem;
import com.zhimian.rag.retrieval.InterviewRagEvidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewAnswerEvaluationService {
    private static final int MAX_ATTEMPTS = 2;
    private final InterviewRagEvidenceProvider evidenceProvider;
    private final InterviewEvaluationGenerator generator;
    private final ExecutorService interviewEvaluationExecutor;
    private final RagProperties ragProperties;

    public InterviewEvaluationResult evaluate(String question, String answer, InterviewPlanItem item){
        CompletableFuture<InterviewEvaluationResult> future = null;
        try {
            future = CompletableFuture.supplyAsync(
                    () -> evaluateInternal(question, answer, item),
                    interviewEvaluationExecutor
            );
            return future.get(
                    resolveTimeoutSeconds(),
                    TimeUnit.SECONDS
            );
        } catch (TimeoutException exception) {
            cancelQuietly(future);
            throw new BusinessException(
                    ErrorCode.AI_SERVICE_ERROR,
                    "单题评分超时"
            );
        } catch (InterruptedException exception) {
            cancelQuietly(future);
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    ErrorCode.AI_SERVICE_ERROR,
                    "单题评分被中断"
            );
        } catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.AI_SERVICE_ERROR,
                    "单题评分失败"
            );
        }
    }

    private void cancelQuietly(CompletableFuture<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private InterviewEvaluationResult evaluateInternal(
            String question,
            String answer,
            InterviewPlanItem item
    ) {
        InterviewRagEvidence evidence = evidenceProvider.load(question,item);

        for(int attempt =1;attempt <= MAX_ATTEMPTS;attempt++){
            try {
                GroundedEvaluationOutput output = generator.generate(
                        question,
                        answer,
                        evidence
                );
                return new InterviewEvaluationResult(
                        toLegacy(output),
                        evidence,
                        output
                );
            }catch (RuntimeException exception){
                log.warn("面试单题增强评价第{}次失败",attempt,exception);
            }
        }
        throw new BusinessException(
                ErrorCode.AI_SERVICE_ERROR,"单题评分失败"
        );
    }

    private int resolveTimeoutSeconds() {
        int timeoutSeconds = ragProperties.getEvaluationTimeoutSeconds();
        if (timeoutSeconds <= 0) {
            throw new IllegalStateException("面试评价超时时间必须大于0");
        }
        return timeoutSeconds;
    }

    private InterviewAnswerEvaluation toLegacy(GroundedEvaluationOutput output) {
        StringBuilder evaluation = new StringBuilder(output.evaluation().strip());
        if(!output.coveredPoints().isEmpty()){
            evaluation.append("已覆盖:")
                    .append(String.join("、", output.coveredPoints()))
                    .append("。");
        }
        if (!output.missingPoints().isEmpty()) {
            evaluation.append(" 建议补充:")
                    .append(String.join("、", output.missingPoints()))
                    .append("。");
        }
        return new InterviewAnswerEvaluation(
                output.score(),
                evaluation.toString()
        );
    }
}
