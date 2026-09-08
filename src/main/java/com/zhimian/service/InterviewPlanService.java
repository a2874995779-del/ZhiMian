package com.zhimian.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhimian.common.ErrorCode;
import com.zhimian.exception.BusinessException;
import com.zhimian.model.dto.CreateInterviewDTO;
import com.zhimian.model.entity.InterviewSession;
import com.zhimian.model.enums.InterviewDirection;
import com.zhimian.model.enums.InterviewMode;
import com.zhimian.model.interview.InterviewPlan;
import com.zhimian.model.interview.InterviewPlanItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewPlanService {
    private final ObjectMapper objectMapper;

    public InterviewPlan build(CreateInterviewDTO dto) {
        InterviewMode mode = InterviewMode.fromCode(defaultMode(dto.getMode()));
        if (mode == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "面试模式不正确");
        }
        return mode == InterviewMode.SCENARIO
                ? buildScenario(dto.getScenarioCode(), targetCount(dto))
                : buildDirection(dto.getDirection(), targetCount(dto));
    }

    public String serialize(InterviewPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("面试计划序列化失败", e);
        }
    }

    public InterviewPlan load(InterviewSession session) {
        if (session.getPlanJson() != null && !session.getPlanJson().isBlank()) {
            try {
                return objectMapper.readValue(session.getPlanJson(), InterviewPlan.class);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("面试计划数据损坏", e);
            }
        }
        // 兼容升级前创建的会话：没有计划时按原方向补一份稳定的旧版计划。
        CreateInterviewDTO dto = new CreateInterviewDTO();
        dto.setMode(InterviewMode.DIRECTION.getCode());
        dto.setDirection(session.getDirection());
        dto.setTargetQuestionCount(session.getTargetQuestionCount());
        return buildDirection(session.getDirection(), targetCount(dto));
    }

    public InterviewPlanItem itemForRound(InterviewPlan plan, int roundNo) {
        if (roundNo <= 0 || roundNo > plan.items().size()) {
            return null;
        }
        return plan.items().get(roundNo - 1);
    }

    private InterviewPlan buildDirection(String directionCode, int targetCount) {
        InterviewDirection direction = InterviewDirection.fromCode(directionCode);
        if (direction == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "暂不支持该面试方向");
        }
        List<InterviewPlanItem> items = new ArrayList<>(directionItems(direction));
        shuffle(items);
        return new InterviewPlan(
                InterviewMode.DIRECTION.getCode(),
                direction.getCode(),
                direction.getLabel() + "专项面试",
                direction.getFocus(),
                renumber(items.subList(0, Math.min(targetCount, items.size())))
        );
    }

    private InterviewPlan buildScenario(String scenarioCode, int targetCount) {
        Scenario scenario = Scenario.fromCode(scenarioCode);
        if (scenario == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "暂不支持该综合面试场景");
        }
        List<InterviewPlanItem> items = new ArrayList<>(scenario.items);
        shuffle(items);
        return new InterviewPlan(
                InterviewMode.SCENARIO.getCode(),
                scenario.code,
                scenario.title,
                scenario.focus,
                renumber(items.subList(0, Math.min(targetCount, items.size())))
        );
    }

    private List<InterviewPlanItem> directionItems(InterviewDirection direction) {
        return switch (direction.getCode()) {
            case "java_concurrency" -> List.of(
                    item("thread_pool", "线程池", "核心参数、任务提交和拒绝策略", "基础原理", 1, "线程池的核心参数有哪些？为什么不推荐直接使用 Executors 创建线程池？"),
                    item("thread_pool", "线程池", "线程池参数如何结合业务配置", "工程实践", 2, "如果一个接口既有 CPU 密集任务又有 IO 密集任务，你会如何分别配置线程池？"),
                    item("lock", "锁与同步", "synchronized 与 ReentrantLock", "原理对比", 2, "synchronized 和 ReentrantLock 有哪些区别？你会在什么场景选择它们？"),
                    item("cas", "CAS", "CAS、ABA 与自旋", "底层原理", 2, "CAS 是如何实现并发更新的？ABA 问题是什么，通常如何解决？"),
                    item("aqs", "AQS", "同步器队列与独占共享模式", "底层原理", 3, "请说明 AQS 的基本工作机制，以及它如何支持独占和共享两种模式？"),
                    item("concurrent_collection", "并发容器", "ConcurrentHashMap", "集合原理", 2, "JDK 8 的 ConcurrentHashMap 做了哪些并发控制？扩容时有什么特点？"),
                    item("visibility", "线程协作", "volatile 与内存可见性", "基础原理", 1, "volatile 能保证什么，不能保证什么？它为什么不能替代 i++ 的原子性？"),
                    item("deadlock", "并发排障", "死锁定位与处理", "故障排查", 3, "线上 Java 服务出现线程全部阻塞，你会如何判断是否发生死锁并定位问题？"),
                    item("queue", "线程协作", "BlockingQueue 与生产消费", "工程实践", 2, "如何用 BlockingQueue 实现一个有界生产者消费者模型？队列满了怎么办？"),
                    item("atomic", "原子类", "AtomicInteger 与 LongAdder", "性能权衡", 2, "AtomicLong 和 LongAdder 的实现思路有什么不同？高并发计数时如何选择？"),
                    item("safe_publish", "对象安全", "对象发布与线程安全", "工程实践", 2, "一个对象在多个线程之间共享时，怎样避免看到未完成初始化的状态？"),
                    item("project", "项目实践", "并发问题的完整处理", "综合实践", 3, "请结合一个实际项目，说明你处理过的并发安全问题、定位过程和最终方案。")
            );
            case "jvm" -> List.of(
                    item("runtime_area", "运行时数据区", "堆、栈、方法区", "基础原理", 1, "你能从运行时数据区开始，整体讲一下 JVM 的内存模型吗？"),
                    item("gc", "垃圾回收", "可达性分析与回收算法", "底层原理", 2, "JVM 如何判断一个对象是否可以被回收？常见垃圾回收算法有哪些？"),
                    item("collector", "垃圾回收器", "G1 与分代回收", "原理对比", 3, "G1 垃圾回收器解决了什么问题？它和传统分代收集器有什么区别？"),
                    item("class_loading", "类加载", "双亲委派", "基础原理", 2, "请解释类加载过程和双亲委派模型，为什么要这样设计？"),
                    item("oom", "内存排障", "堆外内存与 OOM", "故障排查", 3, "Java 进程内存持续增长但堆使用率不高，你会从哪些方向排查？"),
                    item("jmm", "Java 内存模型", "可见性、有序性、原子性", "底层原理", 2, "Java 内存模型中的可见性、有序性和原子性分别是什么意思？"),
                    item("jstack", "线上诊断", "线程 dump", "故障排查", 2, "线上 CPU 飙高或线程阻塞时，你会使用哪些工具和步骤定位？"),
                    item("jit", "运行时优化", "JIT 与逃逸分析", "底层原理", 3, "JIT 编译器和逃逸分析会对 Java 程序做哪些优化？"),
                    item("metaspace", "类元数据", "Metaspace 与类卸载", "故障排查", 2, "什么情况下可能出现 Metaspace 溢出？如何定位和治理？"),
                    item("gc_tuning", "性能调优", "GC 日志与停顿", "工程实践", 3, "如果接口 P99 延迟偶发升高，你如何判断是否与 GC 停顿有关？"),
                    item("reference", "对象引用", "强软弱虚引用", "基础原理", 1, "Java 中几种引用类型有什么区别？软引用适合直接用来做缓存吗？"),
                    item("project", "项目实践", "JVM 问题复盘", "综合实践", 3, "请讲一个你会如何排查和解决的 Java 应用内存持续增长问题。")
            );
            case "mysql" -> List.of(
                    item("index", "索引", "B+ 树与范围查询", "基础原理", 1, "MySQL 的 B+ 树索引为什么适合范围查询？"),
                    item("index_design", "索引设计", "联合索引与最左匹配", "工程实践", 2, "设计联合索引时如何考虑最左匹配、选择性和回表？"),
                    item("transaction", "事务", "ACID 与隔离级别", "基础原理", 1, "事务的 ACID 分别是什么？MySQL 的隔离级别解决了哪些问题？"),
                    item("mvcc", "事务", "MVCC 与 Read View", "底层原理", 3, "InnoDB 的 MVCC 如何实现一致性读？Read View 中保存了什么信息？"),
                    item("lock", "锁机制", "行锁、间隙锁与死锁", "底层原理", 3, "InnoDB 的间隙锁解决什么问题？什么情况下容易出现死锁？"),
                    item("slow_sql", "SQL 优化", "执行计划", "故障排查", 2, "拿到一条慢 SQL 后，你会怎样使用 EXPLAIN 定位问题？"),
                    item("index_failure", "SQL 优化", "索引失效", "工程实践", 2, "哪些写法可能导致索引失效？你会如何验证优化是否有效？"),
                    item("binlog", "高可用", "binlog 与主从复制", "架构原理", 3, "MySQL 主从复制的基本流程是什么？延迟和数据一致性如何处理？"),
                    item("large_table", "数据治理", "大表分页与归档", "系统设计", 3, "一张千万级题目记录表需要分页和归档，你会怎样设计？"),
                    item("redo_undo", "存储引擎", "redo log 与 undo log", "底层原理", 3, "redo log 和 undo log 分别解决什么问题？"),
                    item("connection", "数据库资源", "连接池与慢查询", "工程实践", 2, "数据库连接池耗尽时你会如何定位，是 SQL 慢、连接泄漏还是并发过高？"),
                    item("project", "项目实践", "慢 SQL 复盘", "综合实践", 3, "请结合执行计划说明你会如何定位并优化一条慢 SQL。")
            );
            case "redis" -> List.of(
                    item("data_type", "数据结构", "String、Hash、Set、ZSet", "基础原理", 1, "Redis 常见的数据结构有哪些？你在项目里会怎么选择？"),
                    item("cache", "缓存", "Cache Aside", "工程实践", 1, "Cache Aside 的读写流程是什么？更新数据库后为什么通常删除缓存？"),
                    item("penetration", "缓存问题", "穿透、击穿、雪崩", "故障治理", 2, "缓存穿透、击穿和雪崩有什么区别？分别如何处理？"),
                    item("lock", "分布式锁", "NX、TTL 与 token", "底层原理", 2, "Redis 分布式锁为什么需要 NX、过期时间和唯一 token？"),
                    item("lua", "Redis 原子性", "Lua 脚本", "底层原理", 3, "为什么释放分布式锁不能简单执行 GET 再 DEL？Lua 在这里解决了什么问题？"),
                    item("counter", "高频计数", "计数器与异步刷库", "系统设计", 2, "浏览量很高时，如何使用 Redis 计数并降低 MySQL 写压力？"),
                    item("persistence", "持久化", "RDB 与 AOF", "原理对比", 2, "RDB 和 AOF 有什么区别？如何在数据安全和恢复速度之间取舍？"),
                    item("eviction", "内存治理", "淘汰策略", "工程实践", 2, "Redis 内存达到上限时有哪些淘汰策略？缓存系统如何选择？"),
                    item("cluster", "集群", "分片与热点 key", "架构原理", 3, "Redis Cluster 如何分片？热点 key 可能带来什么问题？"),
                    item("big_key", "性能治理", "大 key 与慢命令", "故障排查", 3, "线上 Redis 延迟突然升高，你会如何检查大 key、慢命令和连接情况？"),
                    item("rate_limit", "流量治理", "限流算法", "系统设计", 2, "如何用 Redis 实现一个按用户限流的接口？固定窗口有什么缺点？"),
                    item("project", "项目实践", "Redis 方案复盘", "综合实践", 3, "请结合一个项目说明你为什么使用 Redis，以及如何处理一致性和故障。")
            );
            case "system_design" -> List.of(
                    item("requirement", "需求分析", "功能与非功能需求", "系统设计", 1, "拿到一个系统设计题时，你会先如何澄清业务目标和非功能需求？"),
                    item("ranking", "排行榜", "读写链路与排序", "系统设计", 2, "如果让你设计一个高并发排行榜服务，你会怎么设计读写链路？"),
                    item("traffic", "流量治理", "限流、降级、熔断", "系统设计", 2, "系统流量突然增长时，你会从哪些层面进行扩容和保护？"),
                    item("cache_db", "数据一致性", "缓存与数据库", "架构权衡", 2, "缓存和数据库如何保持一致？不同一致性要求下方案如何选择？"),
                    item("idempotency", "接口设计", "幂等与重复请求", "工程实践", 2, "支付回调或网络重试导致接口重复执行时，你会如何保证幂等？"),
                    item("message", "异步架构", "消息队列与最终一致性", "系统设计", 3, "什么时候应该引入消息队列？如何处理重复消费、消息丢失和积压？"),
                    item("sharding", "数据扩展", "分库分表", "架构权衡", 3, "数据量和流量持续增长时，你会在什么条件下考虑分库分表？"),
                    item("observability", "可观测性", "日志、指标、链路", "工程实践", 2, "一个接口变慢但错误率没有上升，你会如何通过监控定位瓶颈？"),
                    item("failure", "故障处理", "降级与恢复", "故障排查", 3, "核心依赖不可用时，系统如何降级？恢复后如何补偿数据？"),
                    item("hotspot", "性能治理", "热点数据与缓存击穿", "系统设计", 2, "一个热门题目被瞬间访问百万次，你会如何保护缓存和数据库？"),
                    item("security", "安全设计", "鉴权与数据隔离", "工程实践", 2, "一个多用户系统如何保证接口鉴权、越权防护和数据隔离？"),
                    item("tradeoff", "架构权衡", "成本、复杂度与收益", "综合实践", 3, "请说明一次你在性能、成本、开发复杂度之间做取舍的设计。")
            );
            default -> List.of(item("general", "项目实践", "技术方案表达", "综合实践", 1, "请介绍一个你最熟悉的技术点，并说明它在项目中的使用场景。"));
        };
    }

    private enum Scenario {
        MEITUAN("meituan_style_backend", "美团风格后端面试（模拟）", "围绕交易、配送、流量和稳定性展开的后端综合面试", commonScenarioItems()),
        TENCENT("tencent_style_backend", "腾讯风格后端面试（模拟）", "围绕基础能力、项目深挖和高并发系统设计展开的后端综合面试", commonScenarioItems());

        private final String code;
        private final String title;
        private final String focus;
        private final List<InterviewPlanItem> items;

        Scenario(String code, String title, String focus, List<InterviewPlanItem> items) {
            this.code = code;
            this.title = title;
            this.focus = focus;
            this.items = items;
        }

        static Scenario fromCode(String code) {
            for (Scenario scenario : values()) {
                if (scenario.code.equals(code)) {
                    return scenario;
                }
            }
            return null;
        }

        private static List<InterviewPlanItem> commonScenarioItems() {
            return List.of(
                    item("project", "项目深挖", "项目背景、个人职责和技术取舍", "项目介绍", 1, "请介绍一个你参与度较高的项目，并说明你本人负责的核心部分。"),
                    item("java", "Java 基础", "集合、并发和异常处理", "基础原理", 2, "在你的项目中，哪些 Java 并发问题最容易出现？你会怎样保证代码安全？"),
                    item("mysql", "MySQL", "索引、事务和慢查询", "工程实践", 2, "如果订单或业务记录表数据量快速增长，你会怎样设计索引和查询？"),
                    item("redis", "Redis", "缓存、锁和一致性", "工程实践", 2, "如果一个热点数据同时被大量读取和更新，你会如何设计缓存一致性方案？"),
                    item("trade", "交易流程", "下单、库存和状态机", "系统设计", 3, "请设计一个下单流程，说明库存扣减、订单状态和重复请求如何处理。"),
                    item("message", "消息可靠性", "异步解耦与重复消费", "系统设计", 3, "订单状态变化需要通知多个下游系统，你会如何使用消息队列并保证可靠性？"),
                    item("idempotency", "接口幂等", "回调重试与业务唯一键", "故障治理", 2, "支付回调可能重复到达时，你会如何设计幂等处理和异常补偿？"),
                    item("high_concurrency", "高并发", "限流、热点和扩容", "系统设计", 3, "活动开始时流量瞬间增长十倍，你会如何保护入口、缓存、数据库和下游服务？"),
                    item("fault", "故障排查", "延迟、错误率和线程池", "故障排查", 3, "线上接口 P99 延迟突然升高但错误率不高，你会按什么顺序排查？"),
                    item("delivery", "复杂场景", "状态一致与超时重试", "场景设计", 3, "配送或异步任务长时间没有结果时，系统如何避免重复执行并支持补偿？"),
                    item("security", "系统安全", "鉴权、越权与敏感数据", "工程实践", 2, "后端系统如何防止用户访问不属于自己的订单、面试记录或报告？"),
                    item("tradeoff", "综合权衡", "可用性、成本与一致性", "开放讨论", 3, "请讲一次你在性能、稳定性、成本和开发复杂度之间做取舍的经历。")
            );
        }
    }

    private static InterviewPlanItem item(String moduleCode, String moduleName, String skill,
                                           String questionType, int difficulty, String questionText) {
        return new InterviewPlanItem(0, moduleCode, moduleName, skill, questionType, difficulty, questionText, 1);
    }

    private List<InterviewPlanItem> renumber(List<InterviewPlanItem> items) {
        List<InterviewPlanItem> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            InterviewPlanItem item = items.get(i);
            result.add(new InterviewPlanItem(
                    i + 1, item.moduleCode(), item.moduleName(), item.skill(), item.questionType(),
                    item.difficulty(), item.questionText(), item.maxFollowUp()
            ));
        }
        return result;
    }

    private void shuffle(List<InterviewPlanItem> items) {
        Collections.shuffle(items, new Random(UUID.randomUUID().getMostSignificantBits()));
    }

    private int targetCount(CreateInterviewDTO dto) {
        Integer count = dto.getTargetQuestionCount();
        return count == null ? 8 : Math.max(5, Math.min(12, count));
    }

    private String defaultMode(String mode) {
        return mode == null || mode.isBlank() ? InterviewMode.DIRECTION.getCode() : mode;
    }
}
