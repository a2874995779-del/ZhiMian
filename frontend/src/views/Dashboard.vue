<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowRight, TrendCharts, Flag } from '@element-plus/icons-vue'
import ProgressRing from '../components/dashboard/ProgressRing.vue'
import TrendChart from '../components/dashboard/TrendChart.vue'
import { dashboardStats } from '../mock/dashboard'

const router = useRouter()
const stats = dashboardStats
const progressPercent = Math.round((stats.today.done / stats.today.goal) * 100)
</script>

<template>
  <div class="dashboard">
    <div class="bento">
      <!-- 模块 A:今日学习状态 -->
      <section class="cell cell--ring zm-glass zm-glass--hoverable">
        <p class="cell-eyebrow zm-prompt">&gt; today</p>
        <ProgressRing :value="progressPercent" :label="`${stats.today.done} / ${stats.today.goal} 题`" />
        <p class="cell-caption">今日刷题进度</p>
      </section>

      <!-- 模块 B:周度备战指数 -->
      <section class="cell cell--trend zm-glass zm-glass--hoverable">
        <div class="cell-head">
          <div>
            <p class="cell-eyebrow zm-prompt">&gt; weekly_index</p>
            <h3>面试通过概率趋势</h3>
          </div>
          <el-icon class="cell-head-icon"><TrendCharts /></el-icon>
        </div>
        <TrendChart :points="stats.trend" />
      </section>

      <!-- 连续学习天数 -->
      <section class="cell cell--stat cell--streak zm-glass zm-glass--hoverable">
        <p class="cell-eyebrow zm-prompt">&gt; streak</p>
        <p class="stat-number">{{ stats.streakDays }}<span class="stat-unit">天</span></p>
        <p class="cell-caption">连续学习</p>
      </section>

      <!-- 累计通关数 -->
      <section class="cell cell--stat cell--total zm-glass zm-glass--hoverable">
        <p class="cell-eyebrow zm-prompt">&gt; solved</p>
        <p class="stat-number">
          {{ stats.totalSolved }}
          <span class="stat-delta" :class="stats.weeklyDelta >= 0 ? 'stat-delta--up' : 'stat-delta--down'">
            {{ stats.weeklyDelta >= 0 ? '+' : '' }}{{ stats.weeklyDelta }}
          </span>
        </p>
        <p class="cell-caption">累计通关题目</p>
      </section>

      <!-- 模块 C:速配面试舱 -->
      <section class="cell cell--cta">
        <button class="quick-match" type="button" @click="router.push('/interview')">
          <span class="quick-match-icon"><el-icon :size="22"><Flag /></el-icon></span>
          <span class="quick-match-text">
            <span class="quick-match-title">速配面试舱</span>
            <span class="quick-match-sub zm-prompt">&gt; 立即进入随机模拟面试</span>
          </span>
          <el-icon class="quick-match-arrow"><ArrowRight /></el-icon>
        </button>
      </section>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  padding-top: 8px;
}

.bento {
  display: grid;
  grid-template-columns: 1.05fr 1.7fr 1fr;
  grid-template-rows: auto auto auto;
  gap: 18px;
  grid-template-areas:
    'ring trend streak'
    'ring trend total'
    'cta cta cta';
}

.cell {
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.cell--ring {
  grid-area: ring;
  align-items: center;
  justify-content: center;
  gap: 14px;
}

.cell--trend {
  grid-area: trend;
  gap: 10px;
}

.cell--streak {
  grid-area: streak;
}

.cell--total {
  grid-area: total;
}

.cell--stat {
  justify-content: center;
  gap: 6px;
}

.cell--cta {
  grid-area: cta;
}

.cell-eyebrow {
  font-size: 12px;
}

.cell-caption {
  font-size: 13px;
  color: var(--zm-ink-soft);
}

.cell-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.cell-head h3 {
  font-size: 17px;
  margin-top: 4px;
}

.cell-head-icon {
  color: var(--zm-accent);
  font-size: 20px;
}

.stat-number {
  font-family: var(--zm-font-display);
  font-size: 32px;
  font-weight: 800;
  letter-spacing: -0.02em;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.stat-unit {
  font-size: 15px;
  font-weight: 600;
  color: var(--zm-ink-faint);
}

.stat-delta {
  font-family: var(--zm-font-mono);
  font-size: 13px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--zm-radius-pill);
}

.stat-delta--up {
  color: var(--zm-green);
  background: rgba(52, 199, 89, 0.12);
}

.stat-delta--down {
  color: var(--zm-red);
  background: rgba(255, 59, 48, 0.12);
}

.quick-match {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 22px 26px;
  border: none;
  border-radius: var(--zm-radius-lg);
  cursor: pointer;
  background: linear-gradient(135deg, var(--zm-accent) 0%, #4c9fec 100%);
  color: white;
  box-shadow: var(--zm-shadow-glow-accent);
  transition: transform var(--zm-dur-base) var(--zm-ease), box-shadow var(--zm-dur-base) var(--zm-ease);
}

.quick-match:hover {
  transform: translateY(-2px) scale(1.005);
  box-shadow: 0 14px 40px rgba(0, 113, 227, 0.32);
}

.quick-match:active {
  transform: scale(0.98);
}

.quick-match-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.quick-match-text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  flex: 1;
}

.quick-match-title {
  font-family: var(--zm-font-display);
  font-weight: 700;
  font-size: 16px;
}

.quick-match-sub {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85) !important;
}

.quick-match-arrow {
  font-size: 18px;
}

@media (max-width: 960px) {
  .bento {
    grid-template-columns: 1fr;
    grid-template-areas:
      'ring'
      'trend'
      'streak'
      'total'
      'cta';
  }
}
</style>
