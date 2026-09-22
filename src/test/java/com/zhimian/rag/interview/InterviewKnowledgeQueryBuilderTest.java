package com.zhimian.rag.interview;

import com.zhimian.model.interview.InterviewPlanItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewKnowledgeQueryBuilderTest {

    private final InterviewKnowledgeQueryBuilder builder =
            new InterviewKnowledgeQueryBuilder();

    @Test
    void combinesQuestionAndPlanMetadata() {
        InterviewPlanItem item = item();

        String query = builder.build(
                "  如果订单表快速增长，如何设计索引？  ",
                item
        );

        assertThat(query)
                .isEqualTo("MySQL；索引和查询；工程实践；如果订单表快速增长，如何设计索引？");
    }

    @Test
    void doesNotNeedPlanMetadata() {
        assertThat(builder.build("什么是索引？", null))
                .isEqualTo("什么是索引？");
    }

    @Test
    void rejectsBlankQuestion() {
        assertThatThrownBy(() -> builder.build("  ", item()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("面试题目不能为空");
    }

    @Test
    void limitsQueryLength() {
        String query = builder.build("x".repeat(600), item());

        assertThat(query).hasSize(500);
    }

    private InterviewPlanItem item() {
        return new InterviewPlanItem(
                1,
                "mysql",
                "MySQL",
                "索引和查询",
                "工程实践",
                2,
                "原题",
                1
        );
    }
}
