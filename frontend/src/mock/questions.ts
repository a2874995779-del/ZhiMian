import type { QuestionCategory, QuestionDetail, QuestionListItem } from '../types/question'

export const categories: QuestionCategory[] = [
  { id: 1, name: 'Java 基础' },
  { id: 2, name: '并发编程' },
  { id: 3, name: 'JVM' },
  { id: 4, name: 'Redis' },
  { id: 5, name: 'MySQL' },
  { id: 6, name: '系统设计' },
]

interface MockQuestion extends QuestionDetail {}

const bank: MockQuestion[] = [
  {
    id: 1,
    title: '线程池的核心参数有哪些?为什么不推荐用 Executors 直接创建线程池?',
    categoryId: 2,
    categoryName: '并发编程',
    tags: ['thread-pool', 'juc'],
    difficulty: 2,
    readMinutes: 6,
    mastery: 82,
    viewCount: 1290,
    content:
      '结合 ThreadPoolExecutor 的 7 个构造参数说明线程池的运行机制:核心线程数、最大线程数、空闲存活时间、阻塞队列、线程工厂、拒绝策略——并说明为什么阿里巴巴 Java 开发手册明确禁止用 Executors 快捷方法创建线程池。',
    answer:
      'corePoolSize/maximumPoolSize 决定线程数的伸缩区间,workQueue 决定任务排队策略,handler 决定队列和线程都耗尽后的拒绝行为。Executors.newFixedThreadPool 等快捷方法底层用的是无界队列(LinkedBlockingQueue),在任务持续涌入时会导致队列无限膨胀,最终 OOM——所以生产环境要求手写 ThreadPoolExecutor,显式指定有界队列和拒绝策略。',
    codeSnippet: {
      language: 'java',
      code: `// 手写线程池,显式指定每一个参数,而不是走 Executors 的快捷方法
ExecutorService pool = new ThreadPoolExecutor(
    4,                              // corePoolSize
    8,                              // maximumPoolSize
    60L, TimeUnit.SECONDS,          // keepAliveTime
    new ArrayBlockingQueue<>(200),  // 有界队列,防止无限堆积
    new ThreadFactoryBuilder().setNameFormat("answer-flush-%d").build(),
    new ThreadPoolExecutor.CallerRunsPolicy() // 队列满了,退回调用者线程执行
);`,
    },
  },
  {
    id: 2,
    title: 'Redis 如何解决缓存穿透、缓存雪崩、缓存击穿?',
    categoryId: 4,
    categoryName: 'Redis',
    tags: ['cache', 'redis'],
    difficulty: 2,
    readMinutes: 8,
    mastery: 91,
    viewCount: 2310,
    content:
      '穿透:查询一个数据库里根本不存在的 key,缓存和数据库都没有命中,每次都打到数据库。雪崩:大量 key 在同一时刻集中过期。击穿:一个高频访问的热点 key 恰好过期,大量并发请求同时去重建缓存。分别说出对应的工程解法。',
    answer:
      '穿透用空值缓存(短 TTL)或布隆过滤器提前拦截;雪崩给 TTL 加上随机抖动,避免同一批 key 同时到期;击穿用互斥锁(SET NX EX)保证只有一个请求重建缓存,其余请求等待或读旧值,也可以用逻辑过期方案换取更高可用性。',
    codeSnippet: {
      language: 'java',
      code: `// 互斥锁防击穿:只有抢到锁的请求真正查库重建缓存
Boolean locked = redisTemplate.opsForValue()
    .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
if (Boolean.TRUE.equals(locked)) {
    try {
        return rebuildCache(id, cacheKey);
    } finally {
        redisTemplate.delete(lockKey);
    }
}`,
    },
  },
  {
    id: 3,
    title: 'ZSet 底层是跳表,为什么范围查询场景不选红黑树?',
    categoryId: 4,
    categoryName: 'Redis',
    tags: ['redis', 'data-structure'],
    difficulty: 3,
    readMinutes: 7,
    mastery: 64,
    viewCount: 876,
    content:
      '从"区间遍历的复杂度"和"实现复杂度"两个角度,比较跳表和红黑树在 ZSet 场景下的取舍,并说明 ZINCRBY 和 ZREVRANGE 分别对应什么操作。',
    answer:
      '跳表做区间查询只需要在最底层链表上顺序遍历,复杂度和结果集大小线性相关;红黑树做区间查询需要中序遍历再截取,实现和加锁都更复杂。跳表用多层索引把平均查找复杂度控制在 O(logN),写路径也天然支持并发场景下更简单的锁粒度设计。',
    codeSnippet: {
      language: 'bash',
      code: `# ZINCRBY:原子性地给 member 的分数 +1,不存在则从 0 开始
ZINCRBY zhimian:rank:answer:total 1 10086

# ZREVRANGE:按分数从高到低取名次区间,WITHSCORES 带上分数
ZREVRANGE zhimian:rank:answer:total 0 9 WITHSCORES`,
    },
  },
  {
    id: 4,
    title: 'MySQL 索引为什么用 B+ 树而不是 B 树或哈希索引?',
    categoryId: 5,
    categoryName: 'MySQL',
    tags: ['mysql', 'index'],
    difficulty: 2,
    readMinutes: 6,
    mastery: 78,
    viewCount: 1655,
    content:
      '从磁盘 IO 次数、范围查询能力、叶子节点是否存数据这几个维度,对比 B+ 树相对 B 树和哈希索引的优势。',
    answer:
      'B+ 树非叶子节点不存数据,单个节点能存更多索引项,树更矮、磁盘 IO 更少;叶子节点通过链表相连,天然支持范围查询和排序;哈希索引虽然等值查询是 O(1),但完全不支持范围查询和排序,这在业务场景里是致命短板。',
    codeSnippet: {
      language: 'sql',
      code: `-- 联合索引最左前缀原则:只按 tag_id 查询用不上下面这个索引
CREATE TABLE question_tag (
  question_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY (question_id, tag_id),
  KEY idx_tag_id (tag_id) -- 需要单独建反向索引
);`,
    },
  },
  {
    id: 5,
    title: 'JVM 的 CMS 和 G1 垃圾回收器分别解决了什么问题?',
    categoryId: 3,
    categoryName: 'JVM',
    tags: ['jvm', 'gc'],
    difficulty: 3,
    readMinutes: 9,
    mastery: 55,
    viewCount: 934,
    content:
      '说明 CMS 的"标记-清除"如何产生内存碎片、G1 的分区(Region)设计如何兼顾吞吐量和停顿时间,以及为什么 G1 之后 CMS 被标记为废弃。',
    answer:
      'CMS 用标记-清除算法,不整理内存,长期运行会产生碎片,碎片过多时触发一次代价极高的 Full GC。G1 把堆划分成多个大小相等的 Region,可以对任意几个 Region 做混合回收,并支持"停顿时间目标"这种可预测的调优方式,兼顾了吞吐量和延迟,因此在 JDK 9 之后成为默认回收器。',
    codeSnippet: {
      language: 'bash',
      code: `# 显式指定 G1 并设置期望的最大停顿时间(毫秒)
java -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -jar app.jar`,
    },
  },
  {
    id: 6,
    title: '如何设计一个支持高并发的短链接系统?',
    categoryId: 6,
    categoryName: '系统设计',
    tags: ['system-design', 'scalability'],
    difficulty: 3,
    readMinutes: 12,
    mastery: 40,
    viewCount: 1522,
    content:
      '从短码生成算法(自增 ID 转 62 进制 vs 哈希取模)、读多写少场景下的缓存分层、以及跳转接口的 301/302 选择这三个角度展开设计。',
    answer:
      '短码生成优先用发号器 + 62 进制编码,保证不重复且可预测长度;读多写少场景下用多级缓存(本地缓存+Redis)扛住跳转请求;跳转用 302 而不是 301,因为 301 会被浏览器强缓存,后续无法做 A/B 测试或统计点击量。',
    codeSnippet: {
      language: 'text',
      code: `发号器(Snowflake/MySQL 自增段) -> 62 进制编码 -> 短码
写入:MySQL(事实来源) 双写 Redis(短码 -> 原始 URL)
读取:Redis 命中直接 302;未命中回源 MySQL 并回填缓存`,
    },
  },
  {
    id: 7,
    title: 'HashMap 在并发场景下会出问题吗?ConcurrentHashMap 是怎么解决的?',
    categoryId: 2,
    categoryName: '并发编程',
    tags: ['juc', 'collection'],
    difficulty: 2,
    readMinutes: 7,
    mastery: 70,
    viewCount: 1108,
    content:
      'JDK 1.7 和 1.8 的 HashMap 在并发扩容下分别会出现什么问题?ConcurrentHashMap 在 1.8 之后用什么机制替代了分段锁?',
    answer:
      '1.7 的 HashMap 并发扩容会因为链表头插法形成环形链表,导致死循环;1.8 改成尾插法后不会死循环,但仍会丢数据。ConcurrentHashMap 在 1.8 之后放弃了分段锁(Segment),改用 CAS + synchronized 只锁单个桶,锁粒度更细,并发度显著提升。',
    codeSnippet: {
      language: 'java',
      code: `// 1.8 ConcurrentHashMap:只在桶级别加锁,而不是锁整个数组
synchronized (f) {
    if (tabAt(tab, i) == f) {
        // 只锁住当前链表/红黑树头节点 f,其余桶不受影响
    }
}`,
    },
  },
  {
    id: 8,
    title: 'MySQL 的可重复读隔离级别是如何避免幻读的?',
    categoryId: 5,
    categoryName: 'MySQL',
    tags: ['mysql', 'transaction'],
    difficulty: 3,
    readMinutes: 8,
    mastery: 48,
    viewCount: 1341,
    content: '结合 MVCC 的快照读和当前读、以及间隙锁(Gap Lock),说明 InnoDB 在 RR 隔离级别下如何规避幻读。',
    answer:
      '快照读靠 MVCC 的 ReadView 机制,只能看到事务开始时刻已提交的数据,天然避免了快照读维度的幻读;当前读(SELECT ... FOR UPDATE)则依赖间隙锁+行锁组成的 Next-Key Lock,锁住一个范围而不只是已存在的行,阻止其他事务在这个范围内插入新记录。',
    codeSnippet: {
      language: 'sql',
      code: `-- Next-Key Lock:锁住 [10, 20) 这个区间,阻止范围内的插入
SELECT * FROM question WHERE id BETWEEN 10 AND 20 FOR UPDATE;`,
    },
  },
  {
    id: 9,
    title: '什么是分布式锁?基于 Redis 实现分布式锁有哪些经典的坑?',
    categoryId: 4,
    categoryName: 'Redis',
    tags: ['redis', 'distributed-lock'],
    difficulty: 3,
    readMinutes: 9,
    mastery: 60,
    viewCount: 1789,
    content: '说明 SET NX EX 实现分布式锁的基本原理,以及"释放了别人的锁"这个经典问题的成因和解法。',
    answer:
      '加锁用 SET key value NX EX,保证互斥且自带过期防死锁。经典的坑是释放锁时没有校验"锁是不是自己加的"——持锁方业务跑得太久锁自动过期,另一个请求抢到新锁,原持锁方结束后直接 DEL 会误删别人的锁。解法是锁的 value 存唯一标识(如 UUID),释放时用 Lua 脚本做"先判断再删除"的原子操作。',
    codeSnippet: {
      language: 'lua',
      code: `-- 释放锁的 Lua 脚本:先判断 value 是不是自己的,再删除,保证原子性
if redis.call("GET", KEYS[1]) == ARGV[1] then
    return redis.call("DEL", KEYS[1])
else
    return 0
end`,
    },
  },
  {
    id: 10,
    title: '什么情况下会发生 OOM?排查思路是什么?',
    categoryId: 3,
    categoryName: 'JVM',
    tags: ['jvm', 'troubleshooting'],
    difficulty: 2,
    readMinutes: 7,
    mastery: 73,
    viewCount: 998,
    content: '列举堆内存 OOM、栈内存 OOM、元空间 OOM 的典型触发场景,并说明拿到 dump 文件后如何定位问题。',
    answer:
      '堆 OOM 常见于大对象持续创建且无法回收(内存泄漏或查询未分页);栈 OOM 常见于递归没有终止条件;元空间 OOM 常见于动态生成类过多(比如反射/字节码增强框架滥用)。排查思路是 -XX:+HeapDumpOnOutOfMemoryError 拿到 dump,用 MAT 分析支配树,定位占用内存最大的对象和它的 GC Root 引用链。',
    codeSnippet: {
      language: 'bash',
      code: `# 发生 OOM 时自动生成堆转储文件,交给 MAT/JProfiler 分析
java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/logs/heap.hprof -jar app.jar`,
    },
  },
  {
    id: 11,
    title: '如何设计一个每秒十万级请求的排行榜服务?',
    categoryId: 6,
    categoryName: '系统设计',
    tags: ['system-design', 'redis'],
    difficulty: 3,
    readMinutes: 10,
    mastery: 35,
    viewCount: 1204,
    content: '结合 Redis ZSet 的写入原子性和读取复杂度,说明如何支撑高并发的实时排行榜,并处理多维度榜单(日榜/总榜)的过期策略。',
    answer:
      '写路径用 ZINCRBY 保证并发下分数更新的原子性,避免"先查后写"丢更新;读路径用 ZREVRANGE WITHSCORES 取 TopN,复杂度 O(logN+M);日榜 key 按日期分片,只在首次创建时设置 TTL,总榜不设过期;数据库落一份流水表兜底,允许 Redis 和 MySQL 短暂不一致但不允许永久丢失。',
    codeSnippet: {
      language: 'java',
      code: `// 日榜 key 只在首次创建时设置 7 天 TTL,避免每次答题都重新续期
Long ttl = redisTemplate.getExpire(dailyKey);
if (ttl != null && ttl == -1) {
    redisTemplate.expire(dailyKey, 7, TimeUnit.DAYS);
}`,
    },
  },
  {
    id: 12,
    title: '双亲委派模型是什么?为什么要打破它?',
    categoryId: 3,
    categoryName: 'JVM',
    tags: ['jvm', 'classloader'],
    difficulty: 2,
    readMinutes: 6,
    mastery: 66,
    viewCount: 812,
    content: '说明类加载器的委派机制如何保证核心类库不被篡改,以及 Tomcat、SPI 机制为什么需要打破双亲委派。',
    answer:
      '双亲委派要求加载请求先交给父加载器,自己找不到才自己加载,保证 java.lang.Object 这类核心类无论谁加载结果都是同一个类。Tomcat 需要隔离不同 Web 应用的类,SPI 机制需要用调用方的类加载器加载实现类,这两种场景都必须打破双亲委派,自定义类加载顺序。',
    codeSnippet: {
      language: 'java',
      code: `@Override
protected Class<?> loadClass(String name, boolean resolve) {
    // Tomcat 自定义 WebAppClassLoader:优先自己加载,隔离不同应用
    synchronized (getClassLoadingLock(name)) {
        Class<?> c = findLoadedClass(name);
        if (c == null) c = findClass(name); // 不再优先委派给父加载器
        return c;
    }
}`,
    },
  },
]

export function listQuestions(): QuestionListItem[] {
  return bank.map((q) => ({
    id: q.id,
    title: q.title,
    categoryId: q.categoryId,
    categoryName: q.categoryName,
    tags: q.tags,
    difficulty: q.difficulty,
    readMinutes: q.readMinutes,
    mastery: q.mastery,
    viewCount: q.viewCount,
  }))
}

export function getQuestionDetail(id: number): QuestionDetail | undefined {
  return bank.find((q) => q.id === id)
}
