# Redis 初学 —— 从智面项目的真实代码讲起

> 本文档不是"要抄进项目"的参考实现,是学习资料。所有代码示例都来自项目里已经写好、能跑的真实代码(任务 1.4 登出黑名单、任务 1.8 浏览量计数、任务 1.9 题目详情缓存 + 热门题目缓存),配合 Redis 基础概念一起讲。建议对着项目源码(`QuestionServiceImpl`、`ViewCountFlushTask`、`JwtInterceptor`)一起看。

## 目录

1. [为什么这个项目要用 Redis](#1-为什么这个项目要用-redis)
2. [Redis 基础:本项目用到的数据类型和命令](#2-redis-基础本项目用到的数据类型和命令)
3. [StringRedisTemplate:Java 代码和 Redis 命令怎么对应](#3-stringredistemplatejava-代码和-redis-命令怎么对应)
4. [缓存的基本模式:Cache Aside(旁路缓存)](#4-缓存的基本模式cache-aside旁路缓存)
5. [实战一:JWT 登出黑名单](#5-实战一jwt-登出黑名单)
6. [实战二:浏览量异步计数](#6-实战二浏览量异步计数)
7. [实战三:题目详情缓存 + 缓存三大经典问题](#7-实战三题目详情缓存--缓存三大经典问题)
8. [实战四:热门题目缓存](#8-实战四热门题目缓存)
9. [detail 和 hot 两种缓存的设计差异对比](#9-detail-和-hot-两种缓存的设计差异对比)
10. [常见踩坑清单](#10-常见踩坑清单)
11. [命令速查表](#11-命令速查表)
12. [面试自查清单](#12-面试自查清单)

---

## 1. 为什么这个项目要用 Redis

MySQL 是磁盘数据库,每次查询都要经过"发请求 → 磁盘 I/O(或者走 InnoDB Buffer Pool 缓存)→ 解析 → 返回"这一整套流程;Redis 是**内存数据库**,数据全部放在内存里,读写不用碰磁盘,速度是数量级的差距。

这个项目里,Redis 承担了三种完全不同的角色,不要把它们混为一谈:

| 角色 | 具体场景 | 特点 |
|------|----------|------|
| **缓存** | 题目详情、热门题目列表 | 数据库里也有一份"权威数据",Redis 只是加速读、可以随时丢弃重建 |
| **计数器 / 临时状态** | 浏览量增量、互斥锁 | Redis 里的值本身就是"当前状态",有明确的生命周期(用完即焚) |
| **黑名单 / 名单类数据** | JWT 登出黑名单 | 靠 key 存不存在表达一个布尔状态("这个 token 是否已作废"),不关心 value 具体是什么 |

后面每个实战小节都会说清楚,当前这个场景属于哪一种角色。

---

## 2. Redis 基础:本项目用到的数据类型和命令

Redis 支持 String、Hash、List、Set、ZSet(有序集合)等多种数据结构,但**这个项目到目前为止只用到了 String**(排行榜阶段会用到 ZSet,那是后面任务的内容,这里先不展开)。

不要小看"只用 String"——String 类型上有一批看起来简单、但组合起来能解决很多复杂问题的原子命令,这个项目里的黑名单、计数器、分布式锁、穿透防护,全部靠下面这几个命令实现:

| 命令 | 作用 | 本项目用在哪 |
|------|------|------|
| `GET key` | 读取一个 key 的值,不存在返回 `nil` | 读所有缓存 |
| `SET key value` | 写入一个 key | 写所有缓存 |
| `SET key value EX seconds` | 写入的同时设置过期时间(秒) | 详情缓存、热门列表缓存 |
| `SET key value NX EX seconds` | **key 不存在才写入成功**,同时带过期时间 | 详情缓存重建的互斥锁 |
| `INCR key` | 对一个整数值 +1,key 不存在时先当 0 处理 | 浏览量计数器 |
| `GETDEL key` | **原子地**"读取值 + 删除 key" | 浏览量批量回写 |
| `DEL key` | 删除一个 key | 释放锁 |
| `EXISTS key` | 判断 key 是否存在 | JWT 黑名单校验 |
| `TTL key` | 查看一个 key 还有多久过期(秒),不存在返回 `-2`,永不过期返回 `-1` | 自测缓存是否生效 |
| `SCAN cursor MATCH pattern COUNT n` | **游标式**遍历匹配某个模式的 key,不阻塞 | 批量找出所有浏览量计数器 |
| `KEYS pattern` | 一次性遍历所有 key(**生产环境禁用**) | 本项目故意不用它,用来对比 SCAN 的优势 |

**为什么反复强调这些命令是"原子的"**:Redis 是单线程模型(准确说是"单线程执行命令",I/O 是多路复用的),**同一时刻只有一条命令在真正执行**,不会有"两个命令的执行过程交叉进行"这种情况。所以 `INCR`、`GETDEL`、`SET ... NX` 这些命令,不管多少个客户端同时发起,Redis 内部都是排队一条条处理,不需要你在应用层自己加锁去保证"读和写不会被打断"——这是 Redis 相比"先 GET 再判断再 SET"这种拆成多步的操作最大的优势。

---

## 3. StringRedisTemplate:Java 代码和 Redis 命令怎么对应

项目里通过 Spring Data Redis 提供的 `StringRedisTemplate` 操作 Redis,不是直接写 Redis 命令字符串。它的方法名和 Redis 原生命令不是逐字对应的,记住下面这张表,看到 Java 代码就能反应出背后是哪条 Redis 命令:

```java
private final StringRedisTemplate redisTemplate;
```

| Java 方法 | 对应的 Redis 命令 | 用途 |
|-----------|-------------------|------|
| `redisTemplate.opsForValue().get(key)` | `GET key` | 读 |
| `redisTemplate.opsForValue().set(key, value)` | `SET key value` | 写(不过期) |
| `redisTemplate.opsForValue().set(key, value, timeout, unit)` | `SET key value EX seconds` | 写 + 过期时间 |
| `redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit)` | `SET key value NX EX seconds` | 抢锁专用 |
| `redisTemplate.opsForValue().increment(key)` | `INCR key` | 计数器 +1 |
| `redisTemplate.opsForValue().getAndDelete(key)` | `GETDEL key` | 原子取值并删除 |
| `redisTemplate.delete(key)` | `DEL key` | 删除 |
| `redisTemplate.hasKey(key)` | `EXISTS key` | 判断存在 |
| `redisTemplate.getExpire(key)` | `TTL key` | 查看剩余过期时间 |

**为什么 `opsForValue()` 要专门拿一次**:`RedisTemplate` 对五种数据结构(String/Hash/List/Set/ZSet)分别提供了独立的操作接口——`opsForValue()` 是 String,`opsForHash()` 是 Hash,`opsForList()` 是 List,`opsForZSet()` 是 ZSet。`opsForValue()` 只是拿到"操作 String 这一类数据"的那个子接口,不是每次都要重新连接 Redis,可以理解成"我接下来要做的是 String 类型的操作"这样一个入口。

---

## 4. 缓存的基本模式:Cache Aside(旁路缓存)

在讲具体业务之前,先把这个项目**反复出现**的通用模式抽出来讲一遍,后面三个实战小节全都是这个模式的变体:

```java
public Data getData(Long id) {
    String cacheKey = "prefix:" + id;

    // 1. 先查缓存
    String cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
        return deserialize(cached);   // 命中,直接返回
    }

    // 2. 没命中,查数据库
    Data data = db.queryById(id);

    // 3. 查完顺手写回缓存,给下一个请求用
    redisTemplate.opsForValue().set(cacheKey, serialize(data), 30, TimeUnit.MINUTES);
    return data;
}
```

这个模式叫 **Cache Aside**(旁路缓存),之所以叫"旁路",是因为 **Redis 完全不知道 MySQL 的存在**——是应用层的业务代码在"旁边"同时维护着两份数据,自己决定什么时候读缓存、什么时候查库、什么时候把结果写回缓存。这是最简单、最常见的缓存模式,没有之一。

**读的规则**:先查缓存,命中就返回;没命中就查库、写回缓存。

**写的规则(这个项目还没有用到,但要知道,是常见的面试点)**:更新数据时,**先更新数据库,再删除缓存**(不是更新缓存,是删除!),让下一次读请求重新走一遍"查库 → 写缓存"的流程,拿到最新数据。**为什么不直接把新值也顺手更新进缓存**:如果两个写请求并发执行,后完成数据库更新的请求不一定后完成缓存更新,两次操作交织执行可能导致缓存里留下的是旧值——直接删除缓存、交给下一次读请求重新构建,逻辑更简单也更不容易出现这类竞态问题。

> **思考与延伸**:这个项目现在的 `updateQuestion`(修改题目)还没有在数据库更新之后去删除对应的 `zhimian:cache:question:detail:{id}` 缓存——也就是说,如果一道题被缓存过后,管理员改了题目内容,30 分钟之内详情接口返回的还是**改之前的旧内容**。这是当前版本一个明确的、留给你自己练习的点:找到 `updateQuestion` 方法,想一想应该在哪一行加上 `redisTemplate.delete(cacheKey)`。

---

## 5. 实战一:JWT 登出黑名单

**业务背景**:JWT 一旦签发,靠拦截器本地验签没法让某个 token"提前作废"(密钥没变,签名永远有效)。解决办法是维护一个"黑名单"——登出时把这个 token 的唯一标识(`jti`)记进 Redis,并让这条记录在 token 本该过期的时间自动消失。

这是本项目里 **Redis 当"名单"用**的例子,和"缓存"完全是两码事:这里的 key 存不存在本身就是答案(**存在 = 已登出**),`value` 具体是什么无所谓。

### 相关代码(`JwtUtil.java`)

```java
/** Redis 黑名单 key 前缀。拦截器(查)和 Service(写)两处都要用,放这里统一管理 */
public static final String BLACKLIST_KEY_PREFIX = "zhimian:jwt:blacklist:";
```

### 登出时写入黑名单(`UserServiceImpl.logout()`)

```java
@Override
public void logout() {
    String jti = UserContext.getJti();
    long expireAt = UserContext.getExpireAt();
    long remainingSeconds = (expireAt - System.currentTimeMillis()) / 1000;

    if (remainingSeconds > 0) {
        redisTemplate.opsForValue().set(
                JwtUtil.BLACKLIST_KEY_PREFIX + jti,
                "1",
                remainingSeconds,
                TimeUnit.SECONDS
        );
    }
}
```

**关键点:TTL 用的是"token 剩余有效期",不是固定值**。黑名单的唯一使命是"在 token 本该失效的那一刻之前,记得它已经作废"——用剩余有效期做 TTL,黑名单的生命周期和 token 的生命周期严格对齐,token 一过期,黑名单记录也跟着自动消失,不需要任何额外的清理逻辑。如果写死成固定 24 小时:一个只剩 10 分钟就自然过期的 token,黑名单却要占 24 小时的 Redis 内存,纯浪费。

### 拦截器校验(`JwtInterceptor.preHandle`)

```java
String jti = claims.getId();
if (Boolean.TRUE.equals(redisTemplate.hasKey(JwtUtil.BLACKLIST_KEY_PREFIX + jti))) {
    throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
}
```

`hasKey` 对应 `EXISTS` 命令——**验签只能证明"token 是我签的、没被改过、没过期",证明不了"我有没有主动作废它"**,所以每个受保护的请求,验签通过之后还要多查一次黑名单。这一次查询就是"无状态"的 JWT 为了支持"能登出"这个功能,主动付出的代价——面试被问"JWT 不是无状态的吗,你这里怎么又查了 Redis",这段话就是标准答案。

---

## 6. 实战二:浏览量异步计数

**业务背景**:详情接口是全站访问最频繁的接口。如果每次访问都执行 `UPDATE question SET view_count = view_count + 1`,高并发下同一行会有大量 MySQL 行锁竞争,请求互相排队,拖垮整个接口。解决办法:把"记一次浏览"这个高频动作放到 Redis 里做纯内存自增,攒一批之后再合并成一次低频的 MySQL 写。

这是 Redis 当**计数器**用的例子。

### 第一步:访问详情时,只累加 Redis 计数器(`QuestionServiceImpl.getDetail`)

```java
@Override
public QuestionDetailVO getDetail(Long id) {
    // ...查缓存、查库的逻辑先跳过,下一节细讲...

    // 不管这次是缓存命中还是查了库,这次访问都要算一次浏览量;只累加 Redis 计数器,不碰 MySQL
    redisTemplate.opsForValue().increment(VIEW_COUNT_PREFIX + id);
    return vo;
}
```

`increment(key)` 对应 `INCR key`——如果 `zhimian:question:view:5` 这个 key 之前不存在,Redis 会先当它是 0,自增后变成 1;已经存在就直接在原有值上 +1。**全程没有一次 MySQL 写操作**。

### 第二步:定时任务批量合并回写(`ViewCountFlushTask.java`)

```java
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
    private final StringRedisTemplate redisTemplate;
    private final QuestionMapper questionMapper;

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void flush() {
        Set<String> keys = scanKeys(VIEW_COUNT_PREFIX + "*");
        if (keys.isEmpty()) {
            return;
        }

        int flushed = 0;
        for (String key : keys) {
            // getAndDelete 是原子操作:一步到位"取值 + 删除",中间不会有新的 INCR 插进来丢计数
            String countStr = redisTemplate.opsForValue().getAndDelete(key);
            if (countStr == null) {
                continue;
            }
            Long questionId = Long.valueOf(key.substring(VIEW_COUNT_PREFIX.length()));
            int delta = Integer.parseInt(countStr);
            // 原地累加(view_count = view_count + delta),不是覆盖赋值
            questionMapper.incrementView(questionId, delta);
            flushed++;
        }
        log.info("浏览量批量回写完成,共处理 {} 个题目", flushed);
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor =
                         connection.scan(ScanOptions.scanOptions().match(pattern).count(100).build())) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        return keys;
    }
}
```

### 三个必须搞懂的细节

**① 为什么用 `GETDEL` 而不是"先 `GET` 再 `DEL`"**

假设拆成两条命令:

```
GET zhimian:question:view:5      → 读到 10
（这中间,恰好又有一次新的访问触发了 INCR，变成了 11）
DEL zhimian:question:view:5      → 直接把 11 删掉
```

这次"新增的 1"凭空消失了,永远不会被写进 MySQL。`GETDEL` 是**一条原子命令**,读和删是一步完成的,不存在这个中间的空档期——这也是为什么"用 Redis 提供的原子命令,而不是自己拼两条命令模拟等价逻辑"这么重要。

**② 为什么用 `SCAN` 不用 `KEYS`**

`KEYS pattern` 会遍历 Redis 里**所有** key 去做匹配,数据量大时是一个耗时的阻塞操作——Redis 单线程模型下,这段时间处理不了任何其他客户端的请求,官方文档明确写了"不要在生产环境使用"。`SCAN` 用**游标**分批返回,每次只扫一小部分,不会长时间占用 Redis:

```java
redisTemplate.execute((RedisCallback<Void>) connection -> {
    try (Cursor<byte[]> cursor = connection.scan(
            ScanOptions.scanOptions().match(pattern).count(100).build())) {
        while (cursor.hasNext()) {
            keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
        }
    }
    return null;
});
```

`try (Cursor<byte[]> cursor = ...)` 用的是 **try-with-resources** 语法——`Cursor` 实现了 `AutoCloseable`,不管方法体正常结束还是抛异常,离开这个 `try` 块时都会自动调用 `cursor.close()`,不用手写 `finally`。`Cursor` 底层占用着一份和 Redis 的连接资源,不关闭会造成连接泄漏。

**③ 为什么是"原地累加"而不是"覆盖赋值"**

```xml
<update id="incrementView">
    UPDATE question
    SET view_count = view_count + #{delta}   <!-- 正确:原地累加 -->
    WHERE id = #{id} AND is_deleted = 0
</update>
```

Redis 计数器里的 `delta` 只是**这一批 5 分钟时间窗口内的新增量**,不是这道题的总浏览量。如果写成 `SET view_count = #{delta}`,会把数据库里已经存在的历史浏览量全部冲掉——这是开发文档里明确点出的"易踩的坑"。

---

## 7. 实战三:题目详情缓存 + 缓存三大经典问题

这是整个项目里 Redis 用法最完整的一处,把"缓存怎么读写"和"三大经典问题怎么防"揉在了一起。先建立全局认知:

| 问题 | 触发条件 | 解法 | 代码里体现在哪 |
|------|----------|------|------|
| **缓存穿透** | 查询数据库里**根本不存在**的数据,每次都绕开缓存直接打库 | 查不到也缓存一个"空值"标记,给较短的 TTL | `rebuildCache` |
| **缓存雪崩** | **大量** key 在**同一时刻**集中过期,请求瞬间涌入数据库 | 缓存 TTL 加一段随机抖动,打散过期时间 | `rebuildCache` |
| **缓存击穿** | 一个**被高频访问的热点 key** 恰好过期,大量并发同时重建 | 重建时加互斥锁,只让一个请求真正查库 | `loadDetailWithLock` |

### 完整请求路径(先看图,再看代码)

```
getDetail(id)
 └─ 查 Redis: zhimian:cache:question:detail:{id}
     ├─ 命中 → readFromCacheOrThrow(cached)
     │          ├─ 值是 "null" 哨兵 → 抛 404,不查库(穿透防护生效)
     │          └─ 值是正常 JSON → 反序列化返回
     └─ 未命中 → loadDetailWithLock(id, cacheKey)
                 └─ 循环最多 5 轮:
                     ├─ setIfAbsent 抢锁
                     │    ├─ 抢到 → rebuildCache(真正查库、写缓存) → finally 释放锁
                     │    └─ 没抢到 → 睡 100ms → 再看一眼缓存
                     └─ 5 轮都没等到 → 直接查库兜底(不抢锁、不写缓存)
 └─ increment 浏览量 +1
 └─ return vo
```

### 入口:`getDetail`

```java
@Override
public QuestionDetailVO getDetail(Long id) {
    String cacheKey = DETAIL_CACHE_PREFIX + id;
    String cached = redisTemplate.opsForValue().get(cacheKey);
    QuestionDetailVO vo = (cached != null) ? readFromCacheOrThrow(cached) : loadDetailWithLock(id, cacheKey);
    redisTemplate.opsForValue().increment(VIEW_COUNT_PREFIX + id);
    return vo;
}
```

`(cached != null) ? A : B` 是**三元表达式**,`条件 ? 条件为真的值 : 条件为假的值`,和写一个 `if-else` 效果一样,只是更紧凑。这一行是整个缓存逻辑的分岔点:只判断"Redis 里有没有值",不关心这个值是正常数据还是穿透防护写入的哨兵值——这个判断被下放到 `readFromCacheOrThrow` 内部去做。

`increment` 放在 if-else **外面**:不管走了哪条路径,"这次访问"都成立,浏览量都要 +1,写在分支外面保证只写一次。

### 击穿防护核心:`loadDetailWithLock`

```java
private QuestionDetailVO loadDetailWithLock(Long id, String cacheKey) {
    String lockKey = LOCK_PREFIX + id;
    for (int i = 0; i < LOCK_WAIT_RETRY; i++) {
        // SET zhimian:lock:question:detail:{id} 1 NX EX 10
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(locked)) {
            try {
                return rebuildCache(id, cacheKey);
            } finally {
                redisTemplate.delete(lockKey);
            }
        }
        // 没抢到锁:说明有别的请求正在重建,睡一小段时间再看看缓存是不是已经建好了
        sleepQuietly(LOCK_WAIT_MS);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return readFromCacheOrThrow(cached);
        }
    }
    // 等了 LOCK_WAIT_RETRY 轮还没等到重建结果,不再阻塞,直接查库兜底
    return buildDetailVO(mustFindQuestion(id));
}
```

**逐个拆解:**

- `String lockKey = LOCK_PREFIX + id;`——**每道题一把独立的锁**,不是全局一把锁。锁的粒度做细,题目 A 重建缓存不会挡住题目 B 的请求。
- `setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS)`——对应 `SET key value NX EX 10`。`NX` 保证只有一个请求能抢到锁;`EX 10` 给锁本身设过期时间,**这是防死锁的关键**:万一持锁方处理过程中挂了,10 秒后 Redis 自动删掉这把锁,不会一直占着。
- `Boolean.TRUE.equals(locked)` 而不是 `if (locked)`——`setIfAbsent` 返回的是 `Boolean` 包装类型,极端情况下可能是 `null`,直接拆箱判断会有空指针风险,`Boolean.TRUE.equals(...)` 更安全。
- **`try { return rebuildCache(...); } finally { redisTemplate.delete(lockKey); }`**——用 `finally` 而不是在方法末尾写一行 `delete`,是因为 `rebuildCache` 可能抛异常(题目不存在的情况),如果只在末尾写,异常一抛,`delete` 根本执行不到,锁会一直占到 10 秒后才自动释放。`finally` 保证不管 try 块里正常返回还是抛异常,都会执行。
- **没抢到锁的分支**:不是"我也去查库",而是**先睡一会儿,再看看缓存是不是已经被持锁方建好了**——这才是"互斥锁"真正的意义:大部分并发请求不会重复查库,而是等一等直接复用别人建好的结果。
- **循环耗尽的兜底**:`LOCK_WAIT_RETRY = 5`、每次睡 `LOCK_WAIT_MS = 100` 毫秒,最多多等 500 毫秒。超过这个时间还没等到结果(比如持锁方异常卡住了),**直接查库返回,不再阻塞用户**——这是"宁可多查一次库,也不能让用户请求一直挂着"的工程取舍,不是死等。

### 穿透 + 雪崩防护:`rebuildCache`

```java
private QuestionDetailVO rebuildCache(Long id, String cacheKey) {
    Question question = questionMapper.selectById(id);
    if (question == null) {
        // 穿透防护:查不到也缓存"没有"这个事实,TTL 给得比正常详情缓存短很多
        redisTemplate.opsForValue().set(cacheKey, NULL_PLACEHOLDER, NULL_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
    }
    QuestionDetailVO vo = buildDetailVO(question);
    // 雪崩防护:基础 30 分钟 + 0~5 分钟随机抖动,避免同一批缓存同时过期
    long ttlSeconds = DETAIL_TTL_BASE_MINUTES * 60 + ThreadLocalRandom.current().nextInt((int) DETAIL_TTL_JITTER_SECONDS);
    redisTemplate.opsForValue().set(cacheKey, serialize(vo), ttlSeconds, TimeUnit.SECONDS);
    return vo;
}
```

**穿透防护**:`question == null` 时,不是直接抛异常完事,而是先往 `cacheKey` 写一个哨兵字符串(`NULL_PLACEHOLDER`),TTL 只给 1 分钟(比正常的 30 分钟短很多)。下次同一个不存在的 id 再被访问,`getDetail()` 一开始就能读到这个哨兵值,直接判定"之前查过、确认不存在",**根本不会再走到这个方法、不会再查数据库**。TTL 故意给短:万一这道题以后真的被创建出来了,最多 1 分钟哨兵值自然过期,不会一直挡着新数据。

**雪崩防护**:

```java
long ttlSeconds = DETAIL_TTL_BASE_MINUTES * 60 + ThreadLocalRandom.current().nextInt((int) DETAIL_TTL_JITTER_SECONDS);
```

基础 30 分钟(换算成秒是 `1800`)加上 `nextInt(300)` 生成的 `0~299` 之间的随机数,实际缓存时长落在 `1800~2099` 秒的一个随机区间里,不是所有题目都精确 30 分钟。**如果 TTL 都写死一样的值**,一批同时预热的缓存会在同一秒集体失效,瞬间涌入的请求全部打到数据库;加了随机抖动之后,同一批 key 的实际过期时间被打散,不会撞在一起。

`ThreadLocalRandom.current()` 而不是 `new Random()`:`Random` 内部用 CAS 保证线程安全,多线程同时用同一个实例会有竞争;`ThreadLocalRandom` 给每个线程一份独立的生成器,互不等待,更适合高并发场景。

### 缓存值的分支判断:`readFromCacheOrThrow`

```java
private QuestionDetailVO readFromCacheOrThrow(String cached) {
    if (NULL_PLACEHOLDER.equals(cached)) {
        throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
    }
    return deserialize(cached);
}
```

缓存里读到的字符串,可能是正常 JSON,也可能是穿透防护写入的哨兵值——这两种情况的区分逻辑收在一个地方,`getDetail` 缓存命中时、`loadDetailWithLock` 抢锁失败后重新读到缓存时,都调用它,不用各自重复写一遍判断。

### JSON 序列化:为什么要手动转字符串

```java
private String serialize(QuestionDetailVO vo) {
    try {
        return objectMapper.writeValueAsString(vo);
    } catch (JsonProcessingException e) {
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
```

`StringRedisTemplate` 的泛型是 `RedisTemplate<String, String>`,**只能存字符串**,不能直接存一个 Java 对象。要缓存一个 `QuestionDetailVO`,必须先用 `ObjectMapper`(Spring Boot 自动注册好的 Jackson Bean)把它"拍扁"成 JSON 字符串,取出来再解析回对象。

`catch` 到 `JsonProcessingException`(受检异常)之后直接包一层 `RuntimeException` 往上抛,没有做更精细的处理——因为这里序列化的对象都是自己 `new` 出来的简单 VO,不存在真的会失败的场景,不用为"不可能发生的情况"设计复杂的兜底逻辑。

### 组装 VO:`buildDetailVO`

```java
private QuestionDetailVO buildDetailVO(Question question) {
    QuestionDetailVO vo = new QuestionDetailVO();
    BeanUtils.copyProperties(question, vo);

    Map<Long, String> categoryNameMap = new HashMap<>();
    for (Category c : categoryMapper.selectAll()) {
        categoryNameMap.put(c.getId(), c.getName());
    }
    vo.setCategoryName(categoryNameMap.get(question.getCategoryId()));

    List<String> tags = new ArrayList<>();
    for (QuestionTagName row : questionTagMapper.selectTagNamesByQuestionIds(List.of(question.getId()))) {
        tags.add(row.getName());
    }
    vo.setTags(tags);
    return vo;
}
```

这个方法**不碰 Redis**,单纯是"把一个已经查到的 `Question` 拼成完整详情 VO"(补分类名、补标签)。参数是**已经查出来的对象**,不是 `id`——查询和判空是上一层(`rebuildCache`)的事,这里只管组装,职责分开。它被两个地方复用:`rebuildCache`(重建缓存时)和 `loadDetailWithLock` 循环耗尽后的兜底路径,两处都已经确保了"question 不为空"这个前提。

---

## 8. 实战四:热门题目缓存

```java
@Override
public List<QuestionListVO> getHotQuestions() {
    // 热门列表整块当一个 key 缓存,命中就不再查库、也不再重新排序
    String cached = redisTemplate.opsForValue().get(HOT_CACHE_KEY);
    if (cached != null) {
        return deserializeList(cached);
    }
    List<Question> records = questionMapper.selectHot(HOT_LIMIT);
    List<QuestionListVO> voList = enrichToListVO(records);

    long ttl = HOT_TTL_BASE_SECONDS + ThreadLocalRandom.current().nextInt((int) HOT_TTL_JITTER_SECONDS);
    redisTemplate.opsForValue().set(HOT_CACHE_KEY, serializeList(voList), ttl, TimeUnit.SECONDS);
    return voList;
}
```

和 detail 最大的区别:`HOT_CACHE_KEY = "zhimian:cache:question:hot"` 是一个**固定字符串**,不像 detail 那样拼 id——**全站只有一个热门列表 key**,不是每道题一个。

### 反序列化一个 List,为什么不能直接写 `.class`

```java
private List<QuestionListVO> deserializeList(String json) {
    try {
        return objectMapper.readValue(json, new TypeReference<List<QuestionListVO>>() {});
    } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
    }
}
```

这是本项目里**唯一一处**要用 `TypeReference` 的地方,原因是 Java 泛型的**类型擦除**——`List<QuestionListVO>` 这个类型只在编译期存在,编译成 `.class` 之后,虚拟机运行时只看到光秃秃的 `List`,不知道里面装的是什么。如果写 `readValue(json, List.class)`,Jackson 只能把每个元素解析成一个 `LinkedHashMap`,不是你想要的 `QuestionListVO`,后面调用 `vo.getTitle()` 会直接报错。

`new TypeReference<List<QuestionListVO>>() {}` 是一个**匿名内部类**(最后那对 `{}` 是在创建 `TypeReference` 的匿名子类实例)。虽然泛型信息本身会被擦除,但"某个匿名类继承了 `TypeReference<List<QuestionListVO>>`"这件事,会被编译器记录在 `.class` 文件的签名信息里——Jackson 通过反射把这份签名读出来,就能还原出完整的目标类型,正确地把 JSON 数组解析成 `List<QuestionListVO>`。**只要遇到"反序列化一个带泛型的集合类型",就套用这个写法**,不用每次重新推导。

### 复用:`enrichToListVO`

```java
private List<QuestionListVO> enrichToListVO(List<Question> records) {
    if (records.isEmpty()) {
        return List.of();
    }
    Map<Long, String> categoryNameMap = new HashMap<>();
    for (Category c : categoryMapper.selectAll()) {
        categoryNameMap.put(c.getId(), c.getName());
    }
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
    return voList;
}
```

这个方法本身**不碰 Redis**,是"一批 `Question` → 一批补全分类名和标签的 `QuestionListVO`"的转换逻辑,被 `pageQuestions()`(分页查询)和 `getHotQuestions()` 共用,避免同样的代码写两遍——这是"消除重复代码"的工程原则,和 Redis 无关,但正是它让 `getHotQuestions` 的实现能写得这么短。

---

## 9. detail 和 hot 两种缓存的设计差异对比

| 维度 | detail(题目详情) | hot(热门列表) |
|------|------|------|
| 缓存粒度 | 每道题一个 key | 全站一个 key |
| 穿透防护 | ✅ 空值缓存 | ❌ 没有 |
| 击穿防护 | ✅ 互斥锁 | ❌ 没有 |
| 雪崩防护 | ✅ TTL 随机抖动 | ✅ TTL 随机抖动 |

**为什么 hot 不需要穿透和击穿防护:**

- **穿透**防的是"查询数据库里根本不存在的数据"——detail 的查询条件(`id`)是外部输入,任何人都能在 URL 里改成一个不存在的 id 来攻击;hot 接口没有任何外部输入参数,查询条件永远是"view_count 排前 10",不存在"查一个不存在的热门榜单"这种场景。
- **击穿**防的是"一个高频访问的热点 key 恰好过期,大量并发同时重建"——detail 有成百上千道题,每一道都可能是热点;hot 只有一个 key、重建成本相对固定(一条简单查询,不像详情可能带长文本),为它专门加锁的收益覆盖不了额外的复杂度和性能代价。

工程决策不是"能加的防护都加上",而是要判断"这个场景的风险和收益是否值得为它专门做防护"。

---

## 10. 常见踩坑清单

| 坑 | 后果 | 正确做法 |
|----|------|----------|
| 用 `KEYS pattern` 而不是 `SCAN` | 数据量大时阻塞整个 Redis | 用 `SCAN` 游标遍历 |
| 先 `GET` 再 `DEL`,拆成两条命令 | 中间窗口期的新写入被无声丢弃 | 用原子命令 `GETDEL` |
| 回写计数用覆盖赋值(`SET view_count = delta`) | 冲掉数据库里已有的历史值 | 用原地累加(`SET view_count = view_count + delta`) |
| 缓存 TTL 全部写死同一个值 | 缓存雪崩 | 基础值 + 随机抖动 |
| 查不到就不缓存,直接返回 404 | 缓存穿透,每次都要查库 | 缓存一个"空值"哨兵,给较短 TTL |
| 缓存重建不加锁 | 缓存击穿,并发全部打到数据库 | `SET key value NX EX ttl` 做互斥锁 |
| 锁不设过期时间 | 持锁方一挂,锁永远释放不了(死锁) | 加锁时必须带 `EX`,给锁设生存时间 |
| 释放锁用 `DEL` 但不校验归属 | 可能误删别人刚加上的新锁 | 锁 value 存唯一标识,释放前用 Lua 脚本校验后再删(本项目当前是简化版,面试要能说出这个坑) |
| 反序列化 `List<T>` 直接写 `.class` | Jackson 只能解析成 `LinkedHashMap`,后续取值报错 | 用 `TypeReference<List<T>>` |
| `Boolean` 类型直接 `if(obj)` 判断 | 极端情况下 `null` 拆箱抛 `NullPointerException` | 用 `Boolean.TRUE.equals(obj)` |

---

## 11. 命令速查表

```
# 手动查看某个 key 的值和剩余过期时间
redis-cli GET zhimian:cache:question:detail:1
redis-cli TTL zhimian:cache:question:detail:1

# 手动删除一个 key(模拟"缓存过期"来测试)
redis-cli DEL zhimian:cache:question:detail:1

# 查看所有浏览量计数器(仅用于本地调试,别在生产环境用 KEYS)
redis-cli KEYS "zhimian:question:view:*"

# 查看某个计数器当前值
redis-cli GET zhimian:question:view:1

# 查看 JWT 黑名单是否命中
redis-cli EXISTS zhimian:jwt:blacklist:{jti}
```

---

## 12. 面试自查清单

对着这些问题自己心里过一遍答案,答不上来就回到对应小节重新看一遍代码:

1. Cache Aside 模式的读写规则分别是什么?为什么写操作是"删缓存"不是"更新缓存"?
2. `INCR` 为什么是原子的?Redis 单线程模型为什么还能支撑高并发?
3. `GETDEL` 解决了"先 GET 再 DEL"的什么问题?
4. `SCAN` 和 `KEYS` 的区别是什么?为什么生产环境要禁用 `KEYS`?
5. 缓存穿透、雪崩、击穿的定义分别是什么?本项目里各自的解法是什么、写在哪个方法里?
6. `SET key value NX EX seconds` 这条命令为什么能实现互斥锁?`EX` 为什么不能省略?
7. 释放锁时直接 `DEL` 有什么风险?生产级的正确做法是什么?
8. 为什么 JWT 黑名单要用 token 剩余有效期做 TTL,不能写固定值?
9. Java 泛型的类型擦除是什么?为什么反序列化 `List<T>` 要用 `TypeReference`?
10. detail 和 hot 两处缓存,为什么后者不需要做穿透和击穿防护?
