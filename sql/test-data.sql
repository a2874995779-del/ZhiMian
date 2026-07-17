-- ============================================================
-- 智面 · 测试种子数据 v2(任务 1.6 / 1.7 自测用)
-- 内容:12 个分类(4 顶级 + 8 二级)、16 个标签、66 道题、管理员账号
--
-- 可重复执行:会清空 category / tag / question / question_tag 后重灌,
-- user 表只重建 username='admin' 这一个账号,其他账号不受影响。
--
-- 管理员账号:admin / admin123(密码为 BCrypt 哈希,与 Spring 兼容)
-- ============================================================

USE zhimian;

TRUNCATE TABLE question_tag;
TRUNCATE TABLE question;
TRUNCATE TABLE tag;
TRUNCATE TABLE category;

-- ---------- 管理员账号(admin / admin123) ----------
DELETE FROM user WHERE username = 'admin';
INSERT INTO user (username, password, nickname, role, status)
VALUES ('admin', '$2a$10$nIDBbGm1Zc6DU1itH61FsOZi0GMv2VU0h8cUErh3ymWdy42.aNbP6', '管理员', 'admin', 0);

-- ---------- 分类:3 顶级 + 8 二级 ----------
INSERT INTO category (id, name, parent_id, sort) VALUES
  (1,  'Java',       0, 1),
  (2,  '数据库',     0, 2),
  (7,  '计算机基础', 0, 3),
  (8,  '框架与中间件', 0, 4),
  (3,  'Java并发',   1, 2),
  (4,  'JVM',        1, 3),
  (13, 'Java基础',   1, 1),
  (5,  'MySQL',      2, 1),
  (6,  'Redis',      2, 2),
  (9,  '计算机网络', 7, 1),
  (10, '操作系统',   7, 2),
  (11, 'Spring',     8, 1);

-- ---------- 标签 ----------
INSERT INTO tag (id, name) VALUES
  (1, '线程池'), (2, '锁'), (3, '垃圾回收'), (4, '索引'),
  (5, '事务'),   (6, '缓存'), (7, '集合'),   (8, '高频'),
  (9, 'TCP'),    (10, 'HTTP'), (11, 'IOC'),  (12, 'AOP'),
  (13, '消息队列'), (14, 'String'), (15, 'HashMap'), (16, '分布式');

