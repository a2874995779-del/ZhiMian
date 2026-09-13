package com.zhimian.rag.support;

import com.zhimian.config.RagProperties;
import com.zhimian.model.dto.KnowledgeChunkDraft;
import com.zhimian.util.KnowledgeContentHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MarkdownChunker {
    private static final String MARKDOWN = "MARKDOWN";
    private static final String TEXT = "TEXT";

    private final KnowledgeTextCleaner cleaner;
    private final MarkdownSectionParser sectionParser;
    private final TokenWindowSplitter tokenSplitter;
    private final KnowledgeContentHasher contentHasher;
    private final RagProperties ragProperties;

    public List<KnowledgeChunkDraft> chunk(String documentTitle,String sourceType,String content){
        validateInput(documentTitle,sourceType,content);
        validateConfiguration();

        String cleaned = cleaner.clean(content);
        if(!StringUtils.hasText(cleaned)){
            return List.of();
        }

        String normalizedType = sourceType.strip()
                .toUpperCase(Locale.ROOT);
        List<MarkdownSection> sections = buildSections(
                documentTitle.strip(),normalizedType,cleaned);

        List<KnowledgeChunkDraft> drafts = new ArrayList<>();
        int chunkIndex = 0;

        for(MarkdownSection section : sections){
            List<String> windows = tokenSplitter.split(
                    section.content(),
                    ragProperties.getChunkSizeTokens(),
                    ragProperties.getChunkOverlapTokens()
            );

            for(String window : windows){
                if(drafts.size() >= ragProperties.getMaxChunksPerDocument()){
                    throw new IllegalArgumentException("文档切片数量超过系统上限");
                }

                String identityText = section.headingPath()
                        +"\n\n"
                        +window;

                drafts.add(new KnowledgeChunkDraft(
                        chunkIndex++,
                        section.headingPath(),
                        window,
                        contentHasher.sha256(identityText),
                        tokenSplitter.countTokens(window)
                ));
            }
        }
        return List.copyOf(drafts);
    }

    private void validateConfiguration() {
        int chunkSize = ragProperties.getChunkSizeTokens();
        int overlap = ragProperties.getChunkOverlapTokens();
        int maxChunks = ragProperties.getMaxChunksPerDocument();

        if (chunkSize <= 0) {
            throw new IllegalStateException(
                    "RAG chunkSizeTokens 配置必须大于 0");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalStateException(
                    "RAG chunkOverlapTokens 必须大于等于 0 且小于 chunkSizeTokens");
        }
        if (maxChunks <= 0) {
            throw new IllegalStateException(
                    "RAG maxChunksPerDocument 配置必须大于 0");
        }
    }

    private void validateInput(String documentTitle, String sourceType, String content) {
        if (!StringUtils.hasText(documentTitle)) {
            throw new IllegalArgumentException("文档标题不能为空");
        }
        if (!StringUtils.hasText(sourceType)) {
            throw new IllegalArgumentException("文档类型不能为空");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
    }

    private List<MarkdownSection> buildSections(String documentTitle,String sourceType,String cleaned){
        if(MARKDOWN.equals(sourceType)){
            return sectionParser.parse(cleaned,documentTitle);
        }
        if(TEXT.equals(sourceType)){
            return List.of(new MarkdownSection(
                    documentTitle,cleaned));
        }
        throw new IllegalArgumentException("当前只支持MARKDOWN和TEXT文档");
    }
}
