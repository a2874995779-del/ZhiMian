package com.zhimian.rag.interview;

import com.zhimian.config.RagProperties;
import com.zhimian.model.interview.InterviewPlanItem;
import com.zhimian.rag.generation.AssembledKnowledgeContext;
import com.zhimian.rag.generation.KnowledgeContextAssembler;
import com.zhimian.rag.retrieval.InterviewRagEvidence;
import com.zhimian.rag.retrieval.KnowledgeRetrievalService;
import com.zhimian.rag.retrieval.KnowledgeSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewRagEvidenceProvider {
    private final RagProperties ragProperties;
    private final InterviewKnowledgeQueryBuilder queryBuilder;
    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgeContextAssembler contextAssembler;

    public InterviewRagEvidence load(
            String question,
            InterviewPlanItem item
    ){
        String query = queryBuilder.build(question,item);

        if(!ragProperties.isEnabled()){
            return InterviewRagEvidence.degraded(
                    query,
                    InterviewRagEvidenceStatus.RAG_DISABLED
            );
        }

        try {
            KnowledgeSearchResult searchResult = retrievalService.search(
                    query,
                    ragProperties.getTopK(),
                    ragProperties.getSimilarityThreshold()
            );

            if(searchResult.hits().isEmpty()){
                return InterviewRagEvidence.degraded(
                        query,
                        InterviewRagEvidenceStatus.NO_RELEVANT_KNOWLEDGE
                );
            }

            AssembledKnowledgeContext context = contextAssembler.assemble(
                    searchResult.hits()
            );

            if(!context.hasKnowledge()){
                return InterviewRagEvidence.degraded(
                        query,
                        InterviewRagEvidenceStatus.NO_RELEVANT_KNOWLEDGE
                );
            }

            return new InterviewRagEvidence(
                    query,
                    context.promptContext(),
                    context.includedHits(),
                    InterviewRagEvidenceStatus.APPLIED
            );
        } catch (DataAccessException exception) {
            log.warn("面试RAG检索不可用，降级为模型评价:query={}",query,exception);
            return InterviewRagEvidence.degraded(
                    query,
                    InterviewRagEvidenceStatus.RETRIEVAL_UNAVAILABLE
            );
        }
    }
}
