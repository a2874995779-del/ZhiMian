package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.model.enums.RagExplanationMode;
import com.zhimian.model.vo.WrongQuestionExplanationVO;
import com.zhimian.service.WrongQuestionExplanationService;
import com.zhimian.service.WrongQuestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WrongQuestionControllerTest {

    @Mock
    private WrongQuestionService wrongQuestionService;

    @Mock
    private WrongQuestionExplanationService explanationService;

    @InjectMocks
    private WrongQuestionController controller;

    @Test
    void delegatesWrongQuestionExplanation() {
        WrongQuestionExplanationVO explanation =
                new WrongQuestionExplanationVO(
                        12L,
                        "什么是缓存击穿？",
                        "缓存击穿的核心是热点 Key 失效。",
                        List.of("热点 Key"),
                        List.of("不要与缓存穿透混淆"),
                        "对比复习三类缓存问题。",
                        RagExplanationMode.RAG,
                        true,
                        true,
                        List.of(),
                        List.of()
                );
        when(explanationService.explain(12L))
                .thenReturn(explanation);

        Result<WrongQuestionExplanationVO> result =
                controller.explain(12L);

        assertThat(result.getData()).isSameAs(explanation);
        verify(explanationService).explain(12L);
    }
}
