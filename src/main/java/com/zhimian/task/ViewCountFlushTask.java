package com.zhimian.task;

import com.zhimian.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 详情接口每次访问只往 Redis 计数器(zhimian:question:view:{id})里 INCR 一次,不直接碰 MySQL,
 * 避免高并发下同一行 view_count 的 UPDATE 造成行锁竞争。这个定时任务负责定期把这些计数器的增量
 * 批量合并回写到 question.view_count,写完删掉计数器——用"最终一致性"换详情接口的读写性能。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountFlushTask {
    private static final String VIEW_COUNT_PREFIX = "zhimian:question:view:";
    private static final RedisScript<String> GET_AND_DELETE_SCRIPT = RedisScript.of(
            "local value = redis.call('get', KEYS[1]); "
                    + "if value then redis.call('del', KEYS[1]); end; return value",
            String.class
    );
    private final StringRedisTemplate redisTemplate;
    private final QuestionMapper questionMapper;

    @Scheduled(fixedRate = 5 * 60* 1000)
    public void flush(){
        Set<String> keys = scanKeys(VIEW_COUNT_PREFIX + "*");
        if(keys.isEmpty()){
            return;
        }
        int flushed = 0;
        for(String key : keys){
            // Lua 在 Redis 5 也能原子完成取值和删除，避免依赖 Redis 6.2 才提供的 GETDEL。
            String countStr = redisTemplate.execute(GET_AND_DELETE_SCRIPT, List.of(key));
            if(countStr == null){
                continue;
            }
            int delta = Integer.parseInt(countStr);
            try {
                Long questionId = Long.valueOf(key.substring(VIEW_COUNT_PREFIX.length()));
                questionMapper.incrementView(questionId,delta);
                flushed++;
            } catch (RuntimeException e) {
                // 数据库失败时把已取出的增量补回原 key；期间新产生的 INCR 会和它继续累加。
                redisTemplate.opsForValue().increment(key, delta);
                log.error("浏览量回写失败，增量已放回Redis:key={},delta={}", key, delta, e);
            }
        }
        log.info("浏览量批量回写完成，共处理{}个题目",flushed);
    }
    /**
     * 用 SCAN 游标遍历匹配的 key,而不是 KEYS 命令——KEYS 会遍历整个 keyspace 且阻塞其他请求,
     * 生产环境是明确不推荐使用的;SCAN 分批游标遍历,不会阻塞
     */
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        redisTemplate.execute((RedisCallback<Void>) connection ->{
            try(Cursor<byte[]> cursor =
                    connection.keyCommands().scan(
                            ScanOptions.scanOptions().match(pattern).count(100).build())){
                while (cursor.hasNext()){
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        return keys;
    }
}
