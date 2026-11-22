<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, CircleCheck, Expand, Fold, Promotion } from '@element-plus/icons-vue'
import ExaminerAvatar from '../components/interview/ExaminerAvatar.vue'
import ReportCard from '../components/interview/ReportCard.vue'
import LoginDialog from '../components/layout/LoginDialog.vue'
import { useAuthStore } from '../stores/auth'
import {
  chatInterviewStream,
  createInterview,
  finishInterview,
  getCurrentInterview,
  getInterviewDetail,
  getReportStatus,
} from '../api/interview'
import { DIRECTION_OPTIONS, SCENARIO_OPTIONS } from '../types/interview'
import type {
  ChatTurn,
  DirectionOption,
  InterviewMode,
  InterviewReportVO,
  ReportStatus,
  ScenarioOption,
  InterviewSessionDetailVO,
} from '../types/interview'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const loginDialogVisible = ref(false)

const phase = ref<'picking' | 'chatting' | 'generating-report' | 'report'>('picking')
const creating = ref(false)
const restoring = ref(false)
const finishing = ref(false)
const reportLoading = ref(false)
const activeDirection = ref<DirectionOption | ScenarioOption | null>(null)
const interviewMode = ref<InterviewMode>('direction')
const sessionId = ref<number | null>(null)
const transcript = ref<ChatTurn[]>([])
const answer = ref('')
const selectedQuestionCount = ref(8)
const targetQuestionCount = ref(8)
const answeredQuestionCount = ref(0)
const waitingReply = ref(false)
const processingReply = ref(false)
const queuedAnswer = ref<string | null>(null)
const finishAfterReply = ref(false)
const report = ref<InterviewReportVO | null>(null)
const reportMessage = ref('')
const lastReportStatus = ref<ReportStatus | null>(null)
const transcriptEl = ref<HTMLElement | null>(null)
const planExpanded = ref(window.matchMedia('(min-width: 1200px)').matches)
const planToggleEl = ref<HTMLButtonElement | null>(null)
let reportTimer: number | null = null
let queuedAnswerTimer: number | null = null
const CHAT_RATE_WINDOW_MS = 11000
let lastChatStartedAt = 0

function clearQueuedAnswerTimer() {
  if (queuedAnswerTimer !== null) {
    window.clearTimeout(queuedAnswerTimer)
    queuedAnswerTimer = null
  }
}

function sendQueuedAnswer(id: number, startedAt: number) {
  clearQueuedAnswerTimer()
  const delay = Math.max(0, CHAT_RATE_WINDOW_MS - (Date.now() - startedAt))
  queuedAnswerTimer = window.setTimeout(() => {
    queuedAnswerTimer = null
    if (sessionId.value !== id || phase.value !== 'chatting' || queuedAnswer.value === null) return
    answer.value = queuedAnswer.value
    queuedAnswer.value = null
    void submitAnswer()
  }, delay)
}

function closePlan() {
  planExpanded.value = false
  planToggleEl.value?.focus()
}

// 至少答过一轮(有一条用户消息)才让结束——否则一进来就结束,报告没内容可评
const canFinish = computed(() => transcript.value.some((t) => t.role === 'user'))
const hasNextQuestion = computed(() => answeredQuestionCount.value + 1 < targetQuestionCount.value)

// 用户向上翻看历史时，流式输出不应把视图强行拽回底部；只有贴近底部或主动操作时才跟随
const atBottom = ref(true)

