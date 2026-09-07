package com.zhimian.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.ErrorCode;
import com.zhimian.common.PageResult;
import com.zhimian.common.UserContext;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.CategoryMapper;
import com.zhimian.mapper.QuestionMapper;
import com.zhimian.mapper.QuestionTagMapper;
import com.zhimian.model.dto.QuestionAddDTO;
import com.zhimian.model.dto.QuestionQueryDTO;
import com.zhimian.model.entity.Category;
import com.zhimian.model.entity.Question;
import com.zhimian.model.entity.QuestionTagName;
import com.zhimian.model.vo.QuestionDetailVO;
import com.zhimian.model.vo.QuestionListVO;
import com.zhimian.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {
    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;
    private final CategoryMapper categoryMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    //分割线，配置属性
    private static final String DETAIL_CACHE_PREFIX = "zhimian:cache:question:detail:";
    private static final String VIEW_COUNT_PREFIX = "zhimian:question:view:";
    private static final String LOCK_PREFIX = "zhimian:lock:question:detail:";
    private static final String HOT_CACHE_KEY = "zhimian:cache:question:hot";
    private static final int HOT_LIMIT = 10;
    private static final long HOT_TTL_BASE_SECONDS = 5 * 60;
    private static final long HOT_TTL_JITTER_SECONDS = 60;
    private static final String NULL_PLACEHOLDER = "null";
    private static final long DETAIL_TTL_BASE_MINUTES=30;
    private static final long DETAIL_TTL_JITTER_SECONDS= 5*60;
    private static final long NULL_CACHE_TTL_MINUTES=1;
    private static final long LOCK_TTL_SECONDS = 10;
    private static final int LOCK_WAIT_RETRY = 5;
    private static final long LOCK_WAIT_MS = 100;

    @Transactional
    @Override
    public Long addQuestion(QuestionAddDTO dto) {
        Question question = new Question();
        question.setTitle(dto.getTitle());
        question.setContent(dto.getContent());
        question.setAnswer(dto.getAnswer());
        question.setDifficulty(dto.getDifficulty());
        question.setCategoryId(dto.getCategoryId());
        question.setCreateUserId(UserContext.getUserId());
        questionMapper.insert(question);
        // 空列表必须在这里挡住:<foreach> 对空列表会生成非法 SQL
        if(!CollectionUtils.isEmpty(dto.getTagIds())){
            questionTagMapper.batchInsert(question.getId(),dto.getTagIds());
        }
        return question.getId();
    }
    @Transactional
    @Override
    public void updateQuestion(Long id, QuestionAddDTO dto) {
        Question question = new Question();
        question.setId(id);
        question.setTitle(dto.getTitle());
        question.setContent(dto.getContent());
        question.setAnswer(dto.getAnswer());
        question.setDifficulty(dto.getDifficulty());
        question.setCategoryId(dto.getCategoryId());
        // update 的 WHERE 带 is_deleted = 0,影响行数为 0 说明题目不存在或已删除
        if(questionMapper.update(question) == 0){
            throw new BusinessException(ErrorCode.NOT_FOUND,"题目不存在");
        }
        questionTagMapper.deleteByQuestionId(id);
        if(!CollectionUtils.isEmpty(dto.getTagIds())){
            questionTagMapper.batchInsert(id,dto.getTagIds());
        }
        evictQuestionCachesAfterCommit(id);
    }

    @Transactional
    @Override
    public void deleteQuestion(Long id) {
        if(questionMapper.logicalDelete(id) == 0){
            throw new BusinessException(ErrorCode.NOT_FOUND,"题目不存在");
        }
        // 关联记录留不留都不影响正确性(所有查询以 question.is_deleted 为准),
        // 这里选择顺手清掉,让关联表只存有效关系
        questionTagMapper.deleteByQuestionId(id);
        evictQuestionCachesAfterCommit(id);
    }

    @Override
    public PageResult<QuestionListVO> pageQuestions(QuestionQueryDTO dto) {
        long total = questionMapper.count(dto);
        if (total == 0) {
            return PageResult.of(List.of(), 0, dto.getPageNum(), dto.getPageSize());
        }
        List<Question> records = questionMapper.selectPage(dto);

        Map<Long, String> categoryNameMap = new HashMap<>();
        for (Category c : categoryMapper.selectAll()) {
            categoryNameMap.put(c.getId(), c.getName());
        }

        // 补齐 tags:当页题目 id 一次 IN 查询,再按 questionId 分组
        List<Long> questionIds = new ArrayList<>();
        for (Question q : records) {
            questionIds.add(q.getId());
        }
        Map<Long, List<String>> tagMap = new HashMap<>();
        for (QuestionTagName row : questionTagMapper.selectTagNamesByQuestionIds(questionIds)) {
            tagMap.computeIfAbsent(row.getQuestionId(), k -> new ArrayList<>()).add(row.getName());
        }

        List<QuestionListVO> voList = new ArrayList<>();
        for (Question q : records) {
            QuestionListVO vo = new QuestionListVO();
            BeanUtils.copyProperties(q, vo);
            vo.setCategoryName(categoryNameMap.get(q.getCategoryId()));
            vo.setTags(tagMap.getOrDefault(q.getId(), List.of()));
            voList.add(vo);
        }
        return PageResult.of(voList, total, dto.getPageNum(), dto.getPageSize());
    }

    @Override
    public QuestionDetailVO getDetail(Long id) {
        String cacheKey = DETAIL_CACHE_PREFIX + id;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        // 缓存命中(哪怕命中的是"空值"哨兵)直接走 readFromCacheOrThrow;真正没命中才需要抢锁重建,
        // 两条路径最终都不会绕过下面的浏览量计数
        QuestionDetailVO vo = (cached !=null) ? readFromCacheOrThrow(cached) : loadDetailWithLock(id,cacheKey);
        // 不管这次是缓存命中还是查了库,这次访问都要算一次浏览量;只累加 Redis 计数器,不碰 MySQL
        redisTemplate.opsForValue().increment(VIEW_COUNT_PREFIX + id);
        return vo;
    }



    @Override
    public List<QuestionListVO> getHotQuestions() {
        // 热门列表整块当一个 key 缓存(不是每道题分开缓存),命中就不再查库、也不再重新排序
        String cached = redisTemplate.opsForValue().get(HOT_CACHE_KEY);
        if(cached !=null){
            return deserializeList(cached);
        }
        List<Question> records = questionMapper.selectHot(HOT_LIMIT);
        List<QuestionListVO> voList = enrichToListVO(records);

        // 基础 5 分钟 + 0~60 秒随机抖动:防止这个 key 和其他缓存 key 在同一时刻集中过期(缓存雪崩)
        long ttl = HOT_TTL_BASE_SECONDS + ThreadLocalRandom.current().nextInt((int) HOT_TTL_JITTER_SECONDS);
        redisTemplate.opsForValue().set(HOT_CACHE_KEY,serializeList(voList),ttl,TimeUnit.SECONDS);
        return voList;
    }

    /**
     * 缓存未命中时的重建逻辑,用互斥锁防止大量并发同时打到数据库(缓存击穿)。
     * 只有抢到锁的请求才会真正查库重建缓存,其余请求要么等锁释放后读到别人建好的缓存,
     * 要么等了 LOCK_WAIT_RETRY 轮还没等到,直接查库兜底,不让用户的请求无限期挂着。
     */
    private QuestionDetailVO loadDetailWithLock(Long id, String cacheKey) {
        String lockKey = LOCK_PREFIX + id;
        for(int i=0;i<LOCK_WAIT_RETRY;i++){
            // setIfAbsent 对应 Redis 的 SET key value NX EX,key 不存在才能设置成功,天然带过期时间防死锁
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey,"1",LOCK_TTL_SECONDS,TimeUnit.SECONDS);
            if(Boolean.TRUE.equals(locked)){
                try {
                    return rebuildCache(id,cacheKey);
                }finally {
                    // 不管重建成功还是抛异常都要释放锁,不然要等 LOCK_TTL_SECONDS 到期才会自动放开
                    redisTemplate.delete(lockKey);
                }
            }
            // 没抢到锁:说明有别的请求正在重建,睡一小段时间再看看缓存是不是已经建好了
            sleepQuietly(LOCK_WAIT_MS);
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if(cached !=null){
                return readFromCacheOrThrow(cached);
            }
        }
        // 等了 LOCK_WAIT_RETRY 轮还没等到重建结果(持锁方异常卡住等极端情况),不再阻塞,直接查库兜底
        return buildDetailVO(mustFindQuestion(id));
    }

    // 把 Question 实体拼成详情 VO(补 categoryName、tags),rebuildCache 和上面的兜底路径都要用,抽出来复用
    private QuestionDetailVO buildDetailVO(Question question) {
        QuestionDetailVO vo = new QuestionDetailVO();
        BeanUtils.copyProperties(question, vo);
        Map<Long, String> categoryNameMap = new HashMap<>();
        for (Category c : categoryMapper.selectAll()) {
            categoryNameMap.put(c.getId(), c.getName());
        }
        vo.setCategoryName(categoryNameMap.get(question.getCategoryId()));
        List<String> tags = new ArrayList<>();
        for(QuestionTagName row : questionTagMapper.selectTagNamesByQuestionIds(List.of(question.getId()))){
            tags.add(row.getName());
        }
        vo.setTags(tags);
        return vo;
    }

    // 仅用于 loadDetailWithLock 抢锁失败超时后的兜底查询:不加锁、也不写缓存,只是让用户这次请求能拿到结果
    private Question mustFindQuestion(Long id) {
        Question question = questionMapper.selectById(id);
        if(question == null){
            throw new BusinessException(ErrorCode.NOT_FOUND,"题目不存在");
        }
        return question;
    }

    // 抢锁失败时的等待动作。InterruptedException 不能吞掉不处理,重新调用 interrupt() 把中断状态还给线程,
    // 不然上层(比如线程池关闭时的中断信号)会感知不到这次中断,是 Java 并发编程里处理 InterruptedException 的标准写法
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }

    // 真正查库、写缓存(含穿透防护:查不到也缓存空值),只有抢到锁的请求会调用这个方法
    private QuestionDetailVO rebuildCache(Long id, String cacheKey) {
        Question question = questionMapper.selectById(id);
        if(question == null){
            // 穿透防护:哪怕库里真没有这条数据,也要缓存"没有"这个事实,TTL 给得比正常详情缓存短很多,
            // 避免这道题以后被建出来了,还要一直等空值缓存过期才能被访问到
            redisTemplate.opsForValue().set(cacheKey,NULL_PLACEHOLDER,NULL_CACHE_TTL_MINUTES,TimeUnit.MINUTES);
            throw new BusinessException(ErrorCode.NOT_FOUND,"题目不存在");
        }
        QuestionDetailVO vo = buildDetailVO(question);
        // 雪崩防护:基础 30 分钟 + 0~5 分钟随机抖动,避免同一批热点题目的缓存同时过期
        long ttlSeconds = DETAIL_TTL_BASE_MINUTES * 60 + ThreadLocalRandom.current().nextInt((int)DETAIL_TTL_JITTER_SECONDS);
        redisTemplate.opsForValue().set(cacheKey,serialize(vo),ttlSeconds,TimeUnit.SECONDS);
        return vo;
    }

    // 缓存里读到的字符串,可能是正常 JSON,也可能是穿透防护写入的 NULL_PLACEHOLDER 哨兵值——
    // 哨兵值代表"之前查过、确认这条数据不存在",直接抛 NOT_FOUND,不用再去查一次数据库
    private QuestionDetailVO readFromCacheOrThrow(String cached) {
        if(NULL_PLACEHOLDER.equals(cached)){
            throw new BusinessException(ErrorCode.NOT_FOUND,"题目不存在");
        }
        return deserialize(cached);
    }




    private String serializeList(List<QuestionListVO> list) {
        try {
            return objectMapper.writeValueAsString(list);
        }catch (JsonProcessingException e){
            throw new RuntimeException(e);
        }
    }

    // 补 categoryName、tags 这段逻辑 pageQuestions 和 getHotQuestions 都要用,抽成私有方法避免两处重复
    private List<QuestionListVO> enrichToListVO(List<Question> records) {
        if(records.isEmpty()){
            return List.of();
        }
        Map<Long,String> categoryNameMap = new HashMap<>();
        for(Category c : categoryMapper.selectAll()){
            categoryNameMap.put(c.getId(), c.getName());
        }
        List<Long> questionIds = new ArrayList<>();
        for(Question q : records){
            questionIds.add(q.getId());
        }
        Map<Long,List<String>> tagMap = new HashMap<>();
        for(QuestionTagName row : questionTagMapper.selectTagNamesByQuestionIds(questionIds)){
            tagMap.computeIfAbsent(row.getQuestionId(),k->new ArrayList<>()).add(row.getName());
        }
        List<QuestionListVO> voList = new ArrayList<>();
        for(Question q : records){
            QuestionListVO vo = new QuestionListVO();
            BeanUtils.copyProperties(q,vo);
            vo.setCategoryName(categoryNameMap.get(q.getCategoryId()));
            vo.setTags(tagMap.getOrDefault(q.getId(),List.of()));
            voList.add(vo);
        }
        return voList;
    }

    // 反序列化 List<QuestionListVO> 必须用 TypeReference,不能直接写 .class——
    // 泛型信息在运行时会被擦除,ObjectMapper.readValue(json, List.class) 只能知道"这是个 List",
    // 不知道里面装的是什么类型,只能退化成一堆 LinkedHashMap;TypeReference 借助匿名内部类把完整泛型
    // 信息保留到运行时,ObjectMapper 才能正确还原成 List<QuestionListVO>
    private List<QuestionListVO> deserializeList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<QuestionListVO>>() {
            });
        }catch (JsonProcessingException e){
            throw new RuntimeException(e);
        }
    }
    private String serialize(QuestionDetailVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (JsonProcessingException e) {
            // VO 就是几个基础字段和字符串列表,不存在序列化失败的可能,包一层非受检异常往上抛即可
            throw new RuntimeException(e);
        }
    }

    private QuestionDetailVO deserialize(String json) {
        try {
            return objectMapper.readValue(json, QuestionDetailVO.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void evictQuestionCachesAfterCommit(Long questionId) {
        Runnable eviction = () -> {
            try {
                redisTemplate.delete(List.of(
                        DETAIL_CACHE_PREFIX + questionId,
                        HOT_CACHE_KEY
                ));
            } catch (RuntimeException e) {
                log.warn("清理题目缓存失败:questionId={}", questionId, e);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eviction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eviction.run();
            }
        });
    }
}
