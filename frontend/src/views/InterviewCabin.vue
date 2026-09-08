<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, CircleCheck, Expand, Fold, Promotion } from '@element-plus/icons-vue'
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
const report = ref<InterviewReportVO | null>(null)
const reportMessage = ref('')
const lastReportStatus = ref<ReportStatus | null>(null)
const transcriptEl = ref<HTMLElement | null>(null)
const planExpanded = ref(window.matchMedia('(min-width: 1200px)').matches)
const planToggleEl = ref<HTMLButtonElement | null>(null)
let reportTimer: number | null = null

function closePlan() {
  planExpanded.value = false
  planToggleEl.value?.focus()
}

// 至少答过一轮(有一条用户消息)才让结束——否则一进来就结束,报告没内容可评
const canFinish = computed(() => transcript.value.some((t) => t.role === 'user'))

async function scrollToBottom() {
  await nextTick()
  transcriptEl.value?.scrollTo({ top: transcriptEl.value.scrollHeight, behavior: 'smooth' })
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
  scrollToBottom()
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
  if (!content || waitingReply.value || sessionId.value === null) {
    return
  }
  // 记下这次请求归属的会话:如果用户在等回复的过程中点了"重新开始",sessionId 会变,
  // 下面几个回调不能再假设自己面对的还是同一场会话,不然旧会话的回复会串进新会话的对话框里
  const requestSessionId = sessionId.value

  transcript.value.push({ role: 'user', content })
  answer.value = ''
  waitingReply.value = true
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
      scrollToBottom()
    },
    onDone: (event) => {
      if (sessionId.value !== requestSessionId) return
      answeredQuestionCount.value = event.answeredCount
      waitingReply.value = false
      if (event.finished) {
        targetQuestionCount.value = event.targetCount
        phase.value = 'generating-report'
        reportMessage.value = '已完成全部题目，正在生成评价报告'
        startReportPolling(requestSessionId)
      }
    },
    onError: (message) => {
      if (sessionId.value !== requestSessionId) return
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
      }
      if (message.includes('长时间未操作')) {
        startNew()
      }
      ElMessage.error(message)
      waitingReply.value = false
    },
  })
  if (sessionId.value === requestSessionId) {
    scrollToBottom()
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
        `当前待回答题目不会计分，本场已完成 ${answeredQuestionCount.value} 题，确定结束吗？`,
        '结束面试',
        { confirmButtonText: '结束并生成评价', cancelButtonText: '继续回答', type: 'warning' },
      )
    } catch {
      return
    }
  }
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

onUnmounted(stopReportPolling)

function startNew() {
  stopReportPolling()
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
  localStorage.removeItem('zm-active-interview-id')
  router.replace({ path: '/interview' })
}

const examinerStatus = computed(() => (waitingReply.value ? '> 考官正在思考…' : '> 轮到你回答了'))

const overallProgress = computed(() => {
  if (!targetQuestionCount.value) return 0
  return Math.min(100, Math.round((answeredQuestionCount.value / targetQuestionCount.value) * 100))
})

