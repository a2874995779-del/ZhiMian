package com.zhimian.rag.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KnowledgeTextCleaner {
    private static final char BOM='\uFEFF';
    private static final int MAX_CONSECUTIVE_BLANK_LINES = 2;

    public String clean(String content){
        if(!StringUtils.hasText(content)){
            return "";
        }

        String normalized = content
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        if(!normalized.isEmpty() && normalized.charAt(0) == BOM){
            normalized = normalized.substring(1);
        }

        String[] lines = normalized.split("\n",-1);
        StringBuilder result = new StringBuilder(normalized.length());

        boolean inCodeFence = false;
        String fenceMarker = null;
        int consecutiveBlankLines = 0;

        for(String line : lines){
            String leftTrimmed = line.stripLeading();

            if(inCodeFence){
                appendLine(result,line);
                //判断当前行是不是结束围栏：
                if(isClosingFence(leftTrimmed, fenceMarker)){
                    inCodeFence = false;
                    fenceMarker = null;
                }
                continue;
            }
            String openingFence = findFenceMarker(leftTrimmed);
            if(openingFence != null){
                appendLine(result,line.stripTrailing());
                inCodeFence = true;
                fenceMarker = openingFence;
                consecutiveBlankLines = 0;
                continue;
            }

            String cleanedLine = line.stripTrailing();
            if(cleanedLine.isBlank()){
                consecutiveBlankLines++;
                if(consecutiveBlankLines > MAX_CONSECUTIVE_BLANK_LINES){
                    continue;
                }
            }
            else {
                consecutiveBlankLines = 0;
            }
            appendLine(result,cleanedLine);
        }
        return result.toString().strip();
    }

    private String findFenceMarker(String line){
        if(line.isEmpty() || (line.charAt(0) != '`' && line.charAt(0) != '~')){
            return null;
        }

        char marker = line.charAt(0);
        int length = 0;
        while (length < line.length() && line.charAt(length) == marker){
            length++;
        }
        return length >= 3 ? line.substring(0,length) : null;
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

    private void appendLine(StringBuilder builder ,String line){
        if(!builder.isEmpty()){
            builder.append('\n');
        }
        builder.append(line);
    }
}