function onTranscriptScroll() {
  const el = transcriptEl.value
  if (!el) return
  atBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

// force=false：流式输出时只在用户本来就停留在底部时跟随；smooth=false：恢复会话时直接定位，避免平滑滚动被中途打断
async function scrollToBottom(force = true, smooth = true) {
  if (!force && !atBottom.value) return
  await nextTick()
  const el = transcriptEl.value
  if (!el) return
  el.scrollTo({ top: el.scrollHeight, behavior: force && smooth ? 'smooth' : 'auto' })
  atBottom.value = true
}

function parseSessionId(value: unknown): number | null {
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
}

function directionOptionOf(code: string): DirectionOption | ScenarioOption | null {
  return DIRECTION_OPTIONS.find((option) => option.code === code)
    ?? SCENARIO_OPTIONS.find((option) => option.code === code)
    ?? null
}

async function applySessionDetail(detail: InterviewSessionDetailVO) {
  clearQueuedAnswerTimer()
  if (detail.finishReason === 'INACTIVITY_TIMEOUT') {
    localStorage.removeItem('zm-active-interview-id')
    phase.value = 'picking'
    sessionId.value = null
    transcript.value = []
    await router.replace({ path: '/interview' })
    ElMessage.info('上一场面试因长时间未操作已自动结束，未生成评价报告')
    return
  }
  interviewMode.value = detail.mode ?? 'direction'
  activeDirection.value = detail.mode === 'scenario'
    ? SCENARIO_OPTIONS.find((option) => option.code === detail.scenarioCode) ?? null
    : directionOptionOf(detail.direction)
  sessionId.value = detail.id
  targetQuestionCount.value = detail.targetQuestionCount
  answeredQuestionCount.value = detail.answeredQuestionCount
  waitingReply.value = false
  processingReply.value = false
  queuedAnswer.value = null
  finishAfterReply.value = false
  transcript.value = detail.messages.map((message) => ({
    role: message.role,
    content: message.content,
  }))
  localStorage.setItem('zm-active-interview-id', String(detail.id))
  if (parseSessionId(route.query.sessionId) !== detail.id) {
    await router.replace({ path: '/interview', query: { sessionId: detail.id } })
  }

  if (detail.status === 0) {
    phase.value = 'chatting'
    report.value = null
    reportMessage.value = ''
    lastReportStatus.value = null
  } else {
    phase.value = 'generating-report'
    startReportPolling(detail.id)
  }
  void scrollToBottom(true, false)
}

async function restoreSession(id: number) {
  if (!auth.token || restoring.value) {
    return
  }
  restoring.value = true
  try {
    const detail = await getInterviewDetail(id)
    await applySessionDetail(detail)
  } finally {
    restoring.value = false
  }
}

async function restoreCurrentSession() {
  if (!auth.token || restoring.value) {
    return
  }
  restoring.value = true
  try {
    const detail = await getCurrentInterview()
    if (detail) {
      await applySessionDetail(detail)
      return
    }
    localStorage.removeItem('zm-active-interview-id')
    phase.value = 'picking'
    sessionId.value = null
    transcript.value = []
  } finally {
    restoring.value = false
  }
}

function restoreEntrySession() {
  const queryId = parseSessionId(route.query.sessionId)
  return queryId ? restoreSession(queryId) : restoreCurrentSession()
}

onMounted(() => {
  void restoreEntrySession()
})

watch(
  () => auth.token,
  (token) => {
    if (!token) {
      stopReportPolling()
      return
    }
    void restoreEntrySession()
  },
)

async function pickOption(option: DirectionOption | ScenarioOption) {
  if (!auth.token) {
    loginDialogVisible.value = true
    return
  }
  creating.value = true
  try {
    const selection = 'modules' in option
      ? { mode: 'scenario' as const, scenarioCode: option.code }
      : { mode: 'direction' as const, direction: option.code }
    const session = await createInterview(selection, selectedQuestionCount.value)
    activeDirection.value = option
    interviewMode.value = selection.mode
    sessionId.value = session.id
    targetQuestionCount.value = session.targetQuestionCount
    answeredQuestionCount.value = session.answeredQuestionCount
    transcript.value = [{ role: 'assistant', content: session.openingMessage }]
    localStorage.setItem('zm-active-interview-id', String(session.id))
    router.replace({ path: '/interview', query: { sessionId: session.id } })
    phase.value = 'chatting'
    scrollToBottom()
  } finally {
    creating.value = false
  }
}

async function submitAnswer() {
  const content = answer.value.trim()
  if (!content || sessionId.value === null || finishAfterReply.value || (queuedAnswer.value !== null && !waitingReply.value)) {
    return
  }
  if (waitingReply.value) {
    if (!hasNextQuestion.value) {
      ElMessage.info('当前是最后一题，完成后将生成报告')
      return
    }
    if (queuedAnswer.value === null) {
      queuedAnswer.value = content
      answer.value = ''
      ElMessage.info('回答已排队，本轮保存完成后自动发送')
    }
    return
  }
  if (lastChatStartedAt > 0 && Date.now() - lastChatStartedAt < CHAT_RATE_WINDOW_MS) {
    queuedAnswer.value = content
    answer.value = ''
    sendQueuedAnswer(sessionId.value, lastChatStartedAt)
    ElMessage.info('回答已排队，稍后自动发送')
    return
  }
  // 记下这次请求归属的会话:如果用户在等回复的过程中点了"重新开始",sessionId 会变,
  // 下面几个回调不能再假设自己面对的还是同一场会话,不然旧会话的回复会串进新会话的对话框里
  const requestSessionId = sessionId.value
  const requestStartedAt = Date.now()
  lastChatStartedAt = requestStartedAt

  transcript.value.push({ role: 'user', content })
  answer.value = ''
  waitingReply.value = true
  processingReply.value = false
  scrollToBottom()

  // 先占一个空的 assistant 气泡,delta 事件到达时往里追加文字,打字机效果。
  // 注意:push 进响应式数组后,数组里存的是这个对象的「响应式代理」;必须把代理引用取回来再改,
  // 直接改 push 之前的原始对象不会触发视图更新(delta 收到了但页面气泡不刷新——打字机效果失效)。
  transcript.value.push({ role: 'assistant', content: '' })
  const assistantTurn = transcript.value[transcript.value.length - 1]

  await chatInterviewStream(requestSessionId, content, {
    onDelta: (delta) => {
      if (sessionId.value !== requestSessionId) return
      assistantTurn.content += delta
      scrollToBottom(false)
    },
    onProcessing: () => {
      if (sessionId.value !== requestSessionId) return
      processingReply.value = true
    },
    onDone: (event) => {
      if (sessionId.value !== requestSessionId) return
      answeredQuestionCount.value = event.answeredCount
      waitingReply.value = false
      processingReply.value = false
      if (event.finished) {
        clearQueuedAnswerTimer()
        finishAfterReply.value = false
        queuedAnswer.value = null
        targetQuestionCount.value = event.targetCount
        phase.value = 'generating-report'
        reportMessage.value = '已完成全部题目，正在生成评价报告'
        startReportPolling(requestSessionId)
      } else if (finishAfterReply.value) {
        finishAfterReply.value = false
        queuedAnswer.value = null
        void finishCurrentSession()
      } else if (queuedAnswer.value !== null) {
        sendQueuedAnswer(requestSessionId, requestStartedAt)
      }
    },
    onError: (message) => {
      if (sessionId.value !== requestSessionId) return
      processingReply.value = false
      finishAfterReply.value = false
      clearQueuedAnswerTimer()
      if (queuedAnswer.value !== null) {
        answer.value = queuedAnswer.value
        queuedAnswer.value = null
      }
      // 一个字都没收到就失败:去掉这个空气泡,把刚才那句用户消息标成失败态,而不是留一个空白的 AI 回复
      if (!assistantTurn.content) {
        const idx = transcript.value.indexOf(assistantTurn)
        if (idx !== -1) {
          transcript.value.splice(idx, 1)
        }
        const lastTurn = transcript.value.at(-1)
        if (lastTurn && lastTurn.role === 'user') {
          lastTurn.pending = true
        }
      } else {
        assistantTurn.pending = true
      }
      if (message.includes('长时间未操作')) {
        startNew()
      }
      ElMessage.error(message)
      waitingReply.value = false
    },
  })
  if (sessionId.value === requestSessionId) {
    scrollToBottom(false)
  }
}

async function endInterview() {
  if (sessionId.value === null || finishing.value) {
    return
  }
  // 结束面试 = 让后端生成评价报告(顺带把会话状态从"进行中"推走,不再占用并发名额)。
  // 后端对"报告已生成"的会话是幂等的,万一这次生成失败(50001),会话停在"已结束未评价",
  // 用户可以再点一次重试,所以这里失败就停在当前对话页,http.ts 已经弹过错误提示了。
  if (phase.value === 'chatting') {
    try {
      await ElMessageBox.confirm(
        waitingReply.value
          ? '本轮回答正在处理，保存完成后将自动结束并生成报告，确定吗？'
          : `当前待回答题目不会计分，本场已完成 ${answeredQuestionCount.value} 题，确定结束吗？`,
        '结束面试',
        { confirmButtonText: '结束并生成评价', cancelButtonText: '继续回答', type: 'warning' },
      )
    } catch {
      return
    }
  }
  if (waitingReply.value) {
    finishAfterReply.value = true
    queuedAnswer.value = null
    ElMessage.info('本轮保存完成后将自动结束面试')
    return
  }
  clearQueuedAnswerTimer()
  queuedAnswer.value = null
  await finishCurrentSession()
}

async function finishCurrentSession() {
  if (sessionId.value === null || finishing.value) return
  finishing.value = true
  try {
    const status = await finishInterview(sessionId.value)
    lastReportStatus.value = status.status
    reportMessage.value = status.message || ''
    if (status.status === 1 && status.report) {
      report.value = status.report
      phase.value = 'report'
      localStorage.removeItem('zm-active-interview-id')
      stopReportPolling()
    } else {
      phase.value = 'generating-report'
      startReportPolling(sessionId.value)
    }
  } catch {
    // 已由 http.ts 拦截器统一提示,这里不重复弹
  } finally {
    finishing.value = false
  }
}

async function pollReport(id: number) {
  reportLoading.value = true
  try {
    const status = await getReportStatus(id)
    lastReportStatus.value = status.status
    reportMessage.value = status.message || ''
    if (status.status === 1 && status.report) {
      report.value = status.report
      phase.value = 'report'
      localStorage.removeItem('zm-active-interview-id')
      stopReportPolling()
    } else if (status.status === 2) {
      phase.value = 'generating-report'
      localStorage.removeItem('zm-active-interview-id')
      stopReportPolling()
    }
  } finally {
    reportLoading.value = false
  }
}

function startReportPolling(id: number) {
  stopReportPolling()
  void pollReport(id)
  reportTimer = window.setInterval(() => {
    void pollReport(id)
  }, 2000)
}

function stopReportPolling() {
  if (reportTimer !== null) {
    window.clearInterval(reportTimer)
    reportTimer = null
  }
}

onUnmounted(() => {
  stopReportPolling()
  clearQueuedAnswerTimer()
})

function startNew() {
  stopReportPolling()
  clearQueuedAnswerTimer()
  phase.value = 'picking'
  activeDirection.value = null
  interviewMode.value = 'direction'
  sessionId.value = null
  transcript.value = []
  answer.value = ''
  report.value = null
  reportMessage.value = ''
  lastReportStatus.value = null
  targetQuestionCount.value = selectedQuestionCount.value
  answeredQuestionCount.value = 0
  waitingReply.value = false
  lastChatStartedAt = 0
  processingReply.value = false
  queuedAnswer.value = null
  finishAfterReply.value = false
  localStorage.removeItem('zm-active-interview-id')
  router.replace({ path: '/interview' })
}

const examinerStatus = computed(() => processingReply.value
  ? '正在评分并保存本轮…'
  : waitingReply.value ? '面试官正在思考…' : '轮到你回答了')

const examinerState = computed<'idle' | 'thinking' | 'processing'>(() => processingReply.value
  ? 'processing'
  : waitingReply.value ? 'thinking' : 'idle')

const currentQuestionNo = computed(() => Math.min(answeredQuestionCount.value + 1, targetQuestionCount.value))
const modeLabel = computed(() => interviewMode.value === 'scenario' ? '场景面试' : '专项面试')
const userInitial = computed(() => (auth.user?.nickname || auth.user?.username || '我').slice(0, 1).toUpperCase())
const composerDisabled = computed(() => !!queuedAnswer.value || finishAfterReply.value)
const canSend = computed(() => !!answer.value.trim() && !composerDisabled.value && !(waitingReply.value && !hasNextQuestion.value))

const LENGTH_OPTIONS = [
  { value: 5, title: '快速热身', desc: '覆盖核心考点' },
  { value: 8, title: '标准面试', desc: '贴近真实一面' },
  { value: 12, title: '深度挑战', desc: '追问更多细节' },
]

const DIRECTION_BADGES: Record<string, string> = {
  spring: 'SP',
  java_concurrency: 'JUC',
  jvm: 'JVM',
  mysql: 'SQL',
  redis: 'RDS',
  system_design: 'SD',
  rag: 'RAG',
}

function scenarioBadge(option: ScenarioOption) {
  return option.label.slice(0, 1)
}

const overallProgress = computed(() => {
  if (!targetQuestionCount.value) return 0
  return Math.min(100, Math.round((answeredQuestionCount.value / targetQuestionCount.value) * 100))
})

const planSteps = computed(() => {
  const option = activeDirection.value
  const coreLabels = option?.corePlanLabels ?? (option && 'modules' in option ? ['项目深挖'] : [])
  return Array.from({ length: targetQuestionCount.value }, (_, index) => ({
    label: coreLabels[index] ?? `第 ${index + 1} 题`,
    completed: index < answeredQuestionCount.value,
    active: phase.value === 'chatting' && index === answeredQuestionCount.value,
  }))
})
</script>

<template>
  <div
    class="cabin"
    :class="{ 'cabin--chatting': auth.token && phase === 'chatting' }"
    v-loading="restoring"
  >
    <!-- 未登录 -->
    <section v-if="!auth.token" class="state-card">
      <img class="state-avatar" src="/ai-interviewer.png" alt="" />
      <h2>登录后开始模拟面试</h2>
      <p>面试记录、评价报告都会与账号绑定，方便你随时回顾。</p>
      <el-button type="primary" size="large" @click="loginDialogVisible = true">去登录</el-button>
    </section>

    <!-- 选方向 -->
    <div v-else-if="phase === 'picking'" class="picker">
      <section class="hero">
        <div class="hero-copy">
          <span class="hero-badge">AI 模拟面试</span>
          <h2>像真实面试一样练习，每一轮都有反馈</h2>
          <p>选择面试类型与题量，AI 面试官会根据你的回答持续追问，结束后生成结构化评价报告。</p>
          <ul class="hero-points">
            <li><el-icon><CircleCheck /></el-icon>多轮追问</li>
            <li><el-icon><CircleCheck /></el-icon>逐题评分</li>
            <li><el-icon><CircleCheck /></el-icon>进度自动保存</li>
          </ul>
        </div>
        <img class="hero-avatar" src="/ai-interviewer.png" alt="AI 面试官" />
      </section>

      <section class="setup-block">
        <h3 class="setup-title"><span class="setup-step">1</span>选择面试类型</h3>
        <div class="mode-switcher" role="tablist" aria-label="选择面试模式">
          <button
            class="mode-tab"
            :class="{ 'mode-tab--active': interviewMode === 'direction' }"
            type="button"
            role="tab"
            :aria-selected="interviewMode === 'direction'"
            @click="interviewMode = 'direction'"
          >
            <strong>专项面试</strong>
            <small>集中练习单一技术方向，查漏补缺</small>
          </button>
          <button
            class="mode-tab"
            :class="{ 'mode-tab--active': interviewMode === 'scenario' }"
            type="button"
            role="tab"
            :aria-selected="interviewMode === 'scenario'"
            @click="interviewMode = 'scenario'"
          >
            <strong>场景面试</strong>
            <small>模拟大厂综合考察路线，贴近实战</small>
          </button>
        </div>
      </section>

      <section class="setup-block">
        <h3 class="setup-title"><span class="setup-step">2</span>选择面试长度</h3>
        <div class="length-grid" role="radiogroup" aria-label="面试长度">
          <button
            v-for="item in LENGTH_OPTIONS"
            :key="item.value"
            class="length-card"
            :class="{ 'length-card--active': selectedQuestionCount === item.value }"
            type="button"
            role="radio"
            :aria-checked="selectedQuestionCount === item.value"
            @click="selectedQuestionCount = item.value"
          >
            <span class="length-count">{{ item.value }}<small> 题</small></span>
            <span class="length-copy">
              <strong>{{ item.title }}</strong>
              <small>{{ item.desc }}</small>
            </span>
          </button>
        </div>
      </section>

      <section class="setup-block">
        <h3 class="setup-title">
          <span class="setup-step">3</span>{{ interviewMode === 'direction' ? '选择技术方向' : '选择面试场景' }}
          <span class="setup-hint">点击卡片即可开始</span>
        </h3>
        <div v-if="interviewMode === 'direction'" class="option-grid" v-loading="creating">
          <button
            v-for="option in DIRECTION_OPTIONS"
            :key="option.code"
            class="option-card"
            type="button"
            :disabled="creating"
            @click="pickOption(option)"
          >
            <span class="option-badge">{{ DIRECTION_BADGES[option.code] ?? option.label.slice(0, 2) }}</span>
            <span class="option-body">
              <strong>{{ option.label }}</strong>
              <small>{{ option.focus }}</small>
            </span>
            <span class="option-start">开始<el-icon><ArrowRight /></el-icon></span>
          </button>
        </div>
        <div v-else class="option-grid option-grid--scenario" v-loading="creating">
          <button
            v-for="option in SCENARIO_OPTIONS"
            :key="option.code"
            class="option-card"
            type="button"
            :disabled="creating"
            @click="pickOption(option)"
          >
            <span class="option-badge option-badge--scenario">{{ scenarioBadge(option) }}</span>
            <span class="option-body">
              <strong>{{ option.label }}</strong>
              <small>{{ option.focus }}</small>
              <span class="scenario-modules">
                <span v-for="module in option.modules" :key="module" class="module-chip">{{ module }}</span>
              </span>
            </span>
            <span class="option-start">开始<el-icon><ArrowRight /></el-icon></span>
          </button>
        </div>
        <details v-if="interviewMode === 'scenario'" class="scenario-references">
          <summary>公开面经参考与改编说明</summary>
          <p>来源是候选人的个人复盘。题目按实习与校招难度改编，业务场景为训练设计，不代表企业官方题库或统一面试流程。</p>
          <template v-for="option in SCENARIO_OPTIONS" :key="option.code">
            <div v-if="option.references?.length" class="reference-company">
              <h4>{{ option.label }}</h4>
              <ul>
                <li v-for="reference in option.references" :key="reference.url">
                  <a :href="reference.url" target="_blank" rel="noopener noreferrer">{{ reference.title }}</a>
                  <time>{{ reference.publishedAt }}</time>
                </li>
              </ul>
            </div>
          </template>
        </details>
      </section>
    </div>

    <template v-else-if="phase === 'generating-report'">
      <section class="state-card">
        <div v-if="lastReportStatus !== 2" class="state-spinner" aria-hidden="true"></div>
        <h2>{{ lastReportStatus === 2 ? '评价报告生成失败' : '评价报告生成中' }}</h2>
        <p>{{ reportMessage || 'AI 评委正在整理本场面试表现，请稍候。' }}</p>
        <div class="report-actions">
          <el-button v-if="sessionId" type="primary" :loading="finishing || reportLoading" @click="endInterview">
            {{ lastReportStatus === 2 ? '重新生成' : '刷新状态' }}
          </el-button>
          <el-button @click="startNew">再来一场</el-button>
        </div>
      </section>
    </template>

    <!-- 评价报告 -->
    <template v-else-if="phase === 'report' && report">
      <div class="report-wrap">
        <section class="report-head">
          <div class="report-head-copy">
            <span class="hero-badge">面试评价</span>
            <h2 class="report-title">{{ activeDirection?.label || '综合面试' }}</h2>
            <p class="report-sub">{{ modeLabel }} · 共 {{ targetQuestionCount }} 题</p>
          </div>
          <el-button type="primary" size="large" @click="startNew">再来一场</el-button>
        </section>
        <section class="report-body">
          <ReportCard :report="report" />
        </section>
      </div>
    </template>

    <!-- 对话中 -->
    <template v-else>
      <div class="interview-page" @keydown.esc="closePlan">
        <header class="interview-header">
          <div class="interview-heading">
            <h2 class="interview-title" :title="activeDirection?.label || '综合面试'">{{ activeDirection?.label || '综合面试' }}</h2>
            <div class="interview-tags">
              <span class="meta-chip">{{ modeLabel }}</span>
              <span class="meta-chip meta-chip--accent">第 {{ currentQuestionNo }} / {{ targetQuestionCount }} 题</span>
              <span class="meta-chip meta-chip--live"><span class="live-dot"></span>进行中</span>
            </div>
          </div>
          <div class="interview-actions">
            <el-tooltip :content="planExpanded ? '收起面试计划' : '展开面试计划'" placement="bottom" effect="light">
              <span class="plan-toggle-wrap">
                <button
                  ref="planToggleEl"
                  class="icon-button"
                  type="button"
                  :aria-label="planExpanded ? '收起面试计划' : '展开面试计划'"
                  :aria-expanded="planExpanded"
                  aria-controls="interview-plan"
                  @click="planExpanded = !planExpanded"
                >
                  <el-icon><component :is="planExpanded ? Fold : Expand" /></el-icon>
                </button>
              </span>
            </el-tooltip>
            <el-button
              class="header-finish-btn"
              type="primary"
              :loading="finishing"
              :disabled="!canFinish || finishAfterReply"
              @click="endInterview"
            >
              <el-icon v-if="!finishing"><CircleCheck /></el-icon>
              <span class="finish-text">{{ finishAfterReply ? '本轮结束后生成报告' : '结束并生成报告' }}</span>
              <span class="finish-text-short">{{ finishAfterReply ? '待结束' : '结束' }}</span>
            </el-button>
          </div>
          <div
            class="header-progress"
            role="progressbar"
            aria-label="面试进度"
            :aria-valuenow="overallProgress"
            aria-valuemin="0"
            aria-valuemax="100"
          >
            <span class="header-progress-bar" :style="{ width: `${overallProgress}%` }"></span>
          </div>
        </header>

        <section class="interview-frame" :class="{ 'interview-frame--plan-open': planExpanded }">
          <div class="conversation-column">
            <div ref="transcriptEl" class="transcript" @scroll.passive="onTranscriptScroll">
              <div class="transcript-inner">
                <TransitionGroup name="zm-list">
                  <div
                    v-for="(turn, i) in transcript"
                    :key="i"
                    class="turn"
                    :class="turn.role === 'user' ? 'turn--user' : 'turn--assistant'"
                  >
                    <ExaminerAvatar
                      v-if="turn.role === 'assistant'"
                      :speaking="waitingReply && i === transcript.length - 1"
                      compact
                    />
                    <span v-else class="user-avatar" aria-hidden="true">{{ userInitial }}</span>
                    <div class="turn-body">
                      <div class="turn-meta">{{ turn.role === 'user' ? '我' : 'AI 面试官' }}</div>
                      <div class="bubble" :class="{ 'bubble--failed': turn.pending }">
                        <template v-if="turn.content">{{ turn.content }}</template>
                        <span v-else-if="waitingReply && i === transcript.length - 1" class="bubble--typing" aria-label="面试官正在回复">
                          <span class="dot"></span><span class="dot"></span><span class="dot"></span>
                        </span>
                        <span v-if="processingReply && i === transcript.length - 1" class="bubble-processing">正在评分并保存本轮…</span>
                        <span v-if="turn.pending" class="fail-hint">{{ turn.role === 'user' ? '发送失败' : '回复未保存，请刷新会话' }}</span>
                      </div>
                    </div>
                  </div>
                </TransitionGroup>
              </div>
            </div>

            <div class="composer-wrap">
              <Transition name="zm-fade">
                <button v-if="!atBottom" class="jump-latest" type="button" @click="scrollToBottom()">回到最新消息 ↓</button>
              </Transition>
              <div class="composer" :class="{ 'composer--disabled': composerDisabled }">
                <el-input
                  v-model="answer"
                  type="textarea"
                  :autosize="{ minRows: 3, maxRows: 10 }"
                  :disabled="composerDisabled"
                  resize="none"
                  :placeholder="composerDisabled ? '回答已排队，本轮完成后自动发送…' : '在这里输入你的回答，建议先给结论，再展开原理并结合项目经验…'"
                  @keydown.ctrl.enter.prevent="submitAnswer"
                  @keydown.meta.enter.prevent="submitAnswer"
                />
                <div class="composer-footer">
                  <div class="composer-status">
                    <span class="status-dot" :class="`status-dot--${examinerState}`"></span>
                    <span class="status-text">{{ examinerStatus }}</span>
                    <span v-if="queuedAnswer" class="queued-chip">下一条回答已排队</span>
                  </div>
                  <div class="composer-actions">
                    <span class="composer-hint"><kbd>Ctrl</kbd> + <kbd>Enter</kbd> 发送</span>
                    <span v-if="answer.length" class="char-count">{{ answer.length }} 字</span>
                    <el-button type="primary" :disabled="!canSend" @click="submitAnswer">
                      <el-icon><Promotion /></el-icon>
                      <span>{{ waitingReply ? '排队发送' : '发送' }}</span>
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <button v-if="planExpanded" class="plan-backdrop" type="button" tabindex="-1" aria-label="关闭面试计划" @click="closePlan"></button>
          <aside v-show="planExpanded" id="interview-plan" class="plan-card" aria-label="面试计划">
            <div class="plan-heading">
              <h3>面试计划</h3>
              <button class="icon-button icon-button--ghost" type="button" aria-label="收起面试计划" @click="closePlan">
                <el-icon><Fold /></el-icon>
              </button>
            </div>
            <div class="plan-summary">
              <el-progress type="circle" :percentage="overallProgress" :width="64" :stroke-width="6" />
              <div class="plan-summary-copy">
                <strong>已完成 {{ answeredQuestionCount }} / {{ targetQuestionCount }} 题</strong>
                <span>回答后自动进入下一题</span>
              </div>
            </div>
            <ol class="plan-list">
              <li
                v-for="(step, index) in planSteps"
                :key="index"
                class="plan-row"
                :class="{ 'plan-row--active': step.active, 'plan-row--done': step.completed }"
              >
                <span class="plan-row-icon">
                  <el-icon v-if="step.completed"><CircleCheck /></el-icon>
                  <span v-else>{{ index + 1 }}</span>
                </span>
                <span class="plan-row-label" :title="step.label">{{ step.label }}</span>
                <small class="plan-row-state">{{ step.completed ? '已完成' : step.active ? '进行中' : '待进行' }}</small>
              </li>
            </ol>
            <div class="plan-footer">
              <p class="plan-tip-title">答题小贴士</p>
              <ul>
                <li>先给结论，再展开原理与细节</li>
                <li>结合项目经历说明取舍和结果</li>
              </ul>
              <small>进度自动保存；长时间无操作将自动结束且不生成报告</small>
            </div>
          </aside>
        </section>
      </div>
    </template>

    <LoginDialog v-model="loginDialogVisible" />
  </div>
</template>

<style scoped>
/* ============ 通用 ============ */
.cabin {
  max-width: 1120px;
  margin: 0 auto;
}

.cabin--chatting {
  width: 100%;
  max-width: none;
  height: 100%;
  min-height: 0;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: var(--zm-radius-pill);
  background: var(--zm-accent-soft);
  color: var(--zm-accent);
  font-size: 12px;
  font-weight: 650;
}

/* ============ 状态卡(未登录 / 生成报告) ============ */
.state-card {
  max-width: 480px;
  margin: 8vh auto 0;
  padding: 48px 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  text-align: center;
  border: 1px solid var(--zm-border);
  border-radius: var(--zm-radius-lg);
  background: var(--zm-bg-elevated);
  box-shadow: var(--zm-shadow-md);
}

.state-card h2 {
  font-size: 20px;
}

.state-card p {
  font-size: 14px;
  line-height: 1.7;
  color: var(--zm-ink-soft);
}

.state-avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 8px 20px rgba(10, 132, 255, 0.18);
}