const planSteps = computed(() => {
  const option = activeDirection.value
  const scenarioModules = option && 'modules' in option ? option.modules : []
  const directionModules: Record<string, string[]> = {
    java: ['Java 基础', '集合框架', '并发编程', 'JVM', '项目深挖', '系统设计', '总结反馈'],
    mysql: ['SQL 基础', '索引原理', '事务与锁', '性能优化', '项目深挖', '系统设计', '总结反馈'],
    redis: ['数据结构', '持久化', '缓存设计', '分布式能力', '项目深挖', '系统设计', '总结反馈'],
    system_design: ['基础原理', '架构设计', '稳定性', '项目深挖', '场景题', '系统设计', '总结反馈'],
  }
  const modules = scenarioModules.length
    ? scenarioModules
    : directionModules[option && 'code' in option ? option.code : ''] ?? ['基础知识', '核心原理', '项目实践', '系统设计']
  const visibleCount = Math.min(7, Math.max(5, targetQuestionCount.value))
  const labels = Array.from(new Set(modules.length >= 7 ? modules : [...modules, '项目深挖', '系统设计', '总结反馈']))
  return labels.slice(0, visibleCount).map((label, index) => ({
    label,
    completed: index < answeredQuestionCount.value,
    active: index === Math.min(answeredQuestionCount.value, visibleCount - 1),
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
    <section v-if="!auth.token" class="gate zm-glass">
      <p class="zm-prompt gate-eyebrow">&gt; auth_required</p>
      <h2>先登录才能进入模拟面试</h2>
      <p class="gate-desc">面试记录要和你的账号绑定,匿名用户没有这个能力。</p>
      <el-button type="primary" size="large" @click="loginDialogVisible = true">去登录</el-button>
    </section>

    <!-- 选方向 -->
    <template v-else-if="phase === 'picking'">
      <p class="zm-prompt section-eyebrow">&gt; choose_interview_mode</p>
      <div class="mode-switcher" role="tablist" aria-label="选择面试模式">
        <button
          class="mode-tab"
          :class="{ 'mode-tab--active': interviewMode === 'direction' }"
          type="button"
          role="tab"
          :aria-selected="interviewMode === 'direction'"
          @click="interviewMode = 'direction'"
        >
          专项面试
          <small>集中练习单一技术方向</small>
        </button>
        <button
          class="mode-tab"
          :class="{ 'mode-tab--active': interviewMode === 'scenario' }"
          type="button"
          role="tab"
          :aria-selected="interviewMode === 'scenario'"
          @click="interviewMode = 'scenario'"
        >
          场景面试
          <small>模拟大厂综合考察路线</small>
        </button>
      </div>
      <div class="length-picker">
        <span>面试长度</span>
        <el-segmented v-model="selectedQuestionCount" :options="[5, 8, 12]" />
        <span class="length-unit">题</span>
      </div>
      <div v-if="interviewMode === 'direction'" class="direction-grid" v-loading="creating">
        <button
          v-for="option in DIRECTION_OPTIONS"
          :key="option.code"
          class="direction-card zm-glass zm-glass--hoverable"
          type="button"
          @click="pickOption(option)"
        >
          <h3>{{ option.label }}</h3>
          <p>{{ option.focus }}</p>
        </button>
      </div>
      <div v-else class="scenario-grid" v-loading="creating">
        <button
          v-for="option in SCENARIO_OPTIONS"
          :key="option.code"
          class="scenario-card zm-glass zm-glass--hoverable"
          type="button"
          @click="pickOption(option)"
        >
          <span class="scenario-kicker zm-prompt">&gt; scenario_mode</span>
          <h3>{{ option.label }}</h3>
          <p>{{ option.focus }}</p>
          <div class="scenario-modules">
            <span v-for="module in option.modules" :key="module" class="zm-tag">{{ module }}</span>
          </div>
        </button>
      </div>
    </template>

    <template v-else-if="phase === 'generating-report'">
      <section class="report-generating zm-glass" v-loading="reportLoading && lastReportStatus !== 2">
        <p class="zm-prompt section-eyebrow">&gt; generating_report</p>
        <h2>{{ lastReportStatus === 2 ? '评价报告生成失败' : '评价报告生成中' }}</h2>
        <p>{{ reportMessage || 'AI 评委正在整理本场面试表现，请稍候。' }}</p>
        <div class="report-actions">
          <el-button v-if="sessionId" type="primary" :loading="finishing" @click="endInterview">
            {{ lastReportStatus === 2 ? '重新生成' : '刷新状态' }}
          </el-button>
          <el-button @click="startNew">再来一场</el-button>
        </div>
      </section>
    </template>

    <!-- 评价报告 -->
    <template v-else-if="phase === 'report' && report">
      <div class="report-wrap">
        <section class="report-head zm-glass">
          <div>
            <p class="zm-prompt section-eyebrow">&gt; interview_report</p>
            <h2 class="report-title">{{ activeDirection?.label }} 方向 · 面试评价</h2>
          </div>
          <el-button type="primary" @click="startNew">再来一场</el-button>
        </section>
        <section class="report-body zm-glass">
          <ReportCard :report="report" />
        </section>
      </div>
    </template>

    <!-- 对话中 -->
    <template v-else>
      <div class="interview-page" @keydown.esc="closePlan">
        <header class="interview-header">
          <div class="interview-title-wrap">
            <h2 class="interview-title">
              {{ activeDirection?.label || '综合面试' }}
              <span class="title-divider">·</span>
              中级
              <el-icon><ArrowDown /></el-icon>
            </h2>
          </div>
          <div class="interview-actions">
            <el-tooltip :content="planExpanded ? '收起面试计划' : '展开面试计划'" placement="bottom" effect="light">
              <span class="plan-toggle-wrap">
                <button
                  ref="planToggleEl"
                  class="plan-toggle"
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
              :disabled="!canFinish || waitingReply"
              @click="endInterview"
            >
              <el-icon v-if="!finishing"><CircleCheck /></el-icon>
              结束并生成报告
            </el-button>
          </div>
        </header>

        <section class="interview-frame" :class="{ 'interview-frame--plan-open': planExpanded }">
          <div class="conversation-column">
            <div class="progress-strip">
              <div class="progress-label">
                <span>面试进度</span>
                <strong>{{ answeredQuestionCount + 1 }} / {{ targetQuestionCount }}</strong>
              </div>
              <div class="progress-line">
                <span
                  v-for="(step, index) in planSteps"
                  :key="step.label"
                  class="progress-step"
                  :class="{ 'progress-step--done': step.completed, 'progress-step--active': step.active }"
                >
                  <span class="progress-node">
                    <el-icon v-if="step.completed"><CircleCheck /></el-icon>
                    <span v-else>{{ index + 1 }}</span>
                  </span>
                  <span class="progress-step-label">{{ step.label }}</span>
                </span>
              </div>
            </div>

            <section class="chat-card">
              <div ref="transcriptEl" class="transcript">
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
                    <div class="turn-body">
                      <div class="turn-meta">
                        <span>{{ turn.role === 'user' ? '你' : '面试官' }}</span>
                        <span>{{ turn.role === 'user' ? '' : 'AI Interviewer' }}</span>
                      </div>
                      <div class="bubble" :class="{ 'bubble--failed': turn.pending }">
                        {{ turn.content }}
                        <span v-if="turn.pending" class="fail-hint">发送失败</span>
                      </div>
                    </div>
                  </div>
                </TransitionGroup>
                <div v-if="waitingReply" class="turn turn--assistant">
                  <ExaminerAvatar :speaking="true" compact />
                  <div class="turn-body">
                    <div class="turn-meta"><span>面试官</span><span>AI Interviewer</span></div>
                    <div class="bubble bubble--typing">
                      <span class="dot"></span><span class="dot"></span><span class="dot"></span>
                    </div>
                  </div>
                </div>
              </div>

              <div class="chat-toolbar">
                <span class="examiner-status zm-prompt">{{ examinerStatus }}</span>
                <span class="chat-progress-note">回答后自动进入下一题</span>
              </div>

              <div class="composer">
                <el-input
                  v-model="answer"
                  type="textarea"
                  :rows="3"
                  resize="none"
                  placeholder="请输入你的回答…（Ctrl + Enter 发送）"
                  @keydown.ctrl.enter="submitAnswer"
                />
                <el-button type="primary" :disabled="!answer.trim()" :loading="waitingReply" @click="submitAnswer">
                  <el-icon v-if="!waitingReply"><Promotion /></el-icon>
                  发送
                </el-button>
              </div>
            </section>
          </div>

          <button v-if="planExpanded" class="plan-backdrop" type="button" tabindex="-1" aria-label="关闭面试计划" @click="closePlan"></button>
          <aside v-show="planExpanded" id="interview-plan" class="plan-card" aria-label="面试计划">
            <div class="plan-heading">
              <h3>面试计划</h3>
              <el-tooltip content="收起面试计划" placement="left" effect="light">
                <button class="plan-collapse" type="button" aria-label="收起面试计划" @click="closePlan">
                  <el-icon><Fold /></el-icon>
                </button>
              </el-tooltip>
            </div>
            <div class="plan-summary">
              <div>
                <p>整体进度</p>
                <el-progress type="circle" :percentage="overallProgress" :width="64" :stroke-width="5" />
              </div>
              <span>已完成 {{ answeredQuestionCount }} / 预计 {{ targetQuestionCount }} 题</span>
            </div>
            <div class="plan-list">
              <div
                v-for="(step, index) in planSteps"
                :key="step.label"
                class="plan-row"
                :class="{ 'plan-row--active': step.active }"
              >
                <span class="plan-row-icon">
                  <el-icon v-if="step.completed"><CircleCheck /></el-icon>
                  <span v-else>{{ step.active ? '·' : '○' }}</span>
                </span>
                <span class="plan-row-copy">
                  <strong>{{ index + 1 }}. {{ step.label }}</strong>
                  <small>{{ step.completed ? '已完成' : step.active ? '进行中' : '待进行' }}</small>
                </span>
              </div>
            </div>
            <div class="plan-footer">
              <span>进度会自动保存，下次继续</span>
              <small>长时间无操作将自动结束，且不生成报告</small>
            </div>
          </aside>
        </section>
      </div>
    </template>

    <LoginDialog v-model="loginDialogVisible" />
  </div>
</template>

<style scoped>
.cabin {
  max-width: 1180px;
}

.cabin--chatting {
  width: 100%;
  max-width: none;
  height: 100%;
  min-height: 0;
}

.gate {
  max-width: 480px;
  margin: 10vh auto 0;
  padding: 56px 44px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.gate-eyebrow {
  font-size: 13px;
}

.gate-desc {
  font-size: 14px;
  color: var(--zm-ink-soft);
  margin-bottom: 8px;
}

.section-eyebrow {
  font-size: 13px;
  margin-bottom: 16px;
  display: inline-block;
}

.mode-switcher {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 18px;
}

.mode-tab {
  border: 1px solid var(--zm-border);
  background: var(--zm-bg-elevated);
  color: var(--zm-ink-soft);
  padding: 15px 16px;
  text-align: left;
  cursor: pointer;
  border-radius: var(--zm-radius-sm);
  transition: border-color 0.2s ease, background 0.2s ease, color 0.2s ease;
}

.mode-tab small {
  display: block;
  margin-top: 4px;
  color: var(--zm-ink-faint);
  font-size: 11px;
}

.mode-tab--active {
  border-color: var(--zm-accent);
  background: var(--zm-accent-soft);
  color: var(--zm-accent-strong);
  box-shadow: 0 0 0 3px rgba(10, 132, 255, 0.08);
}

.direction-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  min-height: 120px;
}

.length-picker {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  font-size: 13px;
  color: var(--zm-ink-soft);
}

.length-unit {
  color: var(--zm-ink-faint);
}

.direction-card {
  padding: 22px 20px;
  text-align: left;
  cursor: pointer;
  border: 1px solid var(--zm-border);
  background: var(--zm-bg-elevated);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.direction-card h3 {
  font-size: 16px;
}

.direction-card p {
  font-size: 12px;
  line-height: 1.6;
  color: var(--zm-ink-faint);
}

.scenario-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 16px;
}

.scenario-card {
  padding: 22px 20px;
  text-align: left;
  cursor: pointer;
  border: 1px solid var(--zm-border);
  background: var(--zm-bg-elevated);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 9px;
}

.scenario-kicker {
  color: var(--zm-accent);
  font-size: 11px;
}

.scenario-card p {
  font-size: 12px;
  line-height: 1.6;
  color: var(--zm-ink-faint);
}

.scenario-modules {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 3px;
}

.cabin-grid {
  display: grid;
  grid-template-columns: 0.85fr 1.15fr;
  gap: 20px;
  align-items: start;
}

.examiner-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 30px 24px;
  gap: 14px;
  text-align: center;
  border-color: rgba(10, 132, 255, 0.14);
}

.examiner-status {
  font-size: 12px;
}

.session-meta {
  display: flex;
  gap: 8px;
}

.finish-btn {
  margin-top: 6px;
  width: 100%;
}

.finish-hint {
  font-size: 11px;
  color: var(--zm-ink-faint);
  line-height: 1.5;
}

.report-wrap {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.report-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px;
}

.report-title {
  font-size: 18px;
  margin-top: 4px;
}

.report-body {
  padding: 24px;
}

.report-generating {
  max-width: 520px;
  margin: 10vh auto 0;
  padding: 44px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  text-align: center;
}

.report-generating p {
  font-size: 13px;
  color: var(--zm-ink-soft);
}

.report-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 4px;
}

.chat-card {
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
  border-color: rgba(10, 132, 255, 0.14);
}

.transcript {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 24px 24px 16px;
}

.turn {
  display: flex;
  flex-shrink: 0;
}

.turn--assistant {
  justify-content: flex-start;
}

.turn--user {
  justify-content: flex-end;
}

.bubble {
  max-width: 78%;
  padding: 12px 16px;
  border-radius: var(--zm-radius-md);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.turn--assistant .bubble {
  background: var(--zm-surface-strong);
  border: 1px solid var(--zm-border);
  border-bottom-left-radius: 4px;
}

.turn--user .bubble {
  background: var(--zm-accent);
  color: white;
  border-bottom-right-radius: 4px;
}

.bubble--failed {
  background: rgba(255, 59, 48, 0.12) !important;
  color: var(--zm-red) !important;
  border: 1px solid rgba(255, 59, 48, 0.3);
}

.fail-hint {
  display: block;
  font-size: 11px;
  margin-top: 4px;
  opacity: 0.8;
}

.bubble--typing {
  display: flex;
  gap: 4px;
  padding: 16px;
}

.bubble--typing .dot {
  width: 6px;
  height: 6px;
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

.composer {
  display: flex;
  flex-shrink: 0;
  gap: 10px;
  align-items: flex-end;
  padding: 16px 18px 18px;
  background: var(--zm-bg-elevated);
  border-top: 1px solid var(--zm-border);
}

.composer :deep(.el-textarea) {
  flex: 1;
}

.btn-icon {
  margin-right: 4px;
}

@media (max-width: 860px) {
  .mode-switcher {
    grid-template-columns: 1fr;
  }
  .cabin-grid {
    grid-template-columns: 1fr;
  }
}

/* Only the transcript and plan list scroll inside the viewport-sized workspace. */
.interview-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--zm-bg-elevated);
}

.interview-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 72px;
  padding: 14px 24px;
  border-bottom: 1px solid var(--zm-border);
}

.interview-title-wrap {
  min-width: 0;
}

.interview-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 22px;
  font-weight: 700;
}

.interview-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.plan-toggle,
.plan-collapse {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  padding: 0;
  border: 1px solid var(--zm-border);
  border-radius: var(--zm-radius-sm);
  background: var(--zm-bg-elevated);
  color: var(--zm-ink-soft);
  font-size: 17px;
  cursor: pointer;
}

.plan-toggle-wrap {
  display: inline-flex;
}

.plan-toggle:hover,
.plan-collapse:hover {
  background: var(--zm-accent-soft);
  color: var(--zm-accent);
}

.interview-title .el-icon {
  margin-left: 3px;
  color: var(--zm-ink-soft);
  font-size: 16px;
}

.title-divider {
  color: var(--zm-ink-faint);
  font-weight: 400;
}

.header-finish-btn {
  min-width: 150px;
}

.interview-frame {
  position: relative;
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  margin: 0 16px 16px;
  padding: 14px;
  border: 1px solid var(--zm-border);
  border-top: 0;
  border-radius: 0 0 14px 14px;
  background: var(--zm-surface);
}

.interview-frame--plan-open {
  grid-template-columns: minmax(0, 1fr) 280px;
}

.plan-backdrop {
  display: none;
}

.conversation-column {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.progress-strip {
  flex-shrink: 0;
  padding: 2px 8px 18px;
}

.progress-label {
  display: flex;
  align-items: baseline;
  gap: 16px;
  color: var(--zm-ink-soft);
  font-size: 14px;
}

.progress-label strong {
  color: var(--zm-ink);
  font-size: 14px;
}

.progress-line {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(0, 1fr);
  gap: 0;
  margin-top: 15px;
}

.progress-step {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: var(--zm-ink-faint);
  font-size: 11px;
  text-align: center;
}

.progress-step:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 12px;
  left: 50%;
  width: 100%;
  height: 1px;
  background: var(--zm-border-strong);
  z-index: 0;
}

.progress-node {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 25px;
  height: 25px;
  border: 1px solid var(--zm-border-strong);
  border-radius: 50%;
  background: var(--zm-bg-elevated);
  color: var(--zm-ink-faint);
  font-size: 11px;
}

.progress-step--done .progress-node,
.progress-step--active .progress-node {
  border-color: var(--zm-accent);
  color: var(--zm-accent);
}

.progress-step--active .progress-node {
  background: var(--zm-accent);
  color: #fff;
  box-shadow: 0 0 0 4px rgba(10, 132, 255, 0.1);
}

.progress-step--done .progress-node {
  background: var(--zm-bg-elevated);
}

.progress-step--done::after,
.progress-step--active::after {
  background: var(--zm-accent);
}

.progress-step-label {
  overflow: hidden;
  max-width: 100%;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-card {
  min-height: 0;
  height: auto;
  flex: 1;
  border: 1px solid var(--zm-border);
  border-radius: 11px;
  background: var(--zm-bg-elevated);
  box-shadow: var(--zm-shadow-sm);
}

.transcript {
  padding: 22px 20px 18px;
  gap: 18px;
}

.turn {
  align-items: flex-start;
  gap: 10px;
}

.turn--user {
  flex-direction: row-reverse;
}

.turn-body {
  max-width: min(88%, 820px);
  min-width: 0;
}

.turn--user .turn-body {
  text-align: right;
}

.turn-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 1px 2px 5px;
  color: var(--zm-ink-soft);
  font-size: 11px;
}

.turn--user .turn-meta {
  justify-content: flex-end;
}

.turn-meta span + span {
  color: var(--zm-ink-faint);
}

.bubble {
  max-width: none;
  padding: 13px 16px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.65;
}

.turn--assistant .bubble {
  background: #f4f6f8;
  border: 0;
  border-bottom-left-radius: 4px;
}

.turn--user .bubble {
  background: #eaf3ff;
  color: var(--zm-ink);
  border: 1px solid rgba(10, 132, 255, 0.08);
  border-bottom-right-radius: 4px;
}

.chat-toolbar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 48px;
  padding: 0 18px;
  border-top: 1px solid var(--zm-border);
  color: var(--zm-ink-faint);
  font-size: 11px;
}

