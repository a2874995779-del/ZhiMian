package com.zhimian.rag.interview;

import com.zhimian.rag.retrieval.InterviewRagEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatClientInterviewEvaluationGeneratorTest {

    private ChatClient chatClient;
    private InterviewEvaluationPromptBuilder promptBuilder;
    private ChatClientInterviewEvaluationGenerator generator;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        promptBuilder = mock(InterviewEvaluationPromptBuilder.class);
        generator = new ChatClientInterviewEvaluationGenerator(
                chatClient, promptBuilder);
        when(promptBuilder.systemPrompt()).thenReturn("system");
        when(promptBuilder.userPrompt(anyString(), anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn("user");
    }

    @Test
    void mapsValidModelOutput() {
        GroundedEvaluationOutput output = validOutput();
        when(chatClient.prompt()
                .system("system")
                .user("user")
                .call()
                .entity(GroundedEvaluationOutput.class))
                .thenReturn(output);

        assertThat(generator.generate("问题", "回答", evidence()))
                .isSameAs(output);
    }

    @Test
    void rejectsIncompleteModelOutput() {
        GroundedEvaluationOutput output = new GroundedEvaluationOutput(
                101, "评价", List.of(), List.of(), false);
        when(chatClient.prompt()
                .system("system")
                .user("user")
                .call()
                .entity(GroundedEvaluationOutput.class))
                .thenReturn(output);

        assertThatThrownBy(() -> generator.generate(
                "问题", "回答", evidence()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("面试评价模型返回字段不完整");
    }

    @Test
    void rejectsMissingEvidence() {
        assertThatThrownBy(() -> generator.generate("问题", "回答", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("面试评价证据不能为空");
    }

    private GroundedEvaluationOutput validOutput() {
        return new GroundedEvaluationOutput(
                82,
                "回答基本正确",
                List.of("索引选择"),
                List.of("回表查询"),
                true
        );
    }

    private InterviewRagEvidence evidence() {
        return InterviewRagEvidence.degraded(
                "问题", InterviewRagEvidenceStatus.NO_RELEVANT_KNOWLEDGE);
    }
}