.state-spinner {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 3px solid var(--zm-accent-soft);
  border-top-color: var(--zm-accent);
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.report-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 6px;
}

/* ============ 选择页 ============ */
.picker {
  display: flex;
  flex-direction: column;
  gap: 28px;
  padding-bottom: 12px;
}

.hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  overflow: hidden;
  border: 1px solid rgba(10, 132, 255, 0.14);
  border-radius: var(--zm-radius-lg);
  background:
    radial-gradient(circle at 92% 20%, rgba(10, 132, 255, 0.16), transparent 42%),
    linear-gradient(135deg, var(--zm-bg-elevated) 0%, rgba(10, 132, 255, 0.06) 100%);
}

.hero-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}

.hero-copy h2 {
  font-size: 24px;
  line-height: 1.35;
}

.hero-copy p {
  max-width: 560px;
  color: var(--zm-ink-soft);
  font-size: 14px;
  line-height: 1.75;
}

.hero-points {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin: 4px 0 0;
  padding: 0;
  list-style: none;
  color: var(--zm-ink-soft);
  font-size: 13px;
}

.hero-points li {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.hero-points .el-icon {
  color: var(--zm-green);
}

.hero-avatar {
  width: 112px;
  height: 112px;
  flex-shrink: 0;
  border-radius: 50%;
  object-fit: cover;
  border: 4px solid var(--zm-bg-elevated);
  box-shadow: 0 12px 30px rgba(10, 132, 255, 0.2);
}

.setup-block {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.setup-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 16px;
}

.setup-step {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--zm-accent);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.setup-hint {
  margin-left: auto;
  color: var(--zm-ink-faint);
  font-size: 12px;
  font-weight: 500;
}

.mode-switcher {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.mode-tab,
.length-card,
.option-card {
  border: 1px solid var(--zm-border);
  border-radius: var(--zm-radius-md);
  background: var(--zm-bg-elevated);
  color: var(--zm-ink);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--zm-dur-fast) var(--zm-ease), box-shadow var(--zm-dur-fast) var(--zm-ease),
    transform var(--zm-dur-fast) var(--zm-ease), background var(--zm-dur-fast) var(--zm-ease);
}

.mode-tab:hover,
.length-card:hover {
  border-color: var(--zm-border-strong);
}

.mode-tab {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 18px;
}

.mode-tab strong {
  font-size: 15px;
}

.mode-tab small {
  color: var(--zm-ink-faint);
  font-size: 12px;
}

.mode-tab--active,
.length-card--active {
  border-color: var(--zm-accent);
  background: var(--zm-accent-soft);
  box-shadow: 0 0 0 3px rgba(10, 132, 255, 0.08);
}

.mode-tab--active strong,
.length-card--active .length-count {
  color: var(--zm-accent);
}

.length-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.length-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
}

