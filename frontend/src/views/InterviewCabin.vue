<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, Promotion } from '@element-plus/icons-vue'
import ExaminerAvatar from '../components/interview/ExaminerAvatar.vue'
import ReportCard from '../components/interview/ReportCard.vue'
import LoginDialog from '../components/layout/LoginDialog.vue'
import { useAuthStore } from '../stores/auth'
import { chatInterviewStream, createInterview, finishInterview } from '../api/interview'
import { DIRECTION_OPTIONS } from '../types/interview'
import type { ChatTurn, DirectionOption, InterviewReportVO } from '../types/interview'

const auth = useAuthStore()
const loginDialogVisible = ref(false)

const phase = ref<'picking' | 'chatting' | 'report'>('picking')
const creating = ref(false)
const finishing = ref(false)
const activeDirection = ref<DirectionOption | null>(null)
const sessionId = ref<number | null>(null)
const transcript = ref<ChatTurn[]>([])
const answer = ref('')
const waitingReply = ref(false)
const report = ref<InterviewReportVO | null>(null)
const transcriptEl = ref<HTMLElement | null>(null)

// 至少答过一轮(有一条用户消息)才让结束——否则一进来就结束,报告没内容可评
const canFinish = computed(() => transcript.value.some((t) => t.role === 'user'))

async function scrollToBottom() {
  await nextTick()
  transcriptEl.value?.scrollTo({ top: transcriptEl.value.scrollHeight, behavior: 'smooth' })
}

async function pickDirection(option: DirectionOption) {
  if (!auth.token) {
    loginDialogVisible.value = true
    return
  }
  creating.value = true
  try {
    const session = await createInterview(option.code)
    activeDirection.value = option
    sessionId.value = session.id
    transcript.value = [{ role: 'assistant', content: session.openingMessage }]
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

  // 先占一个空的 assistant 气泡,delta 事件到达时往里追加文字,打字机效果
  const assistantTurn: ChatTurn = { role: 'assistant', content: '' }
  transcript.value.push(assistantTurn)

  await chatInterviewStream(requestSessionId, content, {
    onDelta: (delta) => {
      if (sessionId.value !== requestSessionId) return
      assistantTurn.content += delta
      scrollToBottom()
    },
    onDone: () => {
      if (sessionId.value !== requestSessionId) return
      waitingReply.value = false
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
  finishing.value = true
  try {
    report.value = await finishInterview(sessionId.value)
    phase.value = 'report'
  } catch {
    // 已由 http.ts 拦截器统一提示,这里不重复弹
  } finally {
    finishing.value = false
  }
}

function startNew() {
  phase.value = 'picking'
  activeDirection.value = null
  sessionId.value = null
  transcript.value = []
  answer.value = ''
  report.value = null
}

const examinerStatus = computed(() => (waitingReply.value ? '> 考官正在思考…' : '> 轮到你回答了'))
</script>

<template>
  <div class="cabin">
    <!-- 未登录 -->
    <section v-if="!auth.token" class="gate zm-glass">
      <p class="zm-prompt gate-eyebrow">&gt; auth_required</p>
      <h2>先登录才能进入模拟面试</h2>
      <p class="gate-desc">面试记录要和你的账号绑定,匿名用户没有这个能力。</p>
      <el-button type="primary" size="large" @click="loginDialogVisible = true">去登录</el-button>
    </section>

    <!-- 选方向 -->
    <template v-else-if="phase === 'picking'">
      <p class="zm-prompt section-eyebrow">&gt; pick_a_direction</p>
      <div class="direction-grid" v-loading="creating">
        <button
          v-for="option in DIRECTION_OPTIONS"
          :key="option.code"
          class="direction-card zm-glass zm-glass--hoverable"
          type="button"
          @click="pickDirection(option)"
        >
          <h3>{{ option.label }}</h3>
          <p>{{ option.focus }}</p>
        </button>
      </div>
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
      <div class="cabin-grid">
        <section class="examiner-card zm-glass">
          <ExaminerAvatar :speaking="waitingReply" />
          <p class="examiner-status zm-prompt">{{ examinerStatus }}</p>
          <div class="session-meta">
            <span class="zm-tag zm-tag--active">{{ activeDirection?.label }}</span>
            <span class="zm-tag">第 {{ Math.ceil(transcript.length / 2) }} 轮</span>
          </div>
          <el-button
            class="finish-btn"
            type="primary"
            :loading="finishing"
            :disabled="!canFinish || waitingReply"
            @click="endInterview"
          >
            <el-icon v-if="!finishing" class="btn-icon"><CircleCheck /></el-icon>
            结束面试 · 生成评价
          </el-button>
          <p class="finish-hint">结束后会由 AI 评委给出一份评分报告</p>
        </section>

        <section class="chat-card zm-glass">
          <div ref="transcriptEl" class="transcript">
            <TransitionGroup name="zm-list">
              <div
                v-for="(turn, i) in transcript"
                :key="i"
                class="turn"
                :class="turn.role === 'user' ? 'turn--user' : 'turn--assistant'"
              >
                <div class="bubble" :class="{ 'bubble--failed': turn.pending }">
                  {{ turn.content }}
                  <span v-if="turn.pending" class="fail-hint">发送失败</span>
                </div>
              </div>
            </TransitionGroup>
            <div v-if="waitingReply" class="turn turn--assistant">
              <div class="bubble bubble--typing">
                <span class="dot"></span><span class="dot"></span><span class="dot"></span>
              </div>
            </div>
          </div>

          <div class="composer">
            <el-input
              v-model="answer"
              type="textarea"
              :rows="3"
              resize="none"
              placeholder="组织好你的回答,按 Ctrl+Enter 提交…"
              @keydown.ctrl.enter="submitAnswer"
            />
            <el-button type="primary" :disabled="!answer.trim()" :loading="waitingReply" @click="submitAnswer">
              <el-icon v-if="!waitingReply" class="btn-icon"><Promotion /></el-icon>
              提交回答
            </el-button>
          </div>
        </section>
      </div>
    </template>

    <LoginDialog v-model="loginDialogVisible" />
  </div>
</template>

<style scoped>
.cabin {
  max-width: 1080px;
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

.direction-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  min-height: 120px;
}

.direction-card {
  padding: 22px 20px;
  text-align: left;
  cursor: pointer;
  border: none;
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

.chat-card {
  display: flex;
  flex-direction: column;
  height: 560px;
  padding: 20px;
}

.transcript {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 4px 12px;
}

.turn {
  display: flex;
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
  gap: 10px;
  align-items: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--zm-border);
}

.composer :deep(.el-textarea) {
  flex: 1;
}

.btn-icon {
  margin-right: 4px;
}

@media (max-width: 860px) {
  .cabin-grid {
    grid-template-columns: 1fr;
  }
  .chat-card {
    height: 480px;
  }
}
</style>
