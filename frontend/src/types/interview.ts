export type InterviewDirectionCode = 'spring' | 'java_concurrency' | 'jvm' | 'mysql' | 'redis' | 'system_design' | 'rag'
export type InterviewMode = 'direction' | 'scenario'

export interface DirectionOption {
  code: InterviewDirectionCode
  label: string
  focus: string
  corePlanLabels?: string[]
}

export interface ScenarioOption {
  code: string
  label: string
  focus: string
  modules: string[]
  corePlanLabels?: string[]
  references?: { title: string; url: string; publishedAt: string }[]
}

// 和后端 InterviewDirection 枚举一一对应
export const DIRECTION_OPTIONS: DirectionOption[] = [
  { code: 'spring', label: 'Spring 框架', focus: 'IoC、Bean 生命周期、AOP、事务失效、Boot 自动配置与 MVC',
    corePlanLabels: ['IoC 与依赖注入', 'AOP 代理', '事务与失效', 'Boot 自动配置', 'MVC 链路'] },
  { code: 'java_concurrency', label: 'Java 并发', focus: '线程池、锁机制、CAS、AQS、并发容器' },
  { code: 'jvm', label: 'JVM', focus: '内存区域、垃圾回收、类加载机制' },
  { code: 'mysql', label: 'MySQL', focus: '索引原理、事务隔离级别、锁机制' },
  { code: 'redis', label: 'Redis', focus: '数据结构、持久化、缓存三大问题、分布式锁' },
  { code: 'system_design', label: '系统设计', focus: '高并发架构、限流降级、一致性权衡' },
  { code: 'rag', label: 'RAG 检索增强', focus: 'Embedding、向量检索、知识库导入、切片与 Prompt 增强' },
]

export const SCENARIO_OPTIONS: ScenarioOption[] = [
  {
    code: 'alibaba_style_backend',
    label: '阿里风格后端实习面试（模拟）',
    focus: 'Spring、数据库、缓存、商品库存、并发与性能排查',
    modules: ['项目深挖', 'Spring', '商品查询', '商品缓存', '库存竞争', '线程池', '故障排查'],
    corePlanLabels: ['项目深挖', 'Spring 实践', '商品查询', '商品缓存', '电商库存'],
    references: [
      { title: '阿里云 Java 研发实习一面', publishedAt: '2025-04-25', url: 'https://www.nowcoder.com/discuss/743798386737897472?sourceSSR=dynamic' },
      { title: '阿里云实习二面（回顾 2023 年经历）', publishedAt: '2024-03-01', url: 'https://www.nowcoder.com/discuss/591740699846926336' },
    ],
  },
  {
    code: 'ant_style_backend',
    label: '蚂蚁集团（蚂蚁金服）风格后端实习面试（模拟）',
    focus: '事务、支付幂等、流水核对、余额并发与系统稳定性',
    modules: ['项目深挖', 'Spring 事务', '金额与流水', '幂等缓存', '结果确认', '数据核对', '稳定性'],
    corePlanLabels: ['项目深挖', 'Spring 事务', '金额与流水', '支付幂等', '支付结果确认'],
    references: [
      { title: '蚂蚁后端面经（意向）', publishedAt: '2025-10-13', url: 'https://www.nowcoder.com/discuss/807221949330894848' },
    ],
  },
  {
    code: 'jd_style_backend',
    label: '京东风格后端实习面试（模拟）',
    focus: '促销策略、仓库库存、履约拆单、AOP、线程池与监控',
    modules: ['项目深挖', 'Spring 策略', '仓库库存', 'Lua 预占', '履约拆单', '自定义注解', '监控'],
    corePlanLabels: ['项目深挖', '促销策略注入', '仓库库存', '库存预占', '履约拆单'],
    references: [
      { title: '京东零售 JDY Java 实习一面', publishedAt: '2025-08-15', url: 'https://ac.nowcoder.com/discuss/1535507' },
    ],
  },
  {
    code: 'meituan_style_backend',
    label: '美团风格后端实习面试（模拟）',
    focus: '订单、库存、配送、流量治理、稳定性与系统设计',
    modules: ['项目深挖', '订单状态机', '库存与优惠', '支付回调', '配送调度', '高峰流量', '故障排查'],
  },
  {
    code: 'tencent_style_backend',
    label: '腾讯风格后端实习面试（模拟）',
    focus: 'Java 基础、内容社交、高并发与架构权衡',
    modules: ['项目深挖', 'JVM 与并发', 'Feed 流', '社交关系', '消息通知', '实时通信', '架构权衡'],
  },
  {
    code: 'xiaohongshu_style_backend',
    label: '小红书风格后端实习面试（模拟）',
    focus: '内容发布、Feed 流、搜索、互动、审核与推荐基础',
    modules: ['项目深挖', '内容发布', 'Feed 流', '搜索', '点赞评论', '内容审核', '热点治理'],
  },
]

