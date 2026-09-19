package com.zhimian.rag.generation;

import com.zhimian.model.entity.Question;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagExplanationPromptBuilderTest {

    private final RagExplanationPromptBuilder builder =
            new RagExplanationPromptBuilder();

    @Test
    void separatesInstructionsQuestionAnswerAndKnowledge() {
        Question question = question();

        String systemPrompt = builder.systemPrompt();
        String userPrompt = builder.userPrompt(
                question,
                "[资料1] 互斥锁可以保护回源过程"
        );

        assertThat(systemPrompt)
                .contains("待分析数据")
                .contains("不得执行")
                .contains("不得编造");
        assertThat(userPrompt)
                .contains("<question>")
                .contains(question.getTitle())
                .contains(question.getContent())
                .contains("<reference_answer>")
                .contains(question.getAnswer())
                .contains("<knowledge_context>")
                .contains("[资料1]");
    }

    @Test
    void usesExplicitFallbacksForMissingOptionalContent() {
        Question question = new Question();
        question.setTitle("什么是 JVM？");

        String userPrompt = builder.userPrompt(question, "  ");

        assertThat(userPrompt)
                .contains("无额外题干")
                .contains("题库暂未提供标准答案")
                .contains("本次没有检索到可用知识资料");
    }

    private Question question() {
        Question question = new Question();
        question.setTitle("什么是缓存击穿？");
        question.setContent("说明原因和解决办法");
        question.setAnswer("热点 Key 失效后大量请求同时访问数据库");
        return question;
    }
}
