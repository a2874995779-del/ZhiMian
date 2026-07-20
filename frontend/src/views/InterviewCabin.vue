<script setup lang="ts">
import { computed, ref } from 'vue'
import { Microphone, VideoCamera, Promotion, RefreshRight } from '@element-plus/icons-vue'
import ExaminerAvatar from '../components/interview/ExaminerAvatar.vue'
import StageTimeline from '../components/interview/StageTimeline.vue'
import ProgressRing from '../components/dashboard/ProgressRing.vue'
import { interviewStages } from '../mock/interview'
import type { StagePhase } from '../types/interview'

const currentIndex = ref(0)
const phase = ref<StagePhase>('answering')
const answer = ref('')
const recording = ref<'none' | 'audio' | 'video'>('none')
const finished = ref(false)

const stage = computed(() => interviewStages[currentIndex.value])

// 考官"说话"状态:答题阶段持续,评估中停止
const examinerSpeaking = computed(() => phase.value === 'answering' && !finished.value)

function toggleRecording(mode: 'audio' | 'video') {
  recording.value = recording.value === mode ? 'none' : mode
}

function submitAnswer() {
  if (phase.value !== 'answering') return
  phase.value = 'evaluating'
  // mock 评估耗时:真实场景这里是一次 AI 接口调用
  setTimeout(() => {
    phase.value = 'reported'
  }, 1400)
}

function nextStage() {
  if (currentIndex.value >= interviewStages.length - 1) {
    finished.value = true
    return
  }
  currentIndex.value++
  phase.value = 'answering'
  answer.value = ''
  recording.value = 'none'
}

function restart() {
  currentIndex.value = 0
  phase.value = 'answering'
  answer.value = ''
  recording.value = 'none'
  finished.value = false
}

const overallScore = computed(() => {
  const all = interviewStages.flatMap((s) => s.report.map((d) => d.score))
  return Math.round(all.reduce((a, b) => a + b, 0) / all.length)
})
</script>

<template>
  <div class="cabin">
    <!-- 面试进度时间轴 -->
    <section class="timeline-card zm-glass">
      <StageTimeline :stages="interviewStages" :current-index="finished ? interviewStages.length : currentIndex" />
    </section>

    <!-- 完场总结 -->
    <template v-if="finished">
      <section class="summary zm-glass">
        <p class="zm-prompt summary-eyebrow">&gt; interview_complete</p>
        <h2>本场模拟面试结束</h2>
        <div class="summary-body">
          <ProgressRing :value="overallScore" :size="150" label="综合评分" color="var(--zm-gold)" />
          <p class="summary-text">
            五个环节全部完成。整体表现稳健:并发和缓存两个环节的知识深度是明显强项,系统设计环节的容灾思路和自我介绍的感染力是下一步要补的短板——把每个环节报告里分数最低的维度挑出来,针对性练三遍,下一场就能看到分差。
          </p>
        </div>
        <el-button type="primary" size="large" @click="restart">
          <el-icon class="btn-icon"><RefreshRight /></el-icon>
          再来一场
        </el-button>
      </section>
    </template>

    <template v-else>
      <div class="cabin-grid">
        <!-- 左:AI 考官区 -->
        <section class="examiner-card zm-glass">
          <ExaminerAvatar :speaking="examinerSpeaking" />
          <p class="examiner-status zm-prompt">
            {{ phase === 'answering' ? '> 考官正在提问' : phase === 'evaluating' ? '> 正在评估你的回答…' : '> 评估完成' }}
          </p>
          <blockquote class="question">{{ stage.question }}</blockquote>
          <p class="hint">{{ stage.hint }}</p>
        </section>

        <!-- 右:回答区 / 报告区 -->
        <section class="answer-card zm-glass">
          <Transition name="zm-fade" mode="out-in">
            <!-- 报告态 -->
            <div v-if="phase === 'reported'" class="report" key="report">
              <p class="zm-prompt report-eyebrow">&gt; analysis_report · {{ stage.name }}</p>
              <div class="report-grid">
                <div v-for="dim in stage.report" :key="dim.name" class="report-item">
                  <ProgressRing :value="dim.score" :size="96" :stroke="9" :label="dim.name" />
                  <p class="report-comment">{{ dim.comment }}</p>
                </div>
              </div>
              <el-button type="primary" size="large" class="report-next" @click="nextStage">
                {{ currentIndex >= interviewStages.length - 1 ? '查看总结' : '进入下一环节' }}
              </el-button>
            </div>

            <!-- 答题态 -->
            <div v-else class="answering" key="answering">
              <div class="record-toggles">
                <button
                  type="button"
                  class="record-btn"
                  :class="{ 'record-btn--on': recording === 'audio' }"
                  @click="toggleRecording('audio')"
                >
                  <el-icon><Microphone /></el-icon>
                  {{ recording === 'audio' ? '录音中' : '录音' }}
                  <span v-if="recording === 'audio'" class="rec-dot"></span>
                </button>
                <button
                  type="button"
                  class="record-btn"
                  :class="{ 'record-btn--on': recording === 'video' }"
                  @click="toggleRecording('video')"
                >
                  <el-icon><VideoCamera /></el-icon>
                  {{ recording === 'video' ? '录像中' : '录像' }}
                  <span v-if="recording === 'video'" class="rec-dot"></span>
                </button>
              </div>

              <el-input
                v-model="answer"
                type="textarea"
                :rows="9"
                resize="none"
                placeholder="在这里组织你的回答,或开启录音后口述…"
                class="answer-input"
              />

              <el-button
                type="primary"
                size="large"
                class="submit-btn"
                :loading="phase === 'evaluating'"
                :disabled="phase === 'answering' && answer.trim().length === 0 && recording === 'none'"
                @click="submitAnswer"
              >
                <el-icon v-if="phase === 'answering'" class="btn-icon"><Promotion /></el-icon>
                {{ phase === 'evaluating' ? '评估中' : '提交评估' }}
              </el-button>
            </div>
          </Transition>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.cabin {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1080px;
}

