<script setup lang="ts">
import { computed, ref } from 'vue'
import type { TrendPoint } from '../../types/dashboard'

const props = defineProps<{
  points: TrendPoint[]
}>()

const width = 520
const height = 160
const padX = 16
const padY = 20

const maxVal = computed(() => Math.max(...props.points.map((p) => p.passProbability)))
const minVal = computed(() => Math.min(...props.points.map((p) => p.passProbability)))
const span = computed(() => Math.max(1, maxVal.value - minVal.value))

const coords = computed(() =>
  props.points.map((p, i) => {
    const x = padX + (i / (props.points.length - 1)) * (width - padX * 2)
    const normalized = (p.passProbability - minVal.value) / span.value
    const y = height - padY - normalized * (height - padY * 2)
    return { x, y, ...p }
  }),
)

// 用相邻点中点作为控制点,画出一条平滑但不失真的曲线
const linePath = computed(() => {
  const pts = coords.value
  if (pts.length === 0) return ''
  let d = `M ${pts[0].x} ${pts[0].y}`
  for (let i = 0; i < pts.length - 1; i++) {
    const cur = pts[i]
    const next = pts[i + 1]
    const midX = (cur.x + next.x) / 2
    d += ` Q ${midX} ${cur.y}, ${midX} ${(cur.y + next.y) / 2}`
    d += ` Q ${midX} ${next.y}, ${next.x} ${next.y}`
  }
  return d
})

const areaPath = computed(() => `${linePath.value} L ${coords.value.at(-1)?.x} ${height} L ${coords.value[0]?.x} ${height} Z`)

const hovered = ref<number | null>(null)
</script>

<template>
  <div class="trend-chart">
    <svg :viewBox="`0 0 ${width} ${height}`" class="chart-svg" preserveAspectRatio="none">
      <defs>
        <linearGradient id="zm-trend-area" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="var(--zm-accent)" stop-opacity="0.28" />
          <stop offset="100%" stop-color="var(--zm-accent)" stop-opacity="0" />
        </linearGradient>
        <filter id="zm-trend-glow" x="-40%" y="-40%" width="180%" height="180%">
          <feGaussianBlur stdDeviation="3.2" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      <path :d="areaPath" fill="url(#zm-trend-area)" />
      <path :d="linePath" fill="none" stroke="var(--zm-accent)" stroke-width="2.5" filter="url(#zm-trend-glow)" />

      <g v-for="(c, i) in coords" :key="c.label">
        <circle
          :cx="c.x"
          :cy="c.y"
          :r="hovered === i ? 5.5 : 3.5"
          fill="var(--zm-bg-elevated)"
          stroke="var(--zm-accent)"
          stroke-width="2"
          class="chart-dot"
          @mouseenter="hovered = i"
          @mouseleave="hovered = null"
        />
      </g>
    </svg>

    <div class="chart-labels">
      <span v-for="c in coords" :key="c.label" class="chart-label">{{ c.label }}</span>
    </div>

    <Transition name="zm-fade">
      <div v-if="hovered !== null" class="tooltip" :style="{ left: coords[hovered].x + 'px' }">
        <span class="zm-prompt">{{ coords[hovered].passProbability }}%</span>
        <span class="tooltip-sub">{{ coords[hovered].label }}通过概率</span>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.trend-chart {
  position: relative;
}

.chart-svg {
  width: 100%;
  height: 160px;
  display: block;
  overflow: visible;
}

.chart-dot {
  cursor: pointer;
  transition: r var(--zm-dur-fast) var(--zm-ease);
}

.chart-labels {
  display: flex;
  justify-content: space-between;
  padding: 6px 16px 0;
  font-size: 11px;
  color: var(--zm-ink-faint);
}

.tooltip {
  position: absolute;
  top: -6px;
  transform: translate(-50%, -100%);
  background: var(--zm-bg-elevated);
  border: 1px solid var(--zm-border-strong);
  border-radius: var(--zm-radius-sm);
  padding: 6px 10px;
  box-shadow: var(--zm-shadow-lg);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  pointer-events: none;
  white-space: nowrap;
}

.tooltip-sub {
  font-size: 10px;
  color: var(--zm-ink-faint);
}
</style>
