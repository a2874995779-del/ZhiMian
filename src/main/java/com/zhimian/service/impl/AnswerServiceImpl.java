package com.zhimian.service.impl;

import com.zhimian.common.ErrorCode;
import com.zhimian.common.UserContext;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.AnswerRecordMapper;
import com.zhimian.mapper.QuestionMapper;
import com.zhimian.mapper.UserMapper;
import com.zhimian.model.dto.AnswerSubmitDTO;
import com.zhimian.model.entity.AnswerRecord;
import com.zhimian.model.entity.Question;
import com.zhimian.model.entity.User;
import com.zhimian.model.vo.RankVO;
import com.zhimian.service.AnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AnswerServiceImpl implements AnswerService {

    private final AnswerRecordMapper answerRecordMapper;
    private final QuestionMapper questionMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String RANK_TOTAL_KEY = "zhimian:rank:answer:total";
    private static final String RANK_DAILY_PREFIX = "zhimian:rank:answer:";
    private static final long RANK_DAILY_TTL_DAYS = 7;
    private static final DateTimeFormatter DAILY_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    @Override
    public void submitAnswer(long questionId, AnswerSubmitDTO dto) {
        Question question = questionMapper.selectById(questionId);
        if(question == null){
            throw new BusinessException(ErrorCode.NOT_FOUND,"题目不存在");
        }
        Long userId = UserContext.getUserId();
        AnswerRecord record = new AnswerRecord();
        record.setUserId(userId);
        record.setQuestionId(questionId);
        record.setResult(dto.getResult());
        answerRecordMapper.insert(record);
        if(dto.getResult() == 1){
            String member = userId.toString();
            redisTemplate.opsForZSet().incrementScore(RANK_TOTAL_KEY,member,1);
            String dailyKey = todayDailyKey();
            redisTemplate.opsForZSet().incrementScore(dailyKey,member,1);
            Long ttl = redisTemplate.getExpire(dailyKey);
            if(ttl !=null && ttl==-1){
                redisTemplate.expire(dailyKey,RANK_DAILY_TTL_DAYS, TimeUnit.DAYS);
            }
        }
    }

    @Override
    public List<RankVO> getRank(String type, int limit) {
        if(limit <= 0){
            return List.of();
        }
        String key = resolveRankKey(type);
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(
                key,0,limit-1);
        if (tuples == null || tuples.isEmpty()){
            return List.of();
        }
        List<Long> userIds = new ArrayList<>();
        for(ZSetOperations.TypedTuple<String> tuple : tuples){
            userIds.add(Long.valueOf(tuple.getValue()));
        }
        Map<Long,String> nicknameMap = new HashMap<>();
        for(User u : userMapper.selectByIds(userIds)){
            nicknameMap.put(u.getId(),u.getNickname());
        }
        List<RankVO> result = new ArrayList<>();
        int rank=1;
        for(ZSetOperations.TypedTuple<String> tuple : tuples){
            Long userId = Long.valueOf(tuple.getValue());
            RankVO vo = new RankVO();
            vo.setRank(rank++);
            vo.setUserId(userId);
            vo.setNickname(nicknameMap.get(userId));
            vo.setCount(tuple.getScore().intValue());
            result.add(vo);
        }
        return result;
    }

    private String resolveRankKey(String type) {
        if("daily".equals(type)){
            return todayDailyKey();
        }
        return RANK_TOTAL_KEY;
    }

    private String todayDailyKey() {
        return RANK_DAILY_PREFIX + LocalDate.now().format(DAILY_KEY_FORMATTER);
    }
}
