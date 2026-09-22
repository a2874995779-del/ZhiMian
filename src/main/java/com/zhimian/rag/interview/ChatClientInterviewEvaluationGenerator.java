package com.zhimian.rag.interview;

import com.zhimian.rag.retrieval.InterviewRagEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatClientInterviewEvaluationGenerator implements InterviewEvaluationGenerator{

    private final ChatClient chatClient;
    private final InterviewEvaluationPromptBuilder promptBuilder;

    @Override
    public GroundedEvaluationOutput generate(String question, String answer, InterviewRagEvidence evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("面试评价证据不能为空");
        }
        GroundedEvaluationOutput output = chatClient
                .prompt()
                .system(promptBuilder.systemPrompt())
                .user(promptBuilder.userPrompt(question,answer,evidence))
                .call()
                .entity(GroundedEvaluationOutput.class);

        validate(output);
        return output;
    }

    private void validate(GroundedEvaluationOutput output) {
        if(output == null
        || output.score() == null
                ||output.score()<0
                ||output.score() >100
                ||output.evaluation() == null
                ||output.evaluation().isBlank()
                ||output.coveredPoints() == null
                ||output.missingPoints() == null
                ||output.knowledgeSufficient() == null){
            throw new IllegalStateException("面试评价模型返回字段不完整");
        }
    }
}
