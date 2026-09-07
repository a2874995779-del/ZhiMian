import { get, post } from './http'
import type { PageResult } from './question'
import type {
  ChatStreamEvent,
  ChatDoneEvent,
  InterviewDirectionCode,
  InterviewReportStatusVO,
  InterviewSessionDetailVO,
  InterviewSessionListVO,
  InterviewSessionVO,
} from '../types/interview'

// 这两个接口会在后端同步触发 1~2 次大模型调用(生成开场白 / 生成评价报告),耗时远超 http.ts 的全局 10s 超时。
// 若不单独放宽,axios 会在 10s 就 abort、前端弹"网络异常",而后端其实还在生成、会话锁还占着
// (再点会显示"会话正在处理中",刷新后又发现报告已生成)。给它们一个足够长的超时,等真正的结果。
const AI_CALL_TIMEOUT = 120000

export function createInterview(direction: InterviewDirectionCode, targetQuestionCount = 8): Promise<InterviewSessionVO> {
  return post<InterviewSessionVO>('/interviews', { direction, targetQuestionCount })
}

// 结束面试:后端会加载整场对话让模型生成一份结构化评价报告并返回。
// 对已评价(status=2)的会话是幂等的——直接返回已存报告,不再重新调模型,所以也能拿来"查看历史报告"。
export function finishInterview(sessionId: number): Promise<InterviewReportStatusVO> {
  return post<InterviewReportStatusVO>(`/interviews/${sessionId}/finish`, null, { timeout: AI_CALL_TIMEOUT })
}

export function getReportStatus(sessionId: number): Promise<InterviewReportStatusVO> {
  return get<InterviewReportStatusVO>(`/interviews/${sessionId}/report`)
}

export function listInterviews(pageNum: number, pageSize: number): Promise<PageResult<InterviewSessionListVO>> {
  return get<PageResult<InterviewSessionListVO>>('/interviews', { params: { pageNum, pageSize } })
}

export function getInterviewDetail(id: number): Promise<InterviewSessionDetailVO> {
  return get<InterviewSessionDetailVO>(`/interviews/${id}`)
}

export interface ChatStreamHandlers {
  onDelta: (content: string) => void
  onDone: (event: ChatDoneEvent) => void
  onError: (message: string) => void
}

// chat 接口现在返回的是 SSE 流(text/event-stream),不是一次性的 JSON 回复,不能再走 http.ts 那套
// axios + {code,data} 解包的路子——用 fetch 手动读流,按 delta/done/error 三种事件分发给调用方。
// 这个函数本身不 reject:任何失败(同步业务错误、网络异常)都归一走 handlers.onError,调用方只用管一条路径。
export async function chatInterviewStream(
  sessionId: number,
  content: string,
  handlers: ChatStreamHandlers,
): Promise<void> {
  let terminalReceived = false
  try {
    const token = localStorage.getItem('zm-token')
    const resp = await fetch(`/api/interviews/${sessionId}/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ content }),
    })

    const contentType = resp.headers.get('content-type') ?? ''
    if (!contentType.includes('text/event-stream')) {
      // 请求在同步阶段就被拒绝了(会话不存在/已结束/上一轮还没处理完/未登录……),
      // 走的是全局异常处理器返回的普通 {code,message} JSON,不是 SSE
      const body = await resp.json().catch(() => null)
      // 未登录/登录过期:这条流式请求没走 http.ts 的 axios 拦截器,得在这里补上同一套登出逻辑,
      // 否则 token 失效了界面还一直显示"已登录"。动态 import 绕开与 stores/auth 的循环依赖(同 http.ts)。
      if (body?.code === 40100 || body?.code === 40101) {
        import('../stores/auth').then(({ useAuthStore }) => useAuthStore().logout())
      }
      handlers.onError(body?.message || '请求失败')
      terminalReceived = true
      return
    }
    if (!resp.body) {
      handlers.onError('当前浏览器不支持读取流式响应')
      terminalReceived = true
      return
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    const dispatchFrame = (rawFrame: string) => {
      const dataLine = rawFrame.split(/\r?\n/).find((line) => line.startsWith('data:'))
      if (!dataLine) return
      const payload: ChatStreamEvent = JSON.parse(dataLine.slice(5).trim())
      if (payload.type === 'delta') {
        handlers.onDelta(payload.content ?? '')
      } else if (payload.type === 'done') {
        terminalReceived = true
        handlers.onDone({
          messageId: payload.messageId ?? 0,
          finished: payload.finished ?? false,
          answeredCount: payload.answeredCount ?? 0,
          targetCount: payload.targetCount ?? 8,
        })
      } else if (payload.type === 'error') {
        terminalReceived = true
        handlers.onError(payload.message ?? 'AI 服务异常，请稍后重试')
      }
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // 同时兼容 LF 与 CRLF；未读完整的帧继续留在 buffer 中。
      let match = /\r?\n\r?\n/.exec(buffer)
      while (match?.index !== undefined) {
        const rawFrame = buffer.slice(0, match.index)
        buffer = buffer.slice(match.index + match[0].length)
        dispatchFrame(rawFrame)
        match = /\r?\n\r?\n/.exec(buffer)
      }
    }
    buffer += decoder.decode()
    if (buffer.trim()) dispatchFrame(buffer)
    if (!terminalReceived) {
      terminalReceived = true
      handlers.onError('连接已中断，请重新提交本轮回答')
    }
  } catch {
    if (!terminalReceived) {
      terminalReceived = true
      handlers.onError('网络异常，请确认后端服务已启动')
    }
  }
}
