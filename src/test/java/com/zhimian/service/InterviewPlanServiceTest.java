package com.zhimian.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.model.dto.CreateInterviewDTO;
import com.zhimian.model.entity.InterviewSession;
import com.zhimian.model.interview.InterviewPlan;
import com.zhimian.model.interview.InterviewPlanItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.List;

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
    void ragDirectionUsesRagQuestionPool() {
        CreateInterviewDTO dto = new CreateInterviewDTO();
        dto.setMode("direction");
        dto.setDirection("rag");
        dto.setTargetQuestionCount(8);

        InterviewPlan plan = service.build(dto);

        assertThat(plan.title()).isEqualTo("RAG 检索增强专项面试");
        assertThat(plan.focus()).contains("Embedding", "向量检索");
        assertThat(plan.items()).hasSize(8);
        assertThat(plan.items()).extracting(InterviewPlanItem::moduleCode)
                .allMatch(module -> List.of(
                        "rag_basic", "embedding", "chunk", "ingestion", "vector_store",
                        "retrieval", "hybrid", "prompt", "citation", "evaluation",
                        "consistency", "fallback", "security", "context", "project"
                ).contains(module));
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

    @Test
    void companyScenariosUseDifferentQuestionPools() {
        InterviewPlan meituan = service.build(scenario("meituan_style_backend", 12));
        InterviewPlan tencent = service.build(scenario("tencent_style_backend", 12));
        InterviewPlan xiaohongshu = service.build(scenario("xiaohongshu_style_backend", 12));

        assertThat(meituan.items()).extracting(InterviewPlanItem::questionText)
                .doesNotContainAnyElementsOf(tencent.items().stream()
                        .map(InterviewPlanItem::questionText)
                        .toList());
        assertThat(meituan.items()).extracting(InterviewPlanItem::questionText)
                .doesNotContainAnyElementsOf(xiaohongshu.items().stream()
                        .map(InterviewPlanItem::questionText)
                        .toList());
        assertThat(tencent.items()).extracting(InterviewPlanItem::questionText)
                .doesNotContainAnyElementsOf(xiaohongshu.items().stream()
                        .map(InterviewPlanItem::questionText)
                        .toList());
        assertThat(meituan.focus()).contains("订单", "配送");
        assertThat(tencent.focus()).contains("Java 基础", "内容社交");
        assertThat(xiaohongshu.focus()).contains("内容发布", "Feed 流");
    }

    @Test
    void xiaohongshuScenarioStartsWithProjectAndKeepsInternDifficulty() {
        InterviewPlan plan = service.build(scenario("xiaohongshu_style_backend", 8));

        assertThat(plan.title()).contains("小红书", "实习");
        assertThat(plan.items().get(0).moduleCode()).isEqualTo("project");
        assertThat(plan.items()).allSatisfy(item ->
                assertThat(item.difficulty()).isBetween(1, 3));
        assertThat(plan.items()).extracting(InterviewPlanItem::moduleCode)
                .doesNotHaveDuplicates();
    }

    @Test
    void scenarioStartsWithProjectAndCoversDifferentModules() {
        InterviewPlan plan = service.build(scenario("meituan_style_backend", 8));

        assertThat(plan.items().get(0).moduleCode()).isEqualTo("project");
        assertThat(plan.items()).extracting(InterviewPlanItem::moduleCode)
                .doesNotHaveDuplicates();
    }

    @Test
    void scenarioPlanHasRandomizedSelectionFromLargerPool() {
        List<String> plans = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> service.build(
                        scenario("tencent_style_backend", 8)))
                .map(plan -> plan.items().stream()
                        .map(InterviewPlanItem::questionText)
                        .toList()
                        .toString())
                .distinct()
                .toList();

        assertThat(plans).hasSizeGreaterThan(1);
    }

    @Test
    void springDirectionProvidesTwelveDistinctQuestionsAndCoreCoverage() {
        CreateInterviewDTO dto = new CreateInterviewDTO();
        dto.setDirection("spring");
        dto.setTargetQuestionCount(12);

        InterviewPlan plan = service.build(dto);

        assertThat(plan.title()).contains("Spring");
        assertThat(plan.focus()).contains("IoC", "AOP", "事务", "MVC");
        assertThat(plan.items()).hasSize(12);
        assertThat(plan.items()).extracting(InterviewPlanItem::questionText).doesNotHaveDuplicates();
        assertThat(plan.items()).extracting(InterviewPlanItem::moduleCode)
                .contains("ioc", "aop", "transaction", "spring_boot", "spring_mvc");
        assertThat(plan.items()).allSatisfy(item -> assertThat(item.difficulty()).isBetween(1, 3));
    }

    @Test
    void shortestSpringPlanStillCoversTheFiveCoreAreas() {
        CreateInterviewDTO dto = new CreateInterviewDTO();
        dto.setDirection("spring");
        dto.setTargetQuestionCount(5);

        InterviewPlan plan = service.build(dto);

        assertThat(plan.items()).hasSize(5);
        assertThat(plan.items()).extracting(InterviewPlanItem::moduleCode)
                .containsExactly("ioc", "aop", "transaction", "spring_boot", "spring_mvc");
    }

    @ParameterizedTest
    @CsvSource({
            "alibaba_style_backend,5", "alibaba_style_backend,8", "alibaba_style_backend,12",
            "ant_style_backend,5", "ant_style_backend,8", "ant_style_backend,12",
            "jd_style_backend,5", "jd_style_backend,8", "jd_style_backend,12"
    })
    void newCompanyScenariosCoverCoreSkillsWithinRequestedLength(String code, int count) {
        InterviewPlan plan = service.build(scenario(code, count));

        assertThat(plan.items()).hasSize(count);
        assertThat(plan.items().get(0).moduleCode()).isEqualTo("project");
        assertThat(plan.items()).extracting(InterviewPlanItem::moduleCode)
                .contains("spring", "mysql", "redis").doesNotHaveDuplicates();
        assertThat(plan.items()).extracting(InterviewPlanItem::questionText).doesNotHaveDuplicates();
        assertThat(plan.items()).allSatisfy(item -> {
            assertThat(item.difficulty()).isBetween(1, 3);
            assertThat(item.maxFollowUp()).isBetween(1, 2);
        });
        assertThat(plan.title()).contains("模拟");
        assertThat(plan.items().get(count - 1).roundNo()).isEqualTo(count);
    }

    @ParameterizedTest
    @CsvSource({"alibaba_style_backend,commerce_stock", "ant_style_backend,payment_reliability",
            "jd_style_backend,fulfillment"})
    void companyBusinessAxisIsNotLostInTheShortestPlan(String code, String businessModule) {
        InterviewPlan plan = service.build(scenario(code, 5));
        assertThat(plan.items()).extracting(InterviewPlanItem::moduleCode).contains(businessModule);
    }

    @Test
    void sixCompanyPlansDoNotReuseTheSameQuestionText() {
        List<String> codes = List.of("meituan_style_backend", "tencent_style_backend",
                "xiaohongshu_style_backend", "alibaba_style_backend", "ant_style_backend", "jd_style_backend");
        List<String> questions = codes.stream()
                .flatMap(code -> service.build(scenario(code, 12)).items().stream())
                .map(InterviewPlanItem::questionText).toList();

        assertThat(questions).hasSize(72).doesNotHaveDuplicates();
    }

    @Test
    void storedNewScenarioPlanIsRestoredWithoutChangingItsQuestions() {
        InterviewPlan original = service.build(scenario("ant_style_backend", 12));
        InterviewSession session = new InterviewSession();
        session.setPlanJson(service.serialize(original));

        assertThat(service.load(session)).isEqualTo(original);
    }

    @Test
    void repeatedCompanyPracticeCanStartFromDifferentProjectQuestions() {
        List<String> openingQuestions = java.util.stream.IntStream.range(0, 30)
                .mapToObj(index -> service.build(scenario("alibaba_style_backend", 8))
                        .items().get(0).questionText())
                .distinct().toList();

        assertThat(openingQuestions).hasSizeGreaterThan(1);
    }

    private CreateInterviewDTO scenario(String code, int count) {
        CreateInterviewDTO dto = new CreateInterviewDTO();
        dto.setMode("scenario");
        dto.setScenarioCode(code);
        dto.setTargetQuestionCount(count);
        return dto;
    }
}