-- ---------- 题目:62 条 ----------
-- 难度: 1-简单 2-中等 3-困难 | category_id 均为二级分类
INSERT INTO question (id, title, content, answer, difficulty, category_id, view_count, create_user_id) VALUES
  -- Java并发 (cat 3)
  (1,  '线程池的核心参数有哪些?', '请结合 ThreadPoolExecutor 构造函数说明。', 'corePoolSize、maximumPoolSize、keepAliveTime、unit、workQueue、threadFactory、handler。', 2, 3, 320, 1),
  (2,  '线程的几种创建方式?', NULL, '继承 Thread、实现 Runnable、实现 Callable + FutureTask、线程池提交。', 1, 3, 210, 1),
  (3,  'synchronized 和 ReentrantLock 的区别?', NULL, '实现层面(JVM 关键字 vs JUC 类)、可中断、公平锁、条件队列数量、性能等。', 2, 3, 280, 1),
  (4,  '什么是线程安全?如何保证?', NULL, '原子性、可见性、有序性;加锁/CAS/不可变对象/ThreadLocal。', 1, 3, 150, 1),
  (5,  'volatile 关键字的作用与原理?', NULL, '保证可见性与有序性(内存屏障),不保证原子性。', 2, 3, 190, 1),
  (6,  'AQS 的实现原理?', '以 ReentrantLock 为例说明。', 'state + CLH 变体双向队列;独占/共享模式;acquire/release 模板方法。', 3, 3, 96, 1),
  (7,  '线程池的拒绝策略有哪些?各适用什么场景?', NULL, 'AbortPolicy、CallerRunsPolicy、DiscardPolicy、DiscardOldestPolicy。', 2, 3, 130, 1),
  (8,  'ThreadLocal 的原理与内存泄漏问题?', NULL, 'ThreadLocalMap 以 ThreadLocal 为弱引用 key;线程复用时需手动 remove。', 3, 3, 175, 1),
  (23, '线程池提交任务后的执行流程?', 'execute() 内部逻辑。', '核心线程→工作队列→非核心线程→拒绝策略,四步走。', 2, 3, 142, 1),
  (24, 'CAS 是什么?有什么问题?如何解决?', NULL, '比较并交换;ABA 问题(AtomicStampedReference)、自旋开销、只能保证单变量。', 2, 3, 118, 1),
  (25, '什么是死锁?产生条件与避免方法?', NULL, '互斥、持有并等待、不可剥夺、循环等待;破坏任一条件即可避免。', 1, 3, 165, 1),
  (26, 'CountDownLatch 和 CyclicBarrier 的区别?', NULL, '一次性 vs 可复用;计数递减 vs 到齐触发;典型使用场景不同。', 2, 3, 87, 1),
  -- JVM (cat 4)
  (9,  'JVM 内存区域划分?', NULL, '堆、虚拟机栈、本地方法栈、方法区(元空间)、程序计数器。', 1, 4, 240, 1),
  (10, '常见垃圾回收算法有哪些?', NULL, '标记-清除、标记-复制、标记-整理;分代收集。', 2, 4, 160, 1),
  (11, 'CMS 和 G1 收集器的区别?', NULL, '并发标记清除 vs Region 化增量回收;停顿目标、内存碎片。', 3, 4, 88, 1),
  (12, '类加载过程与双亲委派模型?', NULL, '加载-验证-准备-解析-初始化;向上委派;打破场景(SPI、热部署)。', 2, 4, 120, 1),
  (13, '什么情况下会发生 Full GC?', NULL, '老年代不足、元空间不足、System.gc()、晋升担保失败等。', 2, 4, 105, 1),
  (27, '如何判断对象可以被回收?', NULL, '可达性分析(GC Roots);引用计数的循环引用缺陷;四种引用类型。', 2, 4, 133, 1),
  (28, '内存泄漏和内存溢出的区别?举例说明。', NULL, '泄漏是无用对象无法回收(如静态集合持有);溢出是空间不够(OOM)。', 1, 4, 98, 1),
  (29, 'JVM 调优常用参数有哪些?', NULL, '-Xms/-Xmx/-Xmn、-XX:MetaspaceSize、GC 选择与日志参数。', 3, 4, 76, 1),
  -- MySQL (cat 5)
  (14, 'MySQL 索引为什么用 B+ 树?', NULL, '矮胖树减少磁盘 IO;叶子链表利于范围查询;非叶子只存键提高扇出。', 2, 5, 300, 1),
  (15, '聚簇索引和二级索引的区别?什么是回表?', NULL, '聚簇叶子存整行,二级叶子存主键;覆盖索引可避免回表。', 2, 5, 260, 1),
  (16, '事务的 ACID 分别如何保证?', NULL, '原子性 undo log、持久性 redo log、隔离性 锁+MVCC。', 3, 5, 220, 1),
  (17, 'MySQL 的事务隔离级别与各自问题?', NULL, '读未提交/读已提交/可重复读/串行化;脏读、不可重复读、幻读。', 1, 5, 195, 1),
  (18, '什么是最左前缀原则?', NULL, '联合索引按定义顺序匹配,遇范围查询后续列失效。', 1, 5, 170, 1),
  (19, 'LIMIT 100000,10 为什么慢?如何优化?', NULL, '扫描并丢弃前 10 万行;游标法或子查询先定位 id 再回表。', 3, 5, 140, 1),
  (30, 'MVCC 的实现原理?', NULL, '隐藏列(trx_id/roll_ptr)+ undo log 版本链 + ReadView 可见性判断。', 3, 5, 156, 1),
  (31, 'EXPLAIN 各字段的含义?重点看哪几个?', NULL, 'type/key/rows/Extra;type 至少到 range,Extra 警惕 filesort/temporary。', 2, 5, 188, 1),
  (32, '什么情况下索引会失效?', NULL, '函数/运算包裹列、隐式类型转换、前导 %、OR 混用、违反最左前缀。', 2, 5, 245, 1),
  (33, 'InnoDB 和 MyISAM 的区别?', NULL, '事务、行锁、外键、聚簇索引、崩溃恢复;MyISAM 只剩少数场景。', 1, 5, 112, 1),
  (34, '数据库事务并发会出现哪些问题?', NULL, '脏读、不可重复读、幻读、丢失更新;对应隔离级别与锁策略。', 2, 5, 134, 1),
  (35, 'binlog、redo log、undo log 的区别?', NULL, '服务层逻辑日志 vs 引擎层物理日志 vs 回滚版本链;两阶段提交。', 3, 5, 167, 1),
  -- Redis (cat 6)
  (20, 'Redis 为什么快?', NULL, '内存操作、单线程无锁竞争、IO 多路复用、高效数据结构。', 1, 6, 230, 1),
  (21, '缓存穿透、击穿、雪崩的区别与对策?', NULL, '空值缓存/布隆过滤器;互斥锁/逻辑过期;TTL 随机抖动。', 2, 6, 250, 1),
  (22, 'Redis 持久化方式 RDB 与 AOF 的取舍?', NULL, 'RDB 快照恢复快可能丢数据;AOF 更安全体积大;混合持久化。', 2, 6, 110, 1),
  (36, 'Redis 常用数据结构及使用场景?', NULL, 'String/Hash/List/Set/ZSet;计数器、对象缓存、队列、去重、排行榜。', 1, 6, 201, 1),
  (37, 'Redis 如何实现分布式锁?有哪些坑?', NULL, 'SET NX EX + 唯一值 + Lua 释放;误删、锁续期(看门狗)、主从切换丢锁。', 3, 6, 178, 1),
  (38, 'Redis 的过期删除与内存淘汰策略?', NULL, '惰性删除+定期删除;八种 maxmemory-policy,常用 allkeys-lru。', 2, 6, 95, 1),
  (39, '如何保证缓存与数据库的一致性?', NULL, '先更新库再删缓存(Cache Aside);延迟双删;订阅 binlog 异步删除。', 3, 6, 189, 1),
  (40, 'Redis 主从复制的原理?', NULL, '全量同步(RDB)+ 增量同步(复制积压缓冲区);replicaof 流程。', 2, 6, 84, 1),
  -- Java基础 (cat 13)
  (41, 'String、StringBuilder、StringBuffer 的区别?', NULL, '不可变 vs 可变;线程安全性;字符串常量池。', 1, 13, 310, 1),
  (42, 'HashMap 的底层实现原理?', 'JDK 8 为准。', '数组+链表+红黑树;hash 扰动、扩容 2 倍、树化阈值 8/退化 6。', 2, 13, 350, 1),
  (43, 'HashMap 线程不安全体现在哪?怎么解决?', NULL, 'JDK7 头插环链、JDK8 覆盖丢数据;ConcurrentHashMap/Collections.synchronizedMap。', 2, 13, 226, 1),
  (44, '== 和 equals 的区别?为什么重写 equals 必须重写 hashCode?', NULL, '引用比较 vs 逻辑相等;违反约定会导致 HashMap 等集合行为错误。', 1, 13, 198, 1),
  (45, 'ArrayList 和 LinkedList 的区别?', NULL, '动态数组 vs 双向链表;随机访问、中间插入、内存占用。', 1, 13, 244, 1),
  (46, '接口和抽象类的区别?什么时候用哪个?', NULL, '多实现 vs 单继承;is-a vs can-do;JDK8 后 default 方法的影响。', 1, 13, 156, 1),
  (47, 'Java 异常体系?运行时异常与受检异常的区别?', NULL, 'Throwable→Error/Exception;是否强制处理;事务回滚默认只回滚运行时异常。', 2, 13, 177, 1),
  (48, '深拷贝和浅拷贝的区别?如何实现深拷贝?', NULL, '引用共享 vs 完全复制;重写 clone、序列化、拷贝构造器。', 2, 13, 108, 1),
  -- 计算机网络 (cat 9)
  (49, 'TCP 三次握手的过程?为什么不能两次?', NULL, 'SYN→SYN+ACK→ACK;防止失效连接请求突然到达造成资源浪费。', 2, 9, 289, 1),
  (50, 'TCP 四次挥手为什么需要 TIME_WAIT?', NULL, '保证最后一个 ACK 可达、旧报文在网络中消亡;2MSL。', 2, 9, 213, 1),
  (51, 'TCP 和 UDP 的区别及适用场景?', NULL, '可靠连接 vs 无连接;拥塞控制;文件传输 vs 实时音视频。', 1, 9, 187, 1),
  (52, 'HTTPS 的加密过程?', NULL, '非对称交换会话密钥+对称加密数据;证书链校验防中间人。', 2, 9, 235, 1),
  (53, '从输入 URL 到页面展示发生了什么?', NULL, 'DNS→TCP→TLS→HTTP→渲染;每层都可展开追问。', 2, 9, 302, 1),
  (54, 'HTTP 1.1、2、3 的主要区别?', NULL, '长连接与管线化;多路复用与头部压缩;基于 QUIC 的 0-RTT。', 3, 9, 121, 1),
  (55, 'GET 和 POST 的区别?', NULL, '语义、幂等性、缓存、参数位置;本质都是 TCP 报文。', 1, 9, 176, 1),
  (56, 'TCP 如何保证可靠传输?', NULL, '序号确认、超时重传、滑动窗口、拥塞控制(慢启动/拥塞避免)。', 3, 9, 145, 1),
  -- 操作系统 (cat 10)
  (57, '进程和线程的区别?', NULL, '资源分配单位 vs 调度单位;地址空间隔离;切换开销。', 1, 10, 267, 1),
  (58, '进程间通信方式有哪些?', NULL, '管道、消息队列、共享内存、信号量、信号、Socket。', 2, 10, 158, 1),
  (59, '什么是虚拟内存?有什么好处?', NULL, '地址空间抽象;隔离、按需调页、内存超卖;缺页中断。', 2, 10, 119, 1),
  (60, '用户态和内核态的区别?什么时候切换?', NULL, '特权级隔离;系统调用、中断、异常触发切换;切换有开销。', 2, 10, 103, 1),
  -- Spring (cat 11)
  (61, 'Spring IOC 的理解?Bean 的生命周期?', NULL, '控制反转+依赖注入;实例化→属性填充→Aware→初始化→AOP代理→销毁。', 2, 11, 278, 1),
  (62, 'Spring AOP 的实现原理?', NULL, 'JDK 动态代理(接口)/CGLIB(子类);织入时机;自调用失效。', 2, 11, 254, 1),
  (63, 'Spring 事务失效的常见场景?', NULL, '自调用、非 public、异常被吞、受检异常默认不回滚、多线程。', 3, 11, 296, 1),
  (64, 'Spring 如何解决循环依赖?', NULL, '三级缓存;为什么需要第三级(AOP 代理提前暴露);构造器注入无解。', 3, 11, 232, 1),
  (65, '@Autowired 和 @Resource 的区别?', NULL, '按类型 vs 按名称;来源(Spring vs JSR-250);配合 @Qualifier。', 1, 11, 143, 1),
  (66, 'SpringBoot 自动配置的原理?', NULL, '@EnableAutoConfiguration + spring.factories/AutoConfiguration.imports + 条件注解。', 2, 11, 208, 1);

