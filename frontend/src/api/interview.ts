import http from './http'
import type { PageResult } from './question'
import type {
  ChatStreamEvent,
  InterviewDirectionCode,
  InterviewReportVO,
  InterviewSessionDetailVO,
  InterviewSessionListVO,
  InterviewSessionVO,
} from '../types/interview'

export function createInterview(direction: InterviewDirectionCode): Promise<InterviewSessionVO> {
  return http.post('/interviews', { direction }) as unknown as Promise<InterviewSessionVO>
}

// 结束面试:后端会加载整场对话让模型生成一份结构化评价报告并返回。
// 对已评价(status=2)的会话是幂等的——直接返回已存报告,不再重新调模型,所以也能拿来"查看历史报告"。
export function finishInterview(sessionId: number): Promise<InterviewReportVO> {
  return http.post(`/interviews/${sessionId}/finish`) as unknown as Promise<InterviewReportVO>
}

export function listInterviews(pageNum: number, pageSize: number): Promise<PageResult<InterviewSessionListVO>> {
  return http.get('/interviews', { params: { pageNum, pageSize } }) as unknown as Promise<
    PageResult<InterviewSessionListVO>
  >
}

export function getInterviewDetail(id: number): Promise<InterviewSessionDetailVO> {
  return http.get(`/interviews/${id}`) as unknown as Promise<InterviewSessionDetailVO>
}

export interface ChatStreamHandlers {
  onDelta: (content: string) => void
  onDone: (messageId: number) => void
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
      return
    }
    if (!resp.body) {
      handlers.onError('当前浏览器不支持读取流式响应')
      return
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // SSE 帧以空行分隔,一次 read 可能带回不止一帧,也可能一帧被拆成两次 read——按 \n\n 切,切不出来的留在 buffer 里等下一次
      let separatorIndex
      while ((separatorIndex = buffer.indexOf('\n\n')) !== -1) {
        const rawFrame = buffer.slice(0, separatorIndex)
        buffer = buffer.slice(separatorIndex + 2)
        const dataLine = rawFrame.split('\n').find((line) => line.startsWith('data:'))
        if (!dataLine) {
          continue
        }
        const payload: ChatStreamEvent = JSON.parse(dataLine.slice(5).trim())
        if (payload.type === 'delta') {
          handlers.onDelta(payload.content ?? '')
        } else if (payload.type === 'done') {
          handlers.onDone(payload.messageId ?? 0)
        } else if (payload.type === 'error') {
          handlers.onError(payload.message ?? 'AI 服务异常，请稍后重试')
        }
      }
    }
  } catch {
    handlers.onError('网络异常，请确认后端服务已启动')
  }
}
