<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import ReportCard from '../components/interview/ReportCard.vue'
import LoginDialog from '../components/layout/LoginDialog.vue'
import { useAuthStore } from '../stores/auth'
import { finishInterview, getInterviewDetail, listInterviews } from '../api/interview'
import { STATUS_META, directionLabel } from '../types/interview'
import type {
  InterviewReportVO,
  InterviewSessionDetailVO,
  InterviewSessionListVO,
} from '../types/interview'
import { formatDateTime } from '../utils/format'

const auth = useAuthStore()
const loginDialogVisible = ref(false)

const sessions = ref<InterviewSessionListVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

async function refresh() {
  if (!auth.token) return
  loading.value = true
  try {
    const page = await listInterviews(pageNum.value, pageSize.value)
    sessions.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  pageNum.value = p
  refresh()
}

onMounted(refresh)
// 登录成功后(token 从空变有)自动拉取记录
watch(
  () => auth.token,
  (t) => {
    if (t) {
      pageNum.value = 1
      refresh()
    }
  },
)

// —— 详情抽屉 ——
const drawerVisible = ref(false)
const detail = ref<InterviewSessionDetailVO | null>(null)
const detailLoading = ref(false)
const drawerReport = ref<InterviewReportVO | null>(null)
const reportLoading = ref(false)

async function openDetail(item: InterviewSessionListVO) {
  drawerVisible.value = true
  detail.value = null
  drawerReport.value = null
  detailLoading.value = true
  try {
    detail.value = await getInterviewDetail(item.id)
    // 已评价的会话顺带把报告取出来展示。后端没有单独的"查报告"接口,
    // 但 finish 对 status=2 是幂等只读(直接返回已存报告,不重新调模型),拿来读正好。
    if (detail.value.status === 2) {
      await loadReport(item.id)
    }
  } finally {
    detailLoading.value = false
  }
}

// 生成 / 读取本场评价。对未评价的会话(status 0/1)这一步会真正触发一次模型评价,并把状态推进到已评价。
async function loadReport(id: number) {
  reportLoading.value = true
  try {
    drawerReport.value = await finishInterview(id)
    await refresh() // 状态可能从 进行中/已结束 变成 已评价,刷新列表让 badge 同步
  } catch {
    // http.ts 拦截器已统一提示
  } finally {
    reportLoading.value = false
  }
}
</script>

<template>
  <div class="history">
    <!-- 未登录 -->
    <section v-if="!auth.token" class="gate zm-glass">
      <p class="zm-prompt gate-eyebrow">&gt; auth_required</p>
      <h2>登录后查看你的面试记录</h2>
      <el-button type="primary" size="large" @click="loginDialogVisible = true">去登录</el-button>
    </section>

    <template v-else>
      <p class="zm-prompt section-eyebrow">&gt; my_interviews</p>

      <div v-loading="loading" class="list">
        <button
          v-for="item in sessions"
          :key="item.id"
          class="record zm-glass zm-glass--hoverable"
          type="button"
          @click="openDetail(item)"
        >
          <div class="record-main">
            <span class="record-dir">{{ directionLabel(item.direction) }}</span>
            <span class="record-title">{{ item.title }}</span>
          </div>
          <div class="record-side">
            <span class="zm-tag" :class="`zm-tag--${STATUS_META[item.status].tone}`">
              {{ STATUS_META[item.status].label }}
            </span>
            <span class="record-time">{{ formatDateTime(item.createTime) }}</span>
          </div>
        </button>

        <p v-if="!loading && !sessions.length" class="empty">还没有任何面试记录,去「模拟面试舱」开一场吧。</p>
      </div>

      <div v-if="total > pageSize" class="pager">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :current-page="pageNum"
          :page-size="pageSize"
          @current-change="onPageChange"
        />
      </div>
    </template>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" size="560px" :with-header="false" destroy-on-close>
      <div v-if="detail" v-loading="detailLoading" class="drawer">
        <header class="drawer-head">
          <span class="zm-tag zm-tag--active">{{ directionLabel(detail.direction) }}</span>
          <span class="zm-tag" :class="`zm-tag--${STATUS_META[detail.status].tone}`">
            {{ STATUS_META[detail.status].label }}
          </span>
          <span class="drawer-time">{{ formatDateTime(detail.createTime) }}</span>
        </header>

        <!-- 报告区 -->
        <div v-if="drawerReport" class="drawer-report zm-glass">
          <ReportCard :report="drawerReport" />
        </div>
        <div v-else-if="detail.status !== 2" class="drawer-report-cta">
          <span>{{ detail.status === 0 ? '这场面试还没结束' : '这场面试还没生成评价' }}</span>
          <el-button type="primary" :loading="reportLoading" @click="loadReport(detail.id)">
            {{ detail.status === 0 ? '结束并生成评价' : '生成评价报告' }}
          </el-button>
        </div>

        <!-- 对话记录 -->
        <h4 class="drawer-sub">对话记录</h4>
        <div class="drawer-transcript">
          <div
            v-for="(m, i) in detail.messages"
            :key="i"
            class="turn"
            :class="m.role === 'user' ? 'turn--user' : 'turn--assistant'"
          >
            <div class="bubble">{{ m.content }}</div>
          </div>
          <p v-if="!detail.messages.length" class="empty">这场面试还没有对话内容。</p>
        </div>
      </div>
    </el-drawer>

    <LoginDialog v-model="loginDialogVisible" />
  </div>
</template>

<style scoped>
.history {
  max-width: 900px;
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

.section-eyebrow {
  font-size: 13px;
  margin-bottom: 16px;
  display: inline-block;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

.record {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px;
  border: none;
  cursor: pointer;
  text-align: left;
}

.record-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.record-dir {
  font-size: 15px;
  font-weight: 700;
  color: var(--zm-ink);
}

.record-title {
  font-size: 12px;
  color: var(--zm-ink-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-side {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.record-time {
  font-size: 12px;
  color: var(--zm-ink-faint);
}

.empty {
  font-size: 13px;
  color: var(--zm-ink-faint);
  padding: 24px 4px;
}

.pager {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.drawer {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.drawer-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.drawer-time {
  font-size: 12px;
  color: var(--zm-ink-faint);
  margin-left: auto;
}

.drawer-report {
  padding: 20px;
}

.drawer-report-cta {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-start;
  padding: 18px;
  border: 1px dashed var(--zm-border);
  border-radius: var(--zm-radius-md);
  font-size: 13px;
  color: var(--zm-ink-soft);
}

.drawer-sub {
  font-size: 14px;
  font-weight: 700;
  color: var(--zm-ink);
}

.drawer-transcript {
  display: flex;
  flex-direction: column;
  gap: 12px;
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
  max-width: 82%;
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
</style>