-- ---------- 题目-标签关联 ----------
INSERT INTO question_tag (question_id, tag_id) VALUES
  (1, 1), (1, 8), (2, 8), (3, 2), (3, 8), (5, 8), (6, 2), (7, 1), (8, 8),
  (23, 1), (24, 2), (25, 2), (25, 8),
  (10, 3), (11, 3), (13, 3), (27, 3), (29, 3),
  (14, 4), (14, 8), (15, 4), (16, 5), (17, 5), (17, 8), (18, 4), (30, 5), (31, 4), (32, 4), (32, 8), (34, 5), (35, 5),
  (20, 6), (21, 6), (21, 8), (22, 6), (36, 6), (37, 2), (37, 16), (38, 6), (39, 6), (39, 8), (40, 16),
  (41, 14), (41, 8), (42, 15), (42, 7), (42, 8), (43, 15), (43, 7), (44, 8), (45, 7),
  (49, 9), (49, 8), (50, 9), (51, 9), (52, 10), (53, 10), (53, 8), (54, 10), (55, 10), (56, 9),
  (57, 8), (58, 8),
  (61, 11), (61, 8), (62, 12), (62, 8), (63, 5), (63, 12), (64, 11), (66, 11);
-- 无标签题目:4、9、12、19、26、28、33、46、47、48、59、60、65(验证 tags 返回 [])

-- ---------- 校验 ----------
SELECT 'category' AS tbl, COUNT(*) AS cnt FROM category
UNION ALL SELECT 'tag', COUNT(*) FROM tag
UNION ALL SELECT 'question', COUNT(*) FROM question
UNION ALL SELECT 'question_tag', COUNT(*) FROM question_tag
UNION ALL SELECT 'admin_user', COUNT(*) FROM user WHERE username = 'admin';