.length-count {
  flex-shrink: 0;
  font-size: 26px;
  font-weight: 750;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.length-count small {
  font-size: 12px;
  font-weight: 600;
  color: var(--zm-ink-faint);
}

.length-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.length-copy strong {
  font-size: 14px;
}

.length-copy small {
  color: var(--zm-ink-faint);
  font-size: 12px;
}

.option-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
  min-height: 120px;
}

.option-grid--scenario {
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
}

.option-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 18px 18px 16px;
}

.option-card:hover:not(:disabled) {
  transform: translateY(-2px);
  border-color: rgba(10, 132, 255, 0.4);
  box-shadow: var(--zm-shadow-md);
}

.option-card:disabled {
  cursor: progress;
  opacity: 0.7;
}

.option-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 12px;
  background: var(--zm-accent-soft);
  color: var(--zm-accent);
  font-family: var(--zm-font-mono);
  font-size: 12px;
  font-weight: 750;
}

.option-badge--scenario {
  background: rgba(255, 159, 10, 0.12);
  color: #d97706;
  font-family: var(--zm-font-display);
  font-size: 18px;
}

.option-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding-bottom: 22px;
}

.option-body strong {
  font-size: 15px;
  line-height: 1.4;
}

.option-body small {
  color: var(--zm-ink-soft);
  font-size: 12.5px;
  line-height: 1.65;
}

