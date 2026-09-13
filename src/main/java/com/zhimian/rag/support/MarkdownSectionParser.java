package com.zhimian.rag.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MarkdownSectionParser {
    private static final Pattern HEADING_PATTERN=
            Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*$");

    public List<MarkdownSection> parse(String markdown,String documentTitle){
        if(!StringUtils.hasText(markdown)){
            return List.of();
        }

        String fallbackPath = StringUtils.hasText(documentTitle)
                ? documentTitle.strip()
                : "正文";

        List<MarkdownSection> sections = new ArrayList<>();
        String[] headingLevels = new String[6];
        String currentPath = fallbackPath;
        StringBuilder currentContent = new StringBuilder();

        boolean inCodeFence = false;
        String fenceMarker = null;

        for(String line : markdown.split("\n",-1)){
            String leftTrimmed = line.stripLeading();

            if(inCodeFence){
                appendLine(currentContent , line);
                if(isClosingFence(leftTrimmed, fenceMarker)){
                    inCodeFence = false;
                    fenceMarker = null;
                }
                continue;
            }
            String openingFence = findFenceMarker(leftTrimmed);
            if(openingFence != null){
                inCodeFence = true;
                fenceMarker = openingFence;
                appendLine(currentContent,line);
                continue;
            }

            Matcher matcher = HEADING_PATTERN.matcher(line);
            if(matcher.matches()){
                addSectionIfPresent(sections,currentPath,currentContent);

                int level = matcher.group(1).length();
                String heading = matcher.group(2)
                        .replaceFirst("\\s+#+\\s*$", "")
                        .strip();
                headingLevels[level - 1] = heading;
                Arrays.fill(headingLevels,level,headingLevels.length,null);

                currentPath = buildHeadingPath(headingLevels,fallbackPath);
                currentContent = new StringBuilder();
                continue;
            }
            appendLine(currentContent,line);
        }

        addSectionIfPresent(sections,currentPath,currentContent);
        return List.copyOf(sections);


    }

    private String findFenceMarker(String line) {
        if(line.isEmpty() || (line.charAt(0) != '`' && line.charAt(0) != '~')){
            return null;
    }
        char marker = line.charAt(0);
        int length = 0;
        while (length < line.length() && line.charAt(length) == marker){
            length++;
        }
        return length >= 3 ? line.substring(0,length) : null ;
    }

    private boolean isClosingFence(String line, String openingFence) {
        if (line.isEmpty() || line.charAt(0) != openingFence.charAt(0)) {
            return false;
        }

        char marker = line.charAt(0);
        int length = 0;
        while (length < line.length() && line.charAt(length) == marker) {
            length++;
        }
        return length >= openingFence.length()
                && line.substring(length).isBlank();
    }

    private String buildHeadingPath(String[] levels, String fallbackPath) {
        List<String> pathParts = Arrays.stream(levels)
                .filter(StringUtils :: hasText)
                .toList();
        return pathParts.isEmpty()
                ? fallbackPath
                : String.join(" > ",pathParts);
    }

    private void addSectionIfPresent(List<MarkdownSection> sections, String headingPath, StringBuilder content) {
        String value = content.toString().strip();
        if(StringUtils.hasText(value)){
            sections.add(new MarkdownSection(headingPath,value));
        }
    }

    private void appendLine(StringBuilder builder, String line) {
        if(!builder.isEmpty()){
            builder.append('\n');
        }
        builder.append(line);
    }
}
