package com.zhimian.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.mapper.InterviewMessageMapper;
import com.zhimian.model.entity.InterviewMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewContextManager {
    private static final String CTX_KEY_PREFIX = "zhimian:interview:ctx:";
    private static final long CTX_TTL_HOURS = 2;
    // 粗算 token 预算:中文字符数/2 估一个数量级,不追求精确
    private static final int TOKEN_BUDGET = 2000;

    private final StringRedisTemplate redisTemplate;
    private final InterviewMessageMapper interviewMessageMapper;
    private final ObjectMapper objectMapper;

    /**
     * 追加一条消息:写入 Redis List 并续期。
     * 只要这场对话还在继续,TTL 就应该一直被打回 2 小时,不会中途过期。
     */
    public void append(Long sessionId, InterviewMessage message){
        String key = ctxKey(sessionId);
        redisTemplate.opsForList().rightPush(key,serialize(message));
        redisTemplate.expire(key,CTX_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 加载这场会话"要发给模型"的消息列表:Redis 未命中就从 MySQL 重建,
     * 最后统一按 token 预算裁剪(system prompt 永远保留)。
     */

    public List<InterviewMessage> load(Long sessionId){
        String key = ctxKey(sessionId);
        List<String> raw = redisTemplate.opsForList().range(key,0,-1);

        List<InterviewMessage> messages;
        if(raw == null || raw.isEmpty()){
            log.info("会话{}的Redis上下文未命中，从MySQL重建",sessionId);
            messages = interviewMessageMapper.selectBySessionId(sessionId);
            rebuildRedis(sessionId,messages);
        }else {
            messages = raw.stream().map(this::deserialize).toList();
        }
        return trim(messages);
    }


    private void rebuildRedis(Long sessionId, List<InterviewMessage> messages) {
        if(messages.isEmpty()){
            return;
        }
        String key = ctxKey(sessionId);
        for(InterviewMessage m : messages){
            redisTemplate.opsForList().rightPush(key,serialize(m));
        }
        redisTemplate.expire(key,CTX_TTL_HOURS, TimeUnit.HOURS);
    }


    /**
     * 按 token 预算从最新往回裁剪:system prompt 永远保留,
     * 其余消息从最后一条往前累加 token 数,单条超预算就跳过继续看更早的,不整体截断——
     * 不然万一最近一条回答特别长,会把它之前所有本来能放下的历史一次性全部丢光。
     */
    private List<InterviewMessage> trim(List<InterviewMessage> messages){
        InterviewMessage systemMsg = messages.stream()
                .filter(m->"system".equals(m.getRole()))
                .findFirst()
                .orElse(null);
        List<InterviewMessage> others = messages.stream()
                .filter(m->!"system".equals(m.getRole()))
                .toList();
        int used = systemMsg !=null ? estimateTokens(systemMsg.getContent()) : 0 ;
        List<List<InterviewMessage>> units = groupConversationUnits(others);
        LinkedList<List<InterviewMessage>> keptUnits = new LinkedList<>();
        for (int i = units.size() - 1; i >= 0; i--) {
            List<InterviewMessage> unit = units.get(i);
            int tokens = unit.stream().mapToInt(m -> estimateTokens(m.getContent())).sum();
            if (used + tokens > TOKEN_BUDGET) {
                // 最新一组至少要保留，尤其是最后一条待回答问题；更早的内容则整体停止，避免拆散问答。
                if (keptUnits.isEmpty()) {
                    keptUnits.addFirst(unit);
                    used += tokens;
                }
                break;
            }
            keptUnits.addFirst(unit);
            used += tokens;
        }

        List<InterviewMessage> result = new ArrayList<>();
        if(systemMsg !=null){
            result.add(systemMsg);
        }
        for (List<InterviewMessage> unit : keptUnits) {
            result.addAll(unit);
        }
        log.info("会话上下文裁剪完成：原始{}条，保留{}条，预估token{}",messages.size(),result.size(),used);
        return result;
    }

    private List<List<InterviewMessage>> groupConversationUnits(List<InterviewMessage> messages) {
        List<List<InterviewMessage>> units = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            InterviewMessage current = messages.get(i);
            if ("assistant".equals(current.getRole())
                    && i + 1 < messages.size()
                    && "user".equals(messages.get(i + 1).getRole())) {
                units.add(List.of(current, messages.get(++i)));
            } else {
                units.add(List.of(current));
            }
        }
        return units;
    }

    public void evict(Long sessionId) {
        redisTemplate.delete(ctxKey(sessionId));
    }
    private int estimateTokens(String text) {
        return text.length() / 2;
    }

    private String ctxKey(Long sessionId) {
        return CTX_KEY_PREFIX+sessionId;
    }

    private String serialize(InterviewMessage message) {
        try{
            return objectMapper.writeValueAsString(message);
        }catch (JsonProcessingException e){
            throw new RuntimeException(e);
        }
    }
    private InterviewMessage deserialize(String json) {
        try {
            return objectMapper.readValue(json,InterviewMessage.class);
        }catch (JsonProcessingException e){
            throw new RuntimeException(e);
        }
    }
}
