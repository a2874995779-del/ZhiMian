package com.zhimian.rag.support;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
/*
负责把一段较长文本切成多个 Token 窗口。
 */
@Component
public class TokenWindowSplitter {
    private final Encoding encoding = Encodings
            .newLazyEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    public int countTokens(String text){
        if(text == null){
            return 0;
        }
        return encoding.countTokens(text);
    }

    public List<String> split(String text,int maxTokens,int overlapTokens){
        validateWindow(maxTokens,overlapTokens);
        if(!StringUtils.hasText(text)){
            return List.of();
        }

        String normalized = text.strip();
        IntArrayList tokenIds = encoding.encode(normalized);

        if(tokenIds.size() <= maxTokens){
            return List.of(normalized);
        }

        boolean[] safeBoundaries = findSafeBoundaries(tokenIds);
        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < tokenIds.size()) {
            int proposedEnd = Math.min(start + maxTokens, tokenIds.size());
            int end = findSafeEnd(safeBoundaries, start, proposedEnd);
            IntArrayList window = copyRange(tokenIds,start,end);
            String chunk = encoding.decode(window).strip();

            if(StringUtils.hasText(chunk)){
                chunks.add(chunk);
            }
            if(end == tokenIds.size()){
                break;
            }

            int nextStart = end - overlapTokens;
            while (nextStart < end && !safeBoundaries[nextStart]) {
                nextStart++;
            }
            start = nextStart > start ? nextStart : end;
        }
        return List.copyOf(chunks);
    }

    private boolean[] findSafeBoundaries(IntArrayList tokenIds) {
        boolean[] safeBoundaries = new boolean[tokenIds.size() + 1];
        safeBoundaries[0] = true;
        safeBoundaries[tokenIds.size()] = true;

        for (int index = 1; index < tokenIds.size(); index++) {
            IntArrayList token = new IntArrayList(1);
            token.add(tokenIds.get(index));
            byte[] bytes = encoding.decodeBytes(token);
            safeBoundaries[index] = bytes.length == 0
                    || !isUtf8ContinuationByte(bytes[0]);
        }
        return safeBoundaries;
    }

    private int findSafeEnd(boolean[] safeBoundaries,
                            int start,
                            int proposedEnd) {
        int end = proposedEnd;
        while (end > start && !safeBoundaries[end]) {
            end--;
        }
        if (end == start) {
            throw new IllegalArgumentException(
                    "maxTokens过小，无法容纳一个完整字符");
        }
        return end;
    }

    private boolean isUtf8ContinuationByte(byte value) {
        return (value & 0xC0) == 0x80;
    }

    private IntArrayList copyRange(IntArrayList source, int start,int end) {
        IntArrayList result = new IntArrayList(end - start);
        for (int index = start; index < end; index++) {
            result.add(source.get(index));
        }
        return result;
    }

    private void validateWindow(int maxTokens,int overlapTokens){
        if(maxTokens <= 0){
            throw new IllegalArgumentException("maxTokens必须大于0");
        }
        if(overlapTokens <0 || overlapTokens >= maxTokens){
            throw new IllegalArgumentException("overlapTokens必须大于等于0且小于maxTokens");
        }
    }
}