.chat-toolbar .examiner-status {
  margin: 0;
}

.chat-progress-note {
  white-space: nowrap;
}

.composer {
  align-items: flex-end;
  gap: 12px;
  padding: 14px 16px 16px;
}

.composer :deep(.el-textarea__inner) {
  min-height: 76px !important;
  padding: 12px 13px;
  border-radius: 8px;
  background: var(--zm-bg);
}

.composer .el-button {
  height: 42px;
  min-width: 86px;
}

.plan-card {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--zm-border);
  border-radius: 11px;
  background: var(--zm-bg-elevated);
  box-shadow: var(--zm-shadow-sm);
}

.plan-heading {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  padding: 18px 18px 14px;
}

.plan-heading h3 {
  font-size: 17px;
}

.plan-summary {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 14px;
  padding: 0 18px 16px;
  border-bottom: 1px solid var(--zm-border);
}

.plan-summary p {
  margin-bottom: 6px;
  color: var(--zm-ink-soft);
  font-size: 12px;
}

.plan-summary > span {
  color: var(--zm-ink-soft);
  font-size: 11px;
  line-height: 1.6;
}

.plan-summary :deep(.el-progress-circle__track) {
  stroke: var(--zm-bg);
}

.plan-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 9px 0;
}

.plan-row {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 42px;
  padding: 7px 14px;
  color: var(--zm-ink-soft);
}

