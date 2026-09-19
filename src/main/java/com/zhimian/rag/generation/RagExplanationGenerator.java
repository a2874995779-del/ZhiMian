package com.zhimian.rag.generation;

import com.zhimian.model.entity.Question;

public interface RagExplanationGenerator {
    RagExplanationOutput generate(
            Question question,
            String knowledgeContext
    );
}