.option-start {
  position: absolute;
  right: 16px;
  bottom: 12px;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--zm-accent);
  font-size: 12px;
  font-weight: 650;
  opacity: 0.55;
  transition: opacity var(--zm-dur-fast) var(--zm-ease), transform var(--zm-dur-fast) var(--zm-ease);
}

.option-card:hover .option-start {
  opacity: 1;
  transform: translateX(2px);
}

.scenario-modules {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.module-chip {
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--zm-bg);
  color: var(--zm-ink-soft);
  font-size: 11.5px;
  line-height: 1.6;
}

.scenario-references {
  margin-top: 6px;
  padding: 14px 16px;
  border: 1px solid var(--zm-border);
  border-radius: var(--zm-radius-md);
  background: var(--zm-bg-elevated);
  color: var(--zm-ink-soft);
  font-size: 13px;
  line-height: 1.7;
}
.scenario-references summary { cursor: pointer; color: var(--zm-ink); font-weight: 600; }
.scenario-references p { margin: 12px 0; }
.reference-company h4 { margin: 14px 0 6px; font-size: 13px; }
.reference-company ul { margin: 0; padding-left: 20px; }
.reference-company a { color: var(--zm-accent); overflow-wrap: anywhere; }
.reference-company time { display: inline-block; margin-left: 12px; color: var(--zm-ink-faint); }

