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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
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
        List<InterviewPlanItem> items = selectScenarioItems(scenario, targetCount);
        return new InterviewPlan(
                InterviewMode.SCENARIO.getCode(),
                scenario.code,
                scenario.title,
                scenario.focus,
                renumber(items.subList(0, Math.min(targetCount, items.size())))
        );
    }

    /**
     * 综合面试保留项目开场，后续从不同模块随机抽题，避免变成固定题单或连续问同一类问题。
     */
    private List<InterviewPlanItem> selectScenarioItems(
            Scenario scenario,
            int targetCount
    ) {
        List<InterviewPlanItem> candidates = new ArrayList<>(scenario.items);
        InterviewPlanItem opening = candidates.stream()
                .filter(item -> "project".equals(item.moduleCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("综合面试缺少项目开场题"));
        candidates.remove(opening);
        shuffle(candidates);

        List<InterviewPlanItem> selected = new ArrayList<>();
        Set<String> selectedModules = new HashSet<>();
        selected.add(opening);
        selectedModules.add(opening.moduleCode());

        // 第一轮优先保证模块多样性，让 8 道题覆盖基础、业务、并发和故障等不同能力。
        for (InterviewPlanItem candidate : candidates) {
            if (selected.size() >= targetCount) {
                break;
            }
            if (selectedModules.add(candidate.moduleCode())) {
                selected.add(candidate);
            }
        }

        // 题库扩展后如果出现同模块多题，再用剩余题目补足目标数量。
        if (selected.size() < targetCount) {
            for (InterviewPlanItem candidate : candidates) {
                if (selected.size() >= targetCount) {
                    break;
                }
                if (!selected.contains(candidate)) {
                    selected.add(candidate);
                }
            }
        }
        return selected;
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
            case "rag" -> List.of(
                    item("rag_basic", "RAG 基础", "RAG 与微调、普通问答的区别", "基础原理", 1, "RAG 解决什么问题？它和直接调用 ChatModel、模型微调有什么区别？"),
                    item("embedding", "Embedding", "文本向量与语义相似度", "基础原理", 1, "Embedding 模型的输出是什么？为什么两个语义相近的问题可能得到相近的向量？"),
                    item("chunk", "文档切片", "chunk 大小与 overlap", "工程实践", 2, "知识文档为什么要切成多个 chunk？chunk 太大、太小和没有 overlap 分别有什么问题？"),
                    item("ingestion", "知识导入", "清洗、切片、向量化流程", "系统流程", 1, "请描述一篇 Markdown 从上传到进入向量库的完整流程，并说明每一步的职责。"),
                    item("vector_store", "向量数据库", "向量索引与 metadata", "基础原理", 2, "VectorStore 保存哪些内容？metadata 为什么不能完全替代 MySQL 中的原始知识？"),
                    item("retrieval", "语义检索", "TopK 与相似度阈值", "检索设计", 2, "TopK 和 similarity threshold 分别控制什么？阈值过高或过低会带来什么影响？"),
                    item("hybrid", "检索优化", "向量检索与关键词检索", "架构权衡", 2, "只使用向量检索可能遇到什么问题？什么场景适合增加关键词检索或重排？"),
                    item("prompt", "Prompt 增强", "参考资料与系统指令隔离", "安全设计", 2, "怎样把检索资料放进 Prompt，同时防止资料中的文字被模型误认为系统指令？"),
                    item("citation", "引用溯源", "来源、章节与相似度", "工程实践", 1, "为什么 RAG 回答最好返回文档和章节引用？引用信息应该来自哪里？"),
                    item("evaluation", "检索评估", "Hit@K、MRR 与生成质量", "测试评估", 2, "如何判断一个 RAG 检索效果好不好？为什么不能只凭最终回答是否通顺来判断？"),
                    item("consistency", "数据一致性", "MySQL 与向量库最终一致", "可靠性", 2, "MySQL 保存原文、Redis 保存向量时，如何处理向量写入失败、重复导入和删除？"),
                    item("fallback", "故障降级", "超时、重试与无知识库回答", "可靠性", 2, "Embedding 或 Redis 暂时不可用时，错题讲解和面试评价应该怎样降级？"),
                    item("security", "RAG 安全", "Prompt Injection 与权限隔离", "安全设计", 2, "知识库资料和用户回答都可能包含恶意指令，系统应如何隔离、过滤和限制影响？"),
                    item("context", "上下文管理", "Token 预算与资料筛选", "工程实践", 2, "检索到很多片段时，为什么不能全部放进 Prompt？你会怎样控制上下文长度？"),
                    item("project", "项目实践", "RAG 模块设计与复盘", "综合实践", 2, "请结合智面项目，说明你负责的 RAG 模块、遇到的问题和一次具体的取舍。")
            );
            default -> List.of(item("general", "项目实践", "技术方案表达", "综合实践", 1, "请介绍一个你最熟悉的技术点，并说明它在项目中的使用场景。"));
        };
    }

    private enum Scenario {
        MEITUAN(
                "meituan_style_backend",
                "美团风格后端实习面试（模拟）",
                "围绕订单、库存、配送、流量治理和稳定性展开的实习生综合面试",
                meituanScenarioItems()
        ),
        TENCENT(
                "tencent_style_backend",
                "腾讯风格后端实习面试（模拟）",
                "围绕 Java 基础、内容社交、高并发和架构权衡展开的实习生综合面试",
                tencentScenarioItems()
        ),
        XIAOHONGSHU(
                "xiaohongshu_style_backend",
                "小红书风格后端实习面试（模拟）",
                "围绕内容发布、Feed 流、搜索、互动和推荐基础展开的实习生综合面试",
                xiaohongshuScenarioItems()
        );

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

        private static List<InterviewPlanItem> meituanScenarioItems() {
            return List.of(
                    item("project", "项目深挖", "项目背景、个人职责和技术取舍", "项目介绍", 1, "请介绍一个你参与度较高的项目，并说明一次你亲自解决的线上或并发问题。", 2),
                    item("requirement", "业务建模", "需求澄清与核心链路", "项目深挖", 1, "如果让你负责一个本地生活订单模块，你会先向产品和业务确认哪些关键问题？", 2),
                    item("order_state", "订单状态机", "状态流转与非法操作", "系统设计", 2, "请设计外卖订单从待支付、已支付、商家接单到完成的状态机，如何防止状态乱跳？", 2),
                    item("inventory", "库存扣减", "超卖、预扣与补偿", "高并发设计", 3, "高峰期多个用户同时抢最后一份库存，你会如何避免超卖，并处理支付失败后的库存释放？", 2),
                    item("promotion", "优惠与定价", "价格快照与规则一致", "业务设计", 2, "优惠券、满减和配送费同时生效时，订单金额如何计算并保证支付金额可追溯？", 2),
                    item("mysql_order", "订单数据库", "索引、分库与历史归档", "工程实践", 2, "订单表达到数亿行后，用户查最近订单和运营查商家订单分别应该如何设计索引？", 2),
                    item("redis_hot", "热点缓存", "热点商品与缓存保护", "故障治理", 2, "热门商家和商品在午餐高峰被大量读取时，如何防止缓存击穿把数据库打垮？", 2),
                    item("payment", "支付回调", "幂等、乱序与对账", "故障治理", 3, "支付成功回调重复或晚于订单取消回调到达时，你会如何设计状态校验和对账补偿？", 2),
                    item("message", "消息可靠性", "本地消息表与重复消费", "系统设计", 3, "订单状态需要通知库存、配送和消息中心，你如何保证消息不丢、可重试且消费者幂等？", 2),
                    item("delivery", "配送调度", "骑手匹配与任务状态", "场景设计", 3, "配送任务分配给骑手后长时间无人接单，如何设计超时重派并避免两个骑手同时执行？", 2),
                    item("location", "位置服务", "实时位置与历史轨迹", "架构权衡", 2, "骑手位置需要实时展示，哪些数据放 Redis，哪些数据异步落 MySQL？如何控制写入压力？", 2),
                    item("traffic", "高峰流量", "限流、排队与降级", "系统设计", 3, "节假日流量突然增长十倍，你会如何保护入口、订单服务、数据库和第三方支付？", 2),
                    item("delay_close", "延迟任务", "超时取消与可靠执行", "工程实践", 2, "如何实现十五分钟未支付自动取消订单？定时任务重复执行或服务重启时怎么办？", 2),
                    item("fault", "故障排查", "P99、线程池与下游", "故障排查", 3, "订单接口 P99 突然升高但错误率不高，你会按什么顺序判断是数据库、Redis、线程池还是下游变慢？", 2),
                    item("observability", "稳定性建设", "指标、日志与链路追踪", "工程实践", 2, "你会为下单链路设计哪些核心指标，怎样区分用户慢、数据库慢和第三方支付慢？", 1),
                    item("security", "业务安全", "越权、刷单与敏感数据", "工程实践", 2, "如何防止用户查看别人的订单、伪造价格或重复领取优惠券？", 2),
                    item("consistency", "最终一致性", "跨服务状态补偿", "架构权衡", 3, "订单已支付但库存服务不可用时，系统如何保证最终一致，并让用户得到可解释的状态？", 2),
                    item("tradeoff", "综合权衡", "可用性、成本与一致性", "开放讨论", 3, "在实时性、数据一致性、系统成本和开发复杂度冲突时，你会怎样做方案取舍？", 2)
            );
        }

        private static List<InterviewPlanItem> tencentScenarioItems() {
            return List.of(
                    item("project", "项目深挖", "项目背景、核心难点和技术取舍", "项目介绍", 1, "请介绍一个你最熟悉的项目，重点说明架构演进、你的核心贡献以及一次失败复盘。", 2),
                    item("java_depth", "Java 基础深挖", "集合、并发与对象模型", "原理追问", 2, "如果一个 Java 服务的共享 Map 在高并发下出现数据异常，你会从底层结构和并发访问两方面怎么分析？", 2),
                    item("jvm_latency", "JVM 性能", "GC、线程与延迟", "故障排查", 3, "服务吞吐没有明显下降，但 P99 延迟周期性升高，你如何判断是 GC、线程阻塞还是下游抖动？", 2),
                    item("concurrency", "并发模型", "线程池、异步和背压", "架构设计", 2, "一个内容处理接口既有 CPU 计算又有网络 IO，你会如何拆分线程池并防止任务无限堆积？", 2),
                    item("feed", "Feed 流", "推模式、拉模式与热点", "系统设计", 3, "设计一个关注流，普通用户和拥有百万粉丝的大 V 分别适合推模式还是拉模式？为什么？", 2),
                    item("social_graph", "社交关系", "关注、共同关系与存储", "数据建模", 2, "如何设计关注关系、共同关注和粉丝列表？Redis 与 MySQL 分别承担什么职责？", 2),
                    item("like", "点赞互动", "幂等计数与高并发", "工程实践", 2, "点赞接口如何防止重复点赞？点赞数使用 Redis 聚合时，最终如何保证和明细数据一致？", 2),
                    item("content_storage", "内容存储", "冷热分层与大字段", "架构权衡", 2, "内容正文、图片、评论和互动数据如何拆分存储？为什么不建议把所有字段放在一张大表？", 2),
                    item("hot_content", "热点内容", "热点 Key 与读扩散", "高并发设计", 3, "一条热点内容在短时间被数百万用户访问，你会如何设计缓存、限流和热点失效保护？", 2),
                    item("notification", "消息通知", "站内信、未读数与可靠性", "系统设计", 2, "点赞、评论和关注都需要通知用户，如何保证通知不重复，未读数又能快速查询？", 2),
                    item("message_order", "消息顺序", "分区、顺序消费与重试", "消息设计", 3, "同一个用户的多条事件需要保持顺序，但不同用户可以并行消费，你会怎样设计消息分区？", 2),
                    item("long_connection", "实时通信", "SSE、WebSocket 与连接治理", "架构对比", 2, "聊天或实时通知场景什么时候选择 SSE，什么时候选择 WebSocket？大量连接如何治理？", 2),
                    item("search", "内容检索", "倒排索引与异步构建", "系统设计", 2, "发布内容后如何让用户尽快搜索到？索引构建失败或删除内容时如何补偿？", 2),
                    item("distributed_id", "分布式 ID", "趋势有序与时钟问题", "基础设计", 2, "多节点生成内容和消息 ID 时，如何保证唯一性？如果需要趋势递增，还要考虑什么问题？", 2),
                    item("sharding", "数据扩展", "分库分表与热点分片", "架构权衡", 3, "用户和内容数据持续增长时，如何选择分片键？热点用户或大 V 数据倾斜怎么解决？", 2),
                    item("service_governance", "服务治理", "限流、熔断与隔离", "稳定性设计", 3, "推荐服务或消息服务故障时，主链路如何继续工作？如何避免故障扩散？", 2),
                    item("consistency", "一致性权衡", "实时、最终一致与用户体验", "开放讨论", 2, "点赞数短时间不准确但明细正确，是否可以接受？你会如何向产品解释并设计修正机制？", 2),
                    item("observability", "可观测性", "指标、日志与 Trace", "故障排查", 2, "线上某个接口只有部分用户变慢，你会如何结合用户、分片、机房和链路信息定位？", 2),
                    item("security", "平台安全", "权限、风控与隐私", "工程实践", 2, "内容平台如何防止越权读取、恶意刷互动和敏感信息泄露？", 2),
                    item("tradeoff", "综合权衡", "性能、成本与架构演进", "开放讨论", 3, "面对一个高并发系统，你如何判断应该继续优化单体，还是拆分服务和引入更多中间件？", 2)
            );
        }

        private static List<InterviewPlanItem> xiaohongshuScenarioItems() {
            return List.of(
                    item("project", "项目深挖", "项目背景、个人贡献和技术取舍", "项目介绍", 1, "请介绍一个你参与度较高的项目，并重点说明你负责的功能、遇到的问题和最后的改进。", 2),
                    item("content_publish", "内容发布", "草稿、发布和可见状态", "业务建模", 1, "设计一个笔记发布流程，草稿、发布、审核和删除分别应该有哪些状态？如何避免重复发布？", 2),
                    item("content_storage", "内容存储", "正文、图片与扩展字段", "数据建模", 1, "笔记正文、图片地址、话题和可见范围如何存储？为什么图片文件不应该直接放进 MySQL？", 2),
                    item("feed", "首页 Feed", "关注流与推荐流的基本实现", "系统设计", 2, "如果要实现一个简单首页 Feed，你会如何组织用户关注的内容，并处理分页和重复内容？", 2),
                    item("feed_pagination", "Feed 分页", "游标分页与时间线稳定", "工程实践", 2, "Feed 数据不断新增时，为什么 offset 分页可能出现重复或漏数据？你会如何设计游标分页？", 2),
                    item("recommend_cache", "推荐缓存", "热点内容与缓存保护", "高并发设计", 2, "一篇热门笔记突然被大量访问，你会缓存哪些数据？如何避免缓存失效时请求同时打到数据库？", 2),
                    item("search", "内容搜索", "关键词、索引与异步更新", "系统设计", 2, "用户发布笔记后希望很快能搜索到，你会如何设计数据库写入和搜索索引更新？", 2),
                    item("tag", "话题标签", "标签关系与热门排序", "数据建模", 1, "笔记可以关联多个话题，如何设计笔记和话题的关系？热门话题的数量如何统计？", 2),
                    item("like", "点赞收藏", "幂等、计数与明细", "工程实践", 2, "点赞和收藏接口如何保证幂等？点赞数放缓存时，明细数据和计数如何保持最终一致？", 2),
                    item("comment", "评论系统", "楼中楼与敏感内容处理", "业务设计", 2, "如何设计评论和回复的数据结构？评论内容需要审核时，发布链路如何让用户体验不被阻塞？", 2),
                    item("notification", "互动通知", "未读数与消息可靠性", "系统设计", 2, "被点赞、评论或关注后需要通知用户，如何设计通知记录和未读数？重复事件怎么办？", 2),
                    item("hot_key", "热点问题", "热点 Key 与读扩散", "故障治理", 2, "某个明星相关话题突然成为热点，怎样保护 Redis、数据库和搜索服务？", 2),
                    item("image_service", "图片服务", "上传、压缩与 CDN", "架构权衡", 1, "图片上传后如何进行大小限制、压缩和 CDN 分发？后端接口如何避免被大文件拖垮？", 2),
                    item("moderation", "内容审核", "同步审核与异步审核", "可靠性", 2, "笔记发布需要审核时，哪些内容可以同步判断，哪些适合异步审核？审核失败如何重试？", 2),
                    item("rate_limit", "接口保护", "刷赞、刷评论与限流", "安全设计", 2, "如何防止用户短时间大量刷赞或刷评论？限流、幂等和风控分别解决什么问题？", 2),
                    item("observability", "线上排查", "接口延迟与链路定位", "故障排查", 2, "首页 Feed 只有部分用户加载很慢，你会如何结合日志、指标和请求参数定位？", 2),
                    item("consistency", "数据一致性", "删除、审核与索引同步", "架构权衡", 2, "用户删除笔记后，缓存、搜索索引和推荐结果可能短暂存在，你会如何处理最终一致？", 2),
                    item("tradeoff", "综合权衡", "功能、性能与开发成本", "开放讨论", 2, "实习项目时间有限时，你会如何在先做可用版本、缓存优化和复杂推荐之间排序？", 2)
            );
        }
    }

    private static InterviewPlanItem item(String moduleCode, String moduleName, String skill,
                                           String questionType, int difficulty, String questionText) {
        return item(moduleCode, moduleName, skill, questionType, difficulty, questionText, 1);
    }

    private static InterviewPlanItem item(String moduleCode, String moduleName, String skill,
                                           String questionType, int difficulty, String questionText,
                                           int maxFollowUp) {
        return new InterviewPlanItem(0, moduleCode, moduleName, skill, questionType, difficulty, questionText, maxFollowUp);
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
