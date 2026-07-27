<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheck, Warning } from '@element-plus/icons-vue'
import type { InterviewReportVO } from '../../types/interview'

const props = defineProps<{ report: InterviewReportVO }>()

// 分档只影响配色和一句评语,不改分数本身
const grade = computed(() => {
  const s = props.report.score
  if (s >= 85) return { label: '优秀', tone: 'excellent' }
  if (s >= 70) return { label: '良好', tone: 'good' }
  if (s >= 60) return { label: '及格', tone: 'pass' }
  return { label: '待提升', tone: 'weak' }
})
</script>

<template>
  <div class="report">
    <div class="score-block" :class="`score-block--${grade.tone}`">
      <div class="score-num">
        {{ report.score }}<span class="score-unit">/100</span>
      </div>
      <div class="score-grade">{{ grade.label }}</div>
    </div>

    <p class="summary">{{ report.summary }}</p>

    <div class="sections">
      <section class="section">
        <h4 class="section-title section-title--good">
          <el-icon><CircleCheck /></el-icon> 亮点
        </h4>
        <ul>
          <li v-for="(item, i) in report.highlights" :key="`h-${i}`">{{ item }}</li>
          <li v-if="!report.highlights.length" class="empty">暂无</li>
        </ul>
      </section>

      <section class="section">
        <h4 class="section-title section-title--weak">
          <el-icon><Warning /></el-icon> 待改进
        </h4>
        <ul>
          <li v-for="(item, i) in report.weaknesses" :key="`w-${i}`">{{ item }}</li>
          <li v-if="!report.weaknesses.length" class="empty">暂无</li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped>
.report {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.score-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 20px;
  border-radius: var(--zm-radius-md);
  border: 1px solid var(--zm-border);
}

.score-block--excellent {
  background: rgba(52, 199, 89, 0.1);
  border-color: rgba(52, 199, 89, 0.35);
}
.score-block--good {
  background: var(--zm-accent-soft);
  border-color: var(--zm-accent);
}
.score-block--pass {
  background: rgba(255, 149, 0, 0.1);
  border-color: rgba(255, 149, 0, 0.35);
}
.score-block--weak {
  background: rgba(255, 59, 48, 0.1);
  border-color: rgba(255, 59, 48, 0.35);
}

.score-num {
  font-family: var(--zm-font-display);
  font-size: 44px;
  font-weight: 800;
  line-height: 1;
  color: var(--zm-ink);
}

.score-unit {
  font-size: 16px;
  font-weight: 600;
  color: var(--zm-ink-faint);
}

.score-grade {
  font-size: 13px;
  font-weight: 700;
  color: var(--zm-ink-soft);
}

.summary {
  font-size: 14px;
  line-height: 1.8;
  color: var(--zm-ink-soft);
  padding: 14px 16px;
  background: var(--zm-surface-strong);
  border-radius: var(--zm-radius-md);
  white-space: pre-wrap;
}

.sections {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 10px;
}

.section-title--good {
  color: #34c759;
}
.section-title--weak {
  color: #ff9500;
}

.section ul {
  display: flex;
  flex-direction: column;
  gap: 8px;
  list-style: none;
}

.section li {
  font-size: 13px;
  line-height: 1.6;
  color: var(--zm-ink-soft);
  padding-left: 14px;
  position: relative;
}

.section li::before {
  content: '·';
  position: absolute;
  left: 2px;
  color: var(--zm-ink-faint);
}

.section li.empty {
  color: var(--zm-ink-faint);
}

@media (max-width: 720px) {
  .sections {
    grid-template-columns: 1fr;
  }
}
</style>
