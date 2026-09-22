package com.zhimian.model.enums;

public enum InterviewDirection {
    JAVA_CONCURRENCY("java_concurrency", "Java 并发", "线程池、锁机制、CAS、AQS、并发容器、线程协作工具"),
    JVM("jvm", "JVM", "内存区域划分、垃圾回收算法与收集器、类加载机制、常见调优场景"),
    MYSQL("mysql", "MySQL", "索引原理、事务隔离级别与 MVCC、锁机制、SQL 优化与索引失效场景"),
    REDIS("redis", "Redis", "常用数据结构与使用场景、持久化机制、缓存穿透雪崩击穿、分布式锁"),
    SYSTEM_DESIGN("system_design", "系统设计", "高并发架构设计思路、限流降级、缓存与数据库一致性、常见系统的设计权衡"),
    RAG("rag", "RAG 检索增强", "Embedding、向量检索、知识库导入、切片策略、Prompt 增强与 RAG 可靠性");
    private final String code;
    private final String label;
    private final String focus;

    InterviewDirection(String code, String label, String focus) {
        this.code = code;
        this.label = label;
        this.focus = focus;
    }

    public String getCode(){
        return code;
    }
    public String getLabel() {
        return label;
    }

    public String getFocus() {
        return focus;
    }

    public static InterviewDirection fromCode(String code){
        for(InterviewDirection d : values()){
            if(d.code.equals(code)){
                return d;
            }
        }
        return null;
    }
}
