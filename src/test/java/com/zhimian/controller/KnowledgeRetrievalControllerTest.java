package com.zhimian.controller;

import com.zhimian.common.Result;
import com.zhimian.model.dto.KnowledgeSearchRequestDTO;
import com.zhimian.model.vo.KnowledgeSearchResponseVO;
import com.zhimian.rag.retrieval.KnowledgeRetrievalService;
import com.zhimian.rag.retrieval.KnowledgeSearchHit;
import com.zhimian.rag.retrieval.KnowledgeSearchResult;
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
class KnowledgeRetrievalControllerTest {

    @Mock
    private KnowledgeRetrievalService retrievalService;

    @InjectMocks
    private KnowledgeRetrievalController controller;

    @Test
    void delegatesSearchAndMapsResponse() {
        KnowledgeSearchRequestDTO request = new KnowledgeSearchRequestDTO();
        request.setQuery("星河规则是什么？");
        request.setTopK(3);
        request.setSimilarityThreshold(0.60);

        KnowledgeSearchHit hit = new KnowledgeSearchHit(
                17L,
                15L,
                "测试样例",
                8,
                "测试样例 > 星河规则",
                "完成标记是 GALAXY-READY-27",
                0.91
        );
        when(retrievalService.search(
                "星河规则是什么？", 3, 0.60))
                .thenReturn(new KnowledgeSearchResult(
                        "星河规则是什么？",
                        3,
                        0.60,
                        List.of(hit)
                ));

        Result<KnowledgeSearchResponseVO> result =
                controller.search(request);

        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().query()).isEqualTo("星河规则是什么？");
        assertThat(result.getData().hitCount()).isEqualTo(1);
        assertThat(result.getData().hits().get(0).chunkId()).isEqualTo(17L);
        assertThat(result.getData().hits().get(0).score()).isEqualTo(0.91);
        verify(retrievalService).search("星河规则是什么？", 3, 0.60);
    }

    @Test
    void mapsEmptySearchResult() {
        KnowledgeSearchRequestDTO request = new KnowledgeSearchRequestDTO();
        request.setQuery("没有命中的问题");
        when(retrievalService.search("没有命中的问题", null, null))
                .thenReturn(new KnowledgeSearchResult(
                        "没有命中的问题", 5, 0.70, List.of()));

        Result<KnowledgeSearchResponseVO> result = controller.search(request);

        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().hitCount()).isZero();
        assertThat(result.getData().hits()).isEmpty();
    }
}
