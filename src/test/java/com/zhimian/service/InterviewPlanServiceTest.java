package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.model.dto.CreateInterviewDTO;
import com.zhimian.model.interview.InterviewPlan;
import com.zhimian.model.interview.InterviewPlanItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewPlanServiceTest {
    private InterviewPlanService service;

    @BeforeEach
    void setUp() {
        service = new InterviewPlanService(new ObjectMapper());
    }

    @Test
    void directionPlanContainsDifferentQuestionsForEachRound() {
        CreateInterviewDTO dto = new CreateInterviewDTO();
        dto.setMode("direction");
        dto.setDirection("redis");
        dto.setTargetQuestionCount(8);

        InterviewPlan plan = service.build(dto);

        assertThat(plan.mode()).isEqualTo("direction");
        assertThat(plan.items()).hasSize(8);
        assertThat(plan.items()).extracting(InterviewPlanItem::roundNo)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(plan.items()).extracting(InterviewPlanItem::questionText)
                .doesNotHaveDuplicates();
    }

    @Test
    void scenarioPlanHasScenarioMetadataAndMixedModules() {
        CreateInterviewDTO dto = new CreateInterviewDTO();
        dto.setMode("scenario");
        dto.setScenarioCode("meituan_style_backend");
        dto.setTargetQuestionCount(12);

        InterviewPlan plan = service.build(dto);

        assertThat(plan.mode()).isEqualTo("scenario");
        assertThat(plan.code()).isEqualTo("meituan_style_backend");
        assertThat(plan.title()).contains("模拟");
        assertThat(plan.items()).hasSize(12);
        assertThat(plan.items()).extracting(InterviewPlanItem::moduleCode)
                .isNotEmpty()
                .hasSize(new HashSet<>(plan.items().stream().map(InterviewPlanItem::moduleCode).toList()).size());
    }

    @Test
    void unsupportedScenarioIsRejected() {
        CreateInterviewDTO dto = new CreateInterviewDTO();
        dto.setMode("scenario");
        dto.setScenarioCode("unknown");

        assertThatThrownBy(() -> service.build(dto))
                .hasMessage("暂不支持该综合面试场景");
    }
}
