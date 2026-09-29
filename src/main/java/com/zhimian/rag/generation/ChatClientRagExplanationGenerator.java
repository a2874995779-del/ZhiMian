package com.zhimian.rag.generation;

import com.zhimian.model.entity.Question;
import com.zhimian.ai.AiChatClientFactory;
import com.zhimian.common.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ChatClientRagExplanationGenerator implements RagExplanationGenerator{
    private final AiChatClientFactory aiChatClientFactory;
    private final RagExplanationPromptBuilder promptBuilder;

    @Override
    public RagExplanationOutput generate(Question question, String knowledgeContext) {
        RagExplanationOutput output = aiChatClientFactory.forUser(UserContext.getUserId())
                .prompt()
                .system(promptBuilder.systemPrompt())
                .user(promptBuilder.userPrompt(
                        question,
                        knowledgeContext
                ))
                .call()
                .entity(RagExplanationOutput.class);

        if(output == null
                  || !StringUtils.hasText(output.summary())
                  || !StringUtils.hasText(output.reviewAdvice())){
            throw new IllegalStateException("AI 返回的错题讲解不完整");
        }
        return output;
    }
}
