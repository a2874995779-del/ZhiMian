export type InterviewDirectionCode = 'java_concurrency' | 'jvm' | 'mysql' | 'redis' | 'system_design'

export interface DirectionOption {
  code: InterviewDirectionCode
  label: string
  focus: string
}

// 和后端 InterviewDirection 枚举一一对应
export const DIRECTION_OPTIONS: DirectionOption[] = [
  { code: 'java_concurrency', label: 'Java 并发', focus: '线程池、锁机制、CAS、AQS、并发容器' },
  { code: 'jvm', label: 'JVM', focus: '内存区域、垃圾回收、类加载机制' },
  { code: 'mysql', label: 'MySQL', focus: '索引原理、事务隔离级别、锁机制' },
  { code: 'redis', label: 'Redis', focus: '数据结构、持久化、缓存三大问题、分布式锁' },
  { code: 'system_design', label: '系统设计', focus: '高并发架构、限流降级、一致性权衡' },
]

export interface InterviewSessionVO {
  id: number
  direction: InterviewDirectionCode
  openingMessage: string
}

// 后端 chat 接口现在是 SSE 流,不再是一次性的 JSON 回复,事件类型对应 InterviewServiceImpl.sendEvent 发的三种 payload
export interface ChatStreamEvent {
  type: 'delta' | 'done' | 'error'
  content?: string
  messageId?: number
  message?: string
}

// 前端本地维护的对话轮次,role 对应后端 InterviewMessage 的 role
export interface ChatTurn {
  role: 'assistant' | 'user'
  content: string
  pending?: boolean // 用户已提交、AI 还没回复时的占位态
}

// 会话状态,和后端 interview_session.status 一一对应
export type InterviewStatus = 0 | 1 | 2

export const STATUS_META: Record<InterviewStatus, { label: string; tone: 'active' | 'done' | 'graded' }> = {
  0: { label: '进行中', tone: 'active' },
  1: { label: '已结束', tone: 'done' },
  2: { label: '已评价', tone: 'graded' },
}

// list/detail 接口返回的 direction 是 code(java_concurrency…),展示时要转成中文名
export function directionLabel(code: string): string {
  return DIRECTION_OPTIONS.find((o) => o.code === code)?.label ?? code
}

// POST /interviews/{id}/finish 返回的结构化评价报告
export interface InterviewReportVO {
  score: number
  highlights: string[]
  weaknesses: string[]
  summary: string
  createTime: string
}

// GET /interviews 列表项
export interface InterviewSessionListVO {
  id: number
  direction: string
  title: string
  status: InterviewStatus
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
  title: string
  status: InterviewStatus
  createTime: string
  endTime: string | null
  messages: InterviewMessageVO[]
}