.plan-row--active {
  background: var(--zm-accent-soft);
  color: var(--zm-accent);
}

.plan-row-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  color: var(--zm-ink-faint);
  font-size: 16px;
}

.plan-row--active .plan-row-icon,
.plan-row-icon .el-icon {
  color: var(--zm-accent);
}

.plan-row-copy {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.plan-row-copy strong {
  overflow: hidden;
  color: var(--zm-ink);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plan-row-copy small {
  flex-shrink: 0;
  color: var(--zm-ink-faint);
  font-size: 10px;
}

.plan-row--active .plan-row-copy small {
  color: var(--zm-accent);
}

.plan-footer {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex-shrink: 0;
  padding: 13px 16px;
  border-top: 1px solid var(--zm-border);
  color: var(--zm-ink-faint);
  font-size: 11px;
}

.plan-footer small {
  color: var(--zm-orange);
  font-size: 10px;
}

@media (max-width: 1199px) {
  .interview-frame {
    grid-template-columns: minmax(0, 1fr);
  }

  .plan-card {
    position: absolute;
    inset: 0 0 0 auto;
    z-index: 3;
    width: min(300px, 100%);
  }

  .plan-backdrop {
    display: block;
    position: absolute;
    inset: 0;
    z-index: 2;
    padding: 0;
    border: 0;
    background: rgba(0, 0, 0, 0.12);
  }
}

@media (max-width: 820px) {
  .interview-header {
    min-height: 64px;
    padding: 12px 16px;
    gap: 10px;
  }

  .interview-title {
    font-size: 18px;
  }

  .interview-frame {
    margin: 0;
    padding: 12px;
    border-right: 0;
    border-left: 0;
    border-radius: 0;
  }

  .progress-line {
    grid-auto-columns: minmax(62px, 1fr);
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .progress-step {
    min-width: 62px;
  }

  .transcript {
    padding: 16px 12px;
  }

  .turn-body {
    max-width: 88%;
  }

  .turn--assistant .turn-body {
    max-width: calc(100% - 54px);
  }

  .composer {
    gap: 8px;
    padding: 10px;
    padding-bottom: max(10px, env(safe-area-inset-bottom));
  }

  .composer .el-button {
    min-width: 64px;
    padding: 8px 10px;
  }
}

@media (max-width: 520px) {
  .interview-header {
    flex-wrap: wrap;
    gap: 8px;
  }

  .interview-title {
    font-size: 16px;
    gap: 6px;
  }

  .interview-actions {
    margin-left: auto;
  }

  .header-finish-btn {
    min-width: 0;
    padding: 8px 10px;
  }

  .progress-strip {
    padding: 0 0 12px;
  }

  .chat-progress-note {
    display: none;
  }

  .chat-toolbar {
    min-height: 36px;
    padding: 0 12px;
  }
}

@media (max-height: 560px), (max-width: 520px) and (max-height: 640px) {
  .interview-header {
    min-height: 48px;
    padding-top: 8px;
    padding-bottom: 8px;
  }

  .interview-frame {
    margin-bottom: 0;
    padding: 8px;
  }

  .progress-strip {
    padding-bottom: 8px;
  }

  .progress-line,
  .chat-toolbar {
    display: none;
  }

  .composer {
    padding: 8px;
  }

  .composer :deep(.el-textarea__inner) {
    height: 52px;
    min-height: 52px !important;
  }

  .transcript {
    padding: 12px;
  }
}
</style>