export interface InterviewSessionVO {
  id: number
  direction: InterviewDirectionCode | 'scenario'
  mode: InterviewMode
  scenarioCode: string | null
  title: string
  openingMessage: string
  targetQuestionCount: number
  answeredQuestionCount: number
}

// 后端 chat 接口返回 SSE 流，processing 表示文本已生成、评分和保存仍在进行。
export interface ChatStreamEvent {
  type: 'delta' | 'processing' | 'done' | 'error'
  content?: string
  messageId?: number
  message?: string
  finished?: boolean
  answeredCount?: number
  targetCount?: number
}

export interface ChatDoneEvent {
  messageId: number
  finished: boolean
  answeredCount: number
  targetCount: number
}

// 前端本地维护的对话轮次,role 对应后端 InterviewMessage 的 role
export interface ChatTurn {
  role: 'assistant' | 'user'
  content: string
  pending?: boolean // 用户已提交、AI 还没回复时的占位态
}

// 会话状态,和后端 interview_session.status 一一对应
export type InterviewStatus = 0 | 1 | 2
export type InterviewFinishReason = 'AUTO_LIMIT' | 'USER_STOP' | 'SYSTEM_ERROR' | 'INACTIVITY_TIMEOUT' | null

export const STATUS_META: Record<InterviewStatus, { label: string; tone: 'active' | 'done' | 'graded' }> = {
  0: { label: '进行中', tone: 'active' },
  1: { label: '已结束', tone: 'done' },
  2: { label: '已评价', tone: 'graded' },
}

// list/detail 接口返回的 direction 是 code(java_concurrency…),展示时要转成中文名
export function directionLabel(code: string): string {
  return DIRECTION_OPTIONS.find((o) => o.code === code)?.label
    ?? SCENARIO_OPTIONS.find((o) => o.code === code)?.label
    ?? (code === 'scenario' ? '综合场景面试' : code)
}

// POST /interviews/{id}/finish 返回的结构化评价报告
export interface InterviewReportVO {
  score: number
  highlights: string[]
  weaknesses: string[]
  summary: string
  createTime: string
}

export type ReportStatus = 0 | 1 | 2

export interface InterviewReportStatusVO {
  status: ReportStatus
  report?: InterviewReportVO
  message?: string
}

// GET /interviews 列表项
export interface InterviewSessionListVO {
  id: number
  direction: string
  mode: InterviewMode
  scenarioCode: string | null
  title: string
  status: InterviewStatus
  targetQuestionCount: number
  answeredQuestionCount: number
  finishReason: InterviewFinishReason
  createTime: string
  endTime: string | null
}

// GET /interviews/{id} 详情里的单条消息(system 消息后端已过滤,不会出现在这里)
export interface InterviewMessageVO {
  role: 'assistant' | 'user'
  content: string
  createTime: string
}

// GET /interviews/{id} 详情
export interface InterviewSessionDetailVO {
  id: number
  direction: string
  mode: InterviewMode
  scenarioCode: string | null
  title: string
  status: InterviewStatus
  targetQuestionCount: number
  answeredQuestionCount: number
  finishReason: InterviewFinishReason
  createTime: string
  endTime: string | null
  messages: InterviewMessageVO[]
}