/* ============ 报告页 ============ */
.report-wrap {
  max-width: 968px;
  margin: 0 auto;
  /* 带 sessionId 的路由下 AppShell 会去掉内容区内边距，这里自行留白 */
  padding: 28px 24px 40px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.report-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  padding: 24px 28px;
  border: 1px solid rgba(10, 132, 255, 0.14);
  border-radius: var(--zm-radius-lg);
  background: linear-gradient(135deg, var(--zm-bg-elevated) 0%, rgba(10, 132, 255, 0.06) 100%);
}

.report-head-copy {
  min-width: 0;
}

.report-title {
  margin-top: 10px;
  font-size: 22px;
  line-height: 1.4;
}

.report-sub {
  margin-top: 4px;
  color: var(--zm-ink-faint);
  font-size: 13px;
}

.report-body {
  padding: 28px;
  border: 1px solid var(--zm-border);
  border-radius: var(--zm-radius-lg);
  background: var(--zm-bg-elevated);
  box-shadow: var(--zm-shadow-sm);
}

/* ============ 面试中:整体布局 ============ */
/* 视口高度固定，只有对话区与计划列表内部滚动 */
.interview-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--zm-bg);
}

.interview-header {
  position: relative;
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 24px;
  border-bottom: 1px solid var(--zm-border);
  background: var(--zm-bg-elevated);
}