.timeline-card {
  padding: 22px 28px 18px;
}

.cabin-grid {
  display: grid;
  grid-template-columns: 0.9fr 1.1fr;
  gap: 20px;
  align-items: start;
}

.examiner-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 30px 28px;
  gap: 14px;
  text-align: center;
}

.examiner-status {
  font-size: 12px;
}

.question {
  margin: 6px 0 0;
  padding: 16px 18px;
  border-left: 3px solid var(--zm-accent);
  border-radius: 0 var(--zm-radius-sm) var(--zm-radius-sm) 0;
  background: var(--zm-accent-soft);
  font-size: 14px;
  line-height: 1.8;
  color: var(--zm-ink);
  text-align: left;
}

.hint {
  font-size: 12px;
  line-height: 1.7;
  color: var(--zm-ink-faint);
  text-align: left;
}

.answer-card {
  padding: 26px 28px;
  min-height: 380px;
}

.record-toggles {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.record-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 16px;
  border-radius: var(--zm-radius-pill);
  border: 1px solid var(--zm-border-strong);
  background: transparent;
  color: var(--zm-ink-soft);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--zm-dur-fast) var(--zm-ease);
}

.record-btn:hover {
  background: var(--zm-surface-strong);
  color: var(--zm-ink);
}

.record-btn--on {
  color: var(--zm-red);
  border-color: var(--zm-red);
  background: rgba(255, 59, 48, 0.08);
}

.rec-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--zm-red);
  animation: rec-blink 1s step-end infinite;
}

@keyframes rec-blink {
  50% {
    opacity: 0.2;
  }
}

.answer-input :deep(.el-textarea__inner) {
  background: var(--zm-surface);
  border-radius: var(--zm-radius-md);
  font-size: 14px;
  line-height: 1.8;
  padding: 14px 16px;
}

.submit-btn {
  margin-top: 16px;
  width: 100%;
}

.btn-icon {
  margin-right: 6px;
}

.report-eyebrow {
  font-size: 12px;
  margin-bottom: 18px;
}

.report-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px 16px;
}

.report-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  text-align: center;
}

.report-comment {
  font-size: 11px;
  line-height: 1.6;
  color: var(--zm-ink-faint);
}

.report-next {
  margin-top: 20px;
  width: 100%;
}

.summary {
  padding: 40px 44px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 18px;
}

.summary-eyebrow {
  font-size: 12px;
}

.summary h2 {
  font-size: 24px;
}

.summary-body {
  display: flex;
  align-items: center;
  gap: 36px;
}

.summary-text {
  font-size: 14px;
  line-height: 1.9;
  color: var(--zm-ink-soft);
  max-width: 520px;
}

@media (max-width: 860px) {
  .cabin-grid {
    grid-template-columns: 1fr;
  }
  .summary-body {
    flex-direction: column;
    align-items: flex-start;
  }
  .report-grid {
    grid-template-columns: 1fr;
  }
}
</style>
