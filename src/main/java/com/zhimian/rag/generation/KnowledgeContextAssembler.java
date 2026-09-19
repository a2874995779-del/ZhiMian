package com.zhimian.rag.generation;

import com.zhimian.config.RagProperties;
import com.zhimian.rag.retrieval.KnowledgeSearchHit;
import com.zhimian.rag.support.TokenWindowSplitter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KnowledgeContextAssembler {
    private final TokenWindowSplitter tokenCounter;
    private final RagProperties ragProperties;

    public AssembledKnowledgeContext assemble(List<KnowledgeSearchHit> hits){
        if(hits == null || hits.isEmpty()){
            return new AssembledKnowledgeContext("",0,List.of());
        }

        int maxTokens = ragProperties.getMaxContextTokens();
        int maxCitations = ragProperties.getMaxCitations();
        if(maxTokens <= 0 || maxCitations <= 0){
            throw new IllegalStateException("RAG上下文配置必须大于0");
        }

        StringBuilder context = new StringBuilder();
        List<KnowledgeSearchHit> included = new ArrayList<>();
        int usedTokens = 0;

        for (KnowledgeSearchHit hit : hits){
            if(included.size() >= maxCitations){
                break;
            }
            if(hit == null || !StringUtils.hasText(hit.content())){
                continue;
            }

            int sourceIndex = included.size() +1;
            String block = formatBlock(sourceIndex,hit);
            int blockTokens = tokenCounter.countTokens(block);

            if(usedTokens + blockTokens > maxTokens){
                break;
            }

            if(!context.isEmpty()){
                context.append("\n\n");
            }
            context.append(block);
            included.add(hit);
            usedTokens += blockTokens;
        }

        return new AssembledKnowledgeContext(
                context.toString(),
                usedTokens,
                included
        );
    }

    private String formatBlock(int sourceIndex, KnowledgeSearchHit hit) {
        String heading = StringUtils.hasText(hit.headingPath())
                ? hit.headingPath().strip()
                : "未命名章节";
        return """
                     [资料%d]
                     文档：%s
                     章节：%s
                     内容：
                     %s
               """.formatted(
                sourceIndex,
                hit.documentTitle(),
                heading,
                hit.content().strip()
        ).strip();
    }
}