.interview-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.interview-title {
  min-width: 0;
  overflow: hidden;
  font-size: 18px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.interview-tags {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
}

.meta-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: var(--zm-radius-pill);
  background: var(--zm-bg);
  color: var(--zm-ink-soft);
  font-size: 12px;
  font-weight: 550;
  white-space: nowrap;
}

.meta-chip--accent {
  background: var(--zm-accent-soft);
  color: var(--zm-accent);
  font-variant-numeric: tabular-nums;
}

.meta-chip--live {
  background: rgba(52, 199, 89, 0.12);
  color: #1f9d48;
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--zm-green);
  animation: pulse 1.8s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(52, 199, 89, 0.5);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(52, 199, 89, 0);
  }
}

.interview-actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 10px;
}

.plan-toggle-wrap {
  display: inline-flex;
}

.finish-text-short {
  display: none;
}

.header-finish-btn .el-icon ~ span {
  margin-left: 4px;
}

.icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  padding: 0;
  border: 1px solid var(--zm-border);
  border-radius: var(--zm-radius-sm);
  background: var(--zm-bg-elevated);
  color: var(--zm-ink-soft);
  font-size: 17px;
  cursor: pointer;
  transition: background var(--zm-dur-fast) var(--zm-ease), color var(--zm-dur-fast) var(--zm-ease);
}

.icon-button:hover {
  background: var(--zm-accent-soft);
  color: var(--zm-accent);
}

.icon-button--ghost {
  border-color: transparent;
}

.header-progress {
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 3px;
  background: transparent;
}

.header-progress-bar {
  display: block;
  height: 100%;
  border-radius: 0 3px 3px 0;
  background: linear-gradient(90deg, var(--zm-accent), #5ac8fa);
  transition: width var(--zm-dur-slow) var(--zm-ease);
}

.interview-frame {
  position: relative;
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.interview-frame--plan-open {
  grid-template-columns: minmax(0, 1fr) 300px;
}

.plan-backdrop {
  display: none;
}

.conversation-column {
  position: relative;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* ============ 对话区 ============ */
.transcript {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

/* 内容居中并限宽：宽屏下不再"一行拉满"，窄屏下占满可用宽度 */
.transcript-inner {
  position: relative;
  width: 100%;
  max-width: 880px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 28px 24px 24px;
}

.turn {
  display: flex;
  flex-shrink: 0;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}

.turn--user {
  flex-direction: row-reverse;
}

.turn :deep(.examiner) {
  flex-shrink: 0;
}

.turn :deep(.examiner--compact .face),
.turn :deep(.examiner--compact .ripples) {
  width: 38px;
  height: 38px;
}

.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--zm-accent), #5ac8fa);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
}

.turn-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  max-width: calc(100% - 100px);
}

.turn--user .turn-body {
  align-items: flex-end;
}

.turn-meta {
  margin: 0 4px 6px;
  color: var(--zm-ink-faint);
  font-size: 12px;
  font-weight: 550;
}

.bubble {
  min-width: 48px;
  padding: 14px 18px;
  border-radius: 16px;
  font-size: 15px;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
  text-align: left;
}

.turn--assistant .bubble {
  background: var(--zm-bg-elevated);
  border: 1px solid var(--zm-border);
  border-top-left-radius: 4px;
  box-shadow: var(--zm-shadow-sm);
}

.turn--user .bubble {
  background: var(--zm-accent);
  color: #fff;
  border-top-right-radius: 4px;
  box-shadow: 0 6px 16px rgba(10, 132, 255, 0.16);
}

.bubble--failed,
.turn--user .bubble--failed {
  background: rgba(255, 59, 48, 0.08);
  color: var(--zm-ink);
  border: 1px solid rgba(255, 59, 48, 0.35);
  box-shadow: none;
}

.fail-hint {
  display: block;
  margin-top: 6px;
  color: var(--zm-red);
  font-size: 12px;
}

.bubble--typing {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  min-height: 24px;
}

.bubble--typing .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--zm-ink-faint);
  animation: typing-bounce 1.1s ease-in-out infinite;
}

.bubble--typing .dot:nth-child(2) {
  animation-delay: 0.15s;
}
.bubble--typing .dot:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes typing-bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

.bubble-processing {
  display: block;
  margin-top: 8px;
  color: var(--zm-ink-faint);
  font-size: 12px;
}

/* ============ 输入区 ============ */
.composer-wrap {
  position: relative;
  flex-shrink: 0;
  width: 100%;
  max-width: 880px;
  margin: 0 auto;
  padding: 4px 24px 20px;
}

.jump-latest {
  position: absolute;
  top: -44px;
  left: 50%;
  z-index: 2;
  transform: translateX(-50%);
  padding: 7px 14px;
  border: 1px solid var(--zm-border);
  border-radius: var(--zm-radius-pill);
  background: var(--zm-bg-elevated);
  color: var(--zm-accent);
  font-size: 12px;
  font-weight: 600;
  box-shadow: var(--zm-shadow-md);
  cursor: pointer;
}

.composer {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--zm-border-strong);
  border-radius: 16px;
  background: var(--zm-bg-elevated);
  box-shadow: var(--zm-shadow-md);
  transition: border-color var(--zm-dur-fast) var(--zm-ease), box-shadow var(--zm-dur-fast) var(--zm-ease);
}

.composer:focus-within {
  border-color: var(--zm-accent);
  box-shadow: 0 0 0 3px rgba(10, 132, 255, 0.12), var(--zm-shadow-md);
}

.composer--disabled {
  background: var(--zm-bg);
}

/* 输入框本身去掉边框，由外层 composer 提供统一的卡片外观 */
.composer :deep(.el-textarea__inner) {
  padding: 14px 18px 6px;
  border: 0;
  border-radius: 16px 16px 0 0;
  background: transparent;
  box-shadow: none !important;
  color: var(--zm-ink);
  font-size: 15px;
  line-height: 1.75;
  /* 自动增高但不挤占对话区：矮屏下最多占视口 35% */
  max-height: min(280px, 35vh);
  overflow-y: auto;
}

.composer :deep(.el-textarea.is-disabled .el-textarea__inner) {
  background: transparent;
  cursor: not-allowed;
}

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 12px;
  padding: 8px 12px 12px 18px;
}

.composer-status {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: var(--zm-ink-soft);
  font-size: 12.5px;
}

.status-text {
  white-space: nowrap;
}

.status-dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--zm-green);
}

.status-dot--thinking {
  background: var(--zm-accent);
  animation: blink 1s ease-in-out infinite;
}

.status-dot--processing {
  background: var(--zm-orange);
  animation: blink 1s ease-in-out infinite;
}

@keyframes blink {
  50% {
    opacity: 0.3;
  }
}

.queued-chip {
  padding: 2px 8px;
  border-radius: var(--zm-radius-pill);
  background: rgba(255, 159, 10, 0.14);
  color: #b45309;
  font-size: 11.5px;
  white-space: nowrap;
}

.composer-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.composer-hint,
.char-count {
  color: var(--zm-ink-faint);
  font-size: 12px;
  white-space: nowrap;
}

.composer-hint kbd {
  padding: 1px 5px;
  border: 1px solid var(--zm-border-strong);
  border-bottom-width: 2px;
  border-radius: 4px;
  background: var(--zm-bg);
  font-family: var(--zm-font-mono);
  font-size: 11px;
}

.composer-actions .el-button {
  height: 38px;
  min-width: 92px;
  padding: 0 18px;
  border-radius: 10px;
}

.composer-actions .el-button .el-icon + span {
  margin-left: 4px;
}

/* ============ 面试计划侧栏 ============ */
.plan-card {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-left: 1px solid var(--zm-border);
  background: var(--zm-bg-elevated);
}

.plan-heading {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px 20px;
}

.plan-heading h3 {
  font-size: 16px;
}

.plan-summary {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 14px;
  margin: 0 16px;
  padding: 14px;
  border-radius: var(--zm-radius-md);
  background: var(--zm-bg);
}

.plan-summary :deep(.el-progress-circle__track) {
  stroke: var(--zm-border);
}

.plan-summary-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.plan-summary-copy strong {
  font-size: 14px;
}

.plan-summary-copy span {
  color: var(--zm-ink-faint);
  font-size: 12px;
}

.plan-list {
  flex: 1;
  min-height: 0;
  margin: 0;
  padding: 12px 10px;
  overflow-y: auto;
  overscroll-behavior: contain;
  list-style: none;
}

.plan-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 8px 10px;
  border-radius: var(--zm-radius-sm);
  color: var(--zm-ink-soft);
}

.plan-row--active {
  background: var(--zm-accent-soft);
}

.plan-row-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border: 1px solid var(--zm-border-strong);
  border-radius: 50%;
  color: var(--zm-ink-faint);
  font-size: 11px;
  font-weight: 650;
}

.plan-row--done .plan-row-icon {
  border-color: transparent;
  color: var(--zm-green);
  font-size: 20px;
}

.plan-row--active .plan-row-icon {
  border-color: var(--zm-accent);
  background: var(--zm-accent);
  color: #fff;
}

.plan-row-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--zm-ink);
  font-size: 13px;
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plan-row--done .plan-row-label {
  color: var(--zm-ink-soft);
}

.plan-row--active .plan-row-label {
  color: var(--zm-accent);
  font-weight: 650;
}

.plan-row-state {
  flex-shrink: 0;
  color: var(--zm-ink-faint);
  font-size: 11px;
}

.plan-row--active .plan-row-state {
  color: var(--zm-accent);
}

.plan-footer {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex-shrink: 0;
  padding: 14px 20px 18px;
  border-top: 1px solid var(--zm-border);
  color: var(--zm-ink-soft);
  font-size: 12px;
}

.plan-tip-title {
  color: var(--zm-ink);
  font-weight: 650;
}

.plan-footer ul {
  margin: 0;
  padding-left: 16px;
  line-height: 1.7;
}

.plan-footer small {
  margin-top: 4px;
  color: var(--zm-orange);
  font-size: 11px;
  line-height: 1.5;
}

/* ============ 响应式 ============ */
@media (max-width: 1199px) {
  .interview-frame,
  .interview-frame--plan-open {
    grid-template-columns: minmax(0, 1fr);
  }

  .plan-card {
    position: absolute;
    inset: 0 0 0 auto;
    z-index: 3;
    width: min(320px, 100%);
    box-shadow: var(--zm-shadow-lg);
  }

  .plan-backdrop {
    display: block;
    position: absolute;
    inset: 0;
    z-index: 2;
    padding: 0;
    border: 0;
    background: rgba(17, 25, 39, 0.24);
  }
}

@media (max-width: 960px) {
  .interview-heading {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }
}

@media (max-width: 860px) {
  .hero {
    padding: 22px 20px;
  }

  .hero-avatar {
    width: 84px;
    height: 84px;
  }

  .hero-copy h2 {
    font-size: 20px;
  }

  .option-grid,
  .option-grid--scenario {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .interview-header {
    padding: 10px 14px;
    gap: 10px;
  }

  .interview-title {
    font-size: 16px;
  }

  .meta-chip:first-child {
    display: none;
  }

  .header-finish-btn {
    padding: 8px 12px;
  }

  .transcript-inner {
    padding: 18px 14px 16px;
    gap: 18px;
  }

  .turn {
    gap: 8px;
  }

  .turn :deep(.examiner--compact .face),
  .turn :deep(.examiner--compact .ripples),
  .user-avatar {
    width: 32px;
    height: 32px;
  }

  .turn-body {
    max-width: calc(100% - 40px);
  }

  .bubble {
    padding: 12px 14px;
    font-size: 14.5px;
    line-height: 1.75;
  }

  .composer-wrap {
    padding: 4px 10px max(10px, env(safe-area-inset-bottom));
  }

  .composer-hint {
    display: none;
  }
}

@media (max-width: 560px) {
  .hero-avatar {
    display: none;
  }

  .mode-switcher {
    grid-template-columns: 1fr;
  }

  .length-grid {
    gap: 8px;
  }

  .length-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
    padding: 12px;
  }

  .length-copy small {
    display: none;
  }

  .setup-hint {
    display: none;
  }

  .finish-text {
    display: none;
  }

  .finish-text-short {
    display: inline;
  }

  .meta-chip--live {
    display: none;
  }

  .state-card {
    padding: 36px 22px;
  }

  .report-wrap {
    padding: 16px 12px 28px;
  }

  .report-head,
  .report-body {
    padding: 20px;
  }

  .report-head .el-button {
    width: 100%;
  }
}

/* 矮屏(横屏手机、小窗口)：压缩外围留白，把高度让给对话区 */
@media (max-height: 600px) {
  .interview-header {
    padding-top: 8px;
    padding-bottom: 8px;
  }

  .transcript-inner {
    padding-top: 14px;
  }

  .composer-wrap {
    padding-bottom: 8px;
  }

  .composer :deep(.el-textarea__inner) {
    padding-top: 10px;
  }

  .composer-footer {
    padding-top: 4px;
    padding-bottom: 8px;
  }
}
</style>
